package com.platinumrx.paymentrouting.controller;

import com.platinumrx.paymentrouting.config.GatewayConfig;
import com.platinumrx.paymentrouting.dto.GatewayStatsResponse;
import com.platinumrx.paymentrouting.model.GatewayState;
import com.platinumrx.paymentrouting.model.GatewayStats;
import com.platinumrx.paymentrouting.routing.RoutingEngine;
import com.platinumrx.paymentrouting.service.GatewayHealthService;
import com.platinumrx.paymentrouting.store.GatewayStatsStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/gateways")
public class GatewayController {

    private final GatewayStatsStore statsStore;
    private final GatewayHealthService healthService;
    private final RoutingEngine routingEngine;
    private final GatewayConfig config;

    public GatewayController(
        GatewayStatsStore statsStore,
        GatewayHealthService healthService,
        RoutingEngine routingEngine,
        GatewayConfig config
    ) {
        this.statsStore = statsStore;
        this.healthService = healthService;
        this.routingEngine = routingEngine;
        this.config = config;
    }

    @GetMapping("/stats")
    public ResponseEntity<List<GatewayStatsResponse>> getStats() {
        int windowMinutes = config.getHealth().getWindowMinutes();
        List<GatewayStatsResponse> response = statsStore.getAll().stream()
            .map(g -> toResponse(g, windowMinutes))
            .toList();
        return ResponseEntity.ok(response);
    }

    /** Force a gateway into OPEN state for demo/testing. */
    @PostMapping("/{gateway}/force-unhealthy")
    public ResponseEntity<Map<String, String>> forceUnhealthy(@PathVariable String gateway) {
        GatewayStats stats = statsStore.get(gateway);
        stats.setState(GatewayState.OPEN);
        stats.setCooldownUntil(Instant.now().plus(config.getHealth().getCooldownMinutes(), ChronoUnit.MINUTES));
        return ResponseEntity.ok(Map.of("gateway", gateway, "state", "OPEN"));
    }

    /** Reset a gateway back to CLOSED for demo/testing. */
    @PostMapping("/{gateway}/reset")
    public ResponseEntity<Map<String, String>> reset(@PathVariable String gateway) {
        GatewayStats stats = statsStore.get(gateway);
        stats.setState(GatewayState.CLOSED);
        stats.setCooldownUntil(null);
        GatewayConfig.GatewayProperties props = config.getGateways().get(gateway);
        if (props != null) {
            stats.setDynamicWeight(props.getWeight());
        }
        return ResponseEntity.ok(Map.of("gateway", gateway, "state", "CLOSED"));
    }

    /** Inject N failures for a gateway — useful for triggering health degradation in demos. */
    @PostMapping("/{gateway}/inject-failures")
    public ResponseEntity<Map<String, String>> injectFailures(
        @PathVariable String gateway,
        @RequestParam(defaultValue = "10") int count
    ) {
        for (int i = 0; i < count; i++) {
            healthService.processCallback(gateway, false);
        }
        return ResponseEntity.ok(Map.of("gateway", gateway, "injected_failures", String.valueOf(count)));
    }

    private GatewayStatsResponse toResponse(GatewayStats g, int windowMinutes) {
        double[] rates = routingEngine.calculateWindowRates(g, windowMinutes);
        return GatewayStatsResponse.builder()
            .gatewayName(g.getGatewayName())
            .state(g.getState())
            .dynamicWeight(g.getDynamicWeight())
            .recentSuccessCount(g.getSuccessWindow().size())
            .recentFailureCount(g.getFailureWindow().size())
            .recentSuccessRate(rates[0])
            .totalSuccess(g.getTotalSuccess())
            .totalFailure(g.getTotalFailure())
            .cooldownUntil(g.getCooldownUntil())
            .build();
    }
}
