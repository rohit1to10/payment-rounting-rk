package com.platinumrx.paymentrouting.routing;

import com.platinumrx.paymentrouting.config.GatewayConfig;
import com.platinumrx.paymentrouting.model.GatewayState;
import com.platinumrx.paymentrouting.model.GatewayStats;
import com.platinumrx.paymentrouting.store.GatewayStatsStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core routing brain.
 *
 * Scoring formula:
 *   score = (normalizedWeight * 0.4) + (successRate * 0.4) - (failureRate * 0.2)
 *
 * CLOSED gateways are fully eligible. HALF_OPEN gateways are included with
 * halfOpenTrafficPercent probability for recovery probing. OPEN gateways are
 * excluded unless all are unhealthy (least-bad fallback).
 */
@Component
public class RoutingEngine {

    private static final Logger log = LoggerFactory.getLogger(RoutingEngine.class);

    private static final double W_WEIGHT  = 0.4;
    private static final double W_SUCCESS = 0.4;
    private static final double W_FAILURE = 0.2;
    private static final double RETRY_PENALTY = 0.15;

    private final GatewayStatsStore statsStore;
    private final GatewayConfig config;
    private final Random random = new Random();

    public RoutingEngine(GatewayStatsStore statsStore, GatewayConfig config) {
        this.statsStore = statsStore;
        this.config = config;
    }

    public RoutingResult selectGateway(Set<String> previousGateways) {
        Collection<GatewayStats> allStats = statsStore.getAll();
        int windowMinutes = config.getHealth().getWindowMinutes();

        List<GatewayStats> closed = allStats.stream()
            .filter(g -> g.getState() == GatewayState.CLOSED)
            .collect(Collectors.toList());

        List<GatewayStats> halfOpen = allStats.stream()
            .filter(g -> g.getState() == GatewayState.HALF_OPEN)
            .collect(Collectors.toList());

        List<GatewayStats> candidates = new ArrayList<>(closed);

        // Include HALF_OPEN gateways probabilistically for recovery probing
        for (GatewayStats g : halfOpen) {
            if (random.nextInt(100) < config.getHealth().getHalfOpenTrafficPercent()) {
                candidates.add(g);
            }
        }

        boolean fallbackUsed = false;

        if (candidates.isEmpty()) {
            // All gateways unhealthy — pick least-bad by dynamic weight
            List<GatewayStats> openGateways = allStats.stream()
                .filter(g -> g.getState() == GatewayState.OPEN)
                .sorted(Comparator.comparingDouble(GatewayStats::getDynamicWeight).reversed())
                .collect(Collectors.toList());

            if (openGateways.isEmpty()) {
                throw new IllegalStateException("No gateways configured");
            }
            candidates.addAll(openGateways);
            fallbackUsed = true;
            log.warn("[ROUTING] All gateways unhealthy — using least-bad fallback");
        }

        List<String> availableNames = candidates.stream()
            .map(GatewayStats::getGatewayName)
            .toList();

        // Score each candidate
        double totalWeight = candidates.stream()
            .mapToDouble(GatewayStats::getDynamicWeight)
            .sum();
        if (totalWeight == 0) totalWeight = 1.0;

        Map<String, Double> scores = new LinkedHashMap<>();
        for (GatewayStats g : candidates) {
            double normalizedWeight = g.getDynamicWeight() / totalWeight;
            double[] rates = calculateWindowRates(g, windowMinutes);
            double successRate = rates[0];
            double failureRate = rates[1];

            double score = (normalizedWeight * W_WEIGHT)
                + (successRate * W_SUCCESS)
                - (failureRate * W_FAILURE);

            // Penalise gateways already tried for this order (retry diversification)
            if (previousGateways.contains(g.getGatewayName())) {
                score -= RETRY_PENALTY;
            }
            scores.put(g.getGatewayName(), Math.max(score, 0.0));
        }

        String selected = scores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElseThrow();

        double selectedScore = scores.get(selected);

        List<String> fallbackCandidates = scores.entrySet().stream()
            .filter(e -> !e.getKey().equals(selected))
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .map(Map.Entry::getKey)
            .toList();

        GatewayStats selectedStats = statsStore.get(selected);
        double[] selectedRates = calculateWindowRates(selectedStats, windowMinutes);

        String reason = fallbackUsed
            ? "Fallback: all gateways unhealthy; selected least-bad option"
            : String.format("Highest composite score (weight+success-failure); success_rate=%.1f%%",
                selectedRates[0] * 100);

        log.info("""
            [ROUTING DECISION]
              Available gateways : {}
              Scores             : {}
              Selected           : {} (score={})
              Reason             : {}""",
            availableNames,
            scores.entrySet().stream()
                .map(e -> e.getKey() + "=" + String.format("%.3f", e.getValue()))
                .collect(Collectors.joining(", ")),
            selected, String.format("%.3f", selectedScore), reason);

        return RoutingResult.builder()
            .selectedGateway(selected)
            .score(selectedScore)
            .reason(reason)
            .availableGateways(availableNames)
            .fallbackCandidates(fallbackCandidates)
            .allScores(scores)
            .fallbackUsed(fallbackUsed)
            .build();
    }

    /** Returns [successRate, failureRate] for the sliding window. Defaults to [1.0, 0.0] if no data. */
    public double[] calculateWindowRates(GatewayStats stats, int windowMinutes) {
        Instant cutoff = Instant.now().minus(windowMinutes, ChronoUnit.MINUTES);
        long successCount = stats.getSuccessWindow().stream().filter(t -> !t.isBefore(cutoff)).count();
        long failureCount = stats.getFailureWindow().stream().filter(t -> !t.isBefore(cutoff)).count();
        long total = successCount + failureCount;
        if (total == 0) return new double[]{1.0, 0.0};
        double successRate = (double) successCount / total;
        return new double[]{successRate, 1.0 - successRate};
    }
}
