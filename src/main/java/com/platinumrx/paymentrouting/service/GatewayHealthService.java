package com.platinumrx.paymentrouting.service;

import com.platinumrx.paymentrouting.config.GatewayConfig;
import com.platinumrx.paymentrouting.model.GatewayState;
import com.platinumrx.paymentrouting.model.GatewayStats;
import com.platinumrx.paymentrouting.store.GatewayStatsStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class GatewayHealthService {

    private static final Logger log = LoggerFactory.getLogger(GatewayHealthService.class);

    private final GatewayStatsStore statsStore;
    private final GatewayConfig config;

    public GatewayHealthService(GatewayStatsStore statsStore, GatewayConfig config) {
        this.statsStore = statsStore;
        this.config = config;
    }

    public synchronized void processCallback(String gateway, boolean success) {
        if (success) {
            statsStore.recordSuccess(gateway);
        } else {
            statsStore.recordFailure(gateway);
        }
        adaptWeight(gateway, success);
        evaluateHealth(gateway);
    }

    private void evaluateHealth(String gateway) {
        GatewayStats stats = statsStore.get(gateway);
        GatewayConfig.HealthConfig health = config.getHealth();

        purgeOldWindowEvents(stats);

        long successCount = stats.getSuccessWindow().size();
        long failureCount = stats.getFailureWindow().size();
        long total = successCount + failureCount;

        if (total < health.getMinSamples()) {
            log.debug("[HEALTH] {} — insufficient samples ({}/{}), skipping evaluation",
                gateway, total, health.getMinSamples());
            return;
        }

        double successRate = (double) successCount / total;
        GatewayState current = stats.getState();

        if (current == GatewayState.CLOSED && successRate < health.getThreshold()) {
            log.warn("[HEALTH] {} success_rate={}% below threshold={}% — marking OPEN (cooldown {} min)",
                gateway,
                String.format("%.1f", successRate * 100),
                String.format("%.0f", health.getThreshold() * 100),
                health.getCooldownMinutes());
            stats.setState(GatewayState.OPEN);
            stats.setCooldownUntil(Instant.now().plus(health.getCooldownMinutes(), ChronoUnit.MINUTES));

        } else if (current == GatewayState.HALF_OPEN) {
            if (successRate >= health.getThreshold()) {
                log.info("[HEALTH] {} recovered success_rate={}% — marking CLOSED",
                    gateway, String.format("%.1f", successRate * 100));
                stats.setState(GatewayState.CLOSED);
                stats.setCooldownUntil(null);
            } else {
                log.warn("[HEALTH] {} still unhealthy in HALF_OPEN success_rate={}% — reverting to OPEN",
                    gateway, String.format("%.1f", successRate * 100));
                stats.setState(GatewayState.OPEN);
                stats.setCooldownUntil(Instant.now().plus(health.getCooldownMinutes(), ChronoUnit.MINUTES));
            }
        }
    }

    /** Adaptive weight: reward success, penalise failure — bounded to 50%–150% of base weight. */
    private void adaptWeight(String gateway, boolean success) {
        GatewayStats stats = statsStore.get(gateway);
        GatewayConfig.GatewayProperties props = config.getGateways().get(gateway);
        double baseWeight = props != null ? props.getWeight() : 33.0;

        double current = stats.getDynamicWeight();
        double adjusted = success
            ? Math.min(current + 2.0, baseWeight * 1.5)
            : Math.max(current - 3.0, baseWeight * 0.5);

        stats.setDynamicWeight(adjusted);
        log.debug("[WEIGHT] {} weight {} → {}", gateway,
            String.format("%.1f", current), String.format("%.1f", adjusted));
    }

    private void purgeOldWindowEvents(GatewayStats stats) {
        Instant cutoff = Instant.now().minus(config.getHealth().getWindowMinutes(), ChronoUnit.MINUTES);
        stats.getSuccessWindow().removeIf(t -> t.isBefore(cutoff));
        stats.getFailureWindow().removeIf(t -> t.isBefore(cutoff));
    }
}
