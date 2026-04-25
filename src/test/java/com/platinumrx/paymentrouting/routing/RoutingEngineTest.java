package com.platinumrx.paymentrouting.routing;

import com.platinumrx.paymentrouting.config.GatewayConfig;
import com.platinumrx.paymentrouting.model.GatewayState;
import com.platinumrx.paymentrouting.store.GatewayStatsStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingEngineTest {

    private GatewayStatsStore statsStore;
    private RoutingEngine routingEngine;

    @BeforeEach
    void setUp() {
        GatewayConfig config = buildTestConfig(100); // 100% half-open traffic to force inclusion
        statsStore = new GatewayStatsStore(config);
        statsStore.init();
        routingEngine = new RoutingEngine(statsStore, config);
    }

    @Test
    void selectsGatewayWithHighestSuccessRate() {
        // Give razorpay 10 successes and payu 5 successes + 5 failures
        for (int i = 0; i < 10; i++) statsStore.recordSuccess("razorpay");
        for (int i = 0; i < 5; i++) statsStore.recordSuccess("payu");
        for (int i = 0; i < 5; i++) statsStore.recordFailure("payu");

        RoutingResult result = routingEngine.selectGateway(Set.of());

        assertThat(result.getSelectedGateway()).isEqualTo("razorpay");
        assertThat(result.getScore()).isGreaterThan(0);
        assertThat(result.isFallbackUsed()).isFalse();
    }

    @Test
    void excludesOpenGatewayFromNormalRouting() {
        statsStore.get("razorpay").setState(GatewayState.OPEN);
        statsStore.get("razorpay").setCooldownUntil(Instant.now().plusSeconds(1800));

        RoutingResult result = routingEngine.selectGateway(Set.of());

        assertThat(result.getSelectedGateway()).isNotEqualTo("razorpay");
        assertThat(result.getAvailableGateways()).doesNotContain("razorpay");
    }

    @Test
    void fallsBackToLeastBadGatewayWhenAllUnhealthy() {
        statsStore.getAll().forEach(g -> {
            g.setState(GatewayState.OPEN);
            g.setCooldownUntil(Instant.now().plusSeconds(1800));
        });

        RoutingResult result = routingEngine.selectGateway(Set.of());

        assertThat(result.isFallbackUsed()).isTrue();
        // razorpay has weight 40 — highest — so it wins the fallback selection
        assertThat(result.getSelectedGateway()).isEqualTo("razorpay");
    }

    @Test
    void penalisesPreviouslyUsedGateway() {
        // razorpay weight=40, payu weight=38 (close). Both CLOSED, no window data.
        // With retry penalty applied to razorpay, payu should win.
        GatewayConfig config = buildTestConfig(100);
        config.getGateways().get("razorpay").setWeight(40);
        config.getGateways().get("payu").setWeight(38);
        GatewayStatsStore store = new GatewayStatsStore(config);
        store.init();
        RoutingEngine engine = new RoutingEngine(store, config);

        RoutingResult result = engine.selectGateway(Set.of("razorpay"));

        assertThat(result.getSelectedGateway()).isEqualTo("payu");
    }

    @Test
    void halfOpenGatewayIncludedForRecoveryProbe() {
        // Mark all CLOSED gateways as OPEN so only HALF_OPEN cashfree is available
        statsStore.get("razorpay").setState(GatewayState.OPEN);
        statsStore.get("payu").setState(GatewayState.OPEN);
        statsStore.get("cashfree").setState(GatewayState.HALF_OPEN);
        // halfOpenTrafficPercent = 100 → always included

        RoutingResult result = routingEngine.selectGateway(Set.of());

        // cashfree must be selected (only non-OPEN candidate)
        assertThat(result.getSelectedGateway()).isEqualTo("cashfree");
    }

    private GatewayConfig buildTestConfig(int halfOpenPercent) {
        GatewayConfig config = new GatewayConfig();
        GatewayConfig.HealthConfig health = new GatewayConfig.HealthConfig();
        health.setWindowMinutes(15);
        health.setHalfOpenTrafficPercent(halfOpenPercent);
        config.setHealth(health);

        Map<String, GatewayConfig.GatewayProperties> gateways = new java.util.HashMap<>();
        GatewayConfig.GatewayProperties rp = new GatewayConfig.GatewayProperties();
        rp.setWeight(40);
        gateways.put("razorpay", rp);

        GatewayConfig.GatewayProperties pu = new GatewayConfig.GatewayProperties();
        pu.setWeight(35);
        gateways.put("payu", pu);

        GatewayConfig.GatewayProperties cf = new GatewayConfig.GatewayProperties();
        cf.setWeight(25);
        gateways.put("cashfree", cf);

        config.setGateways(gateways);
        return config;
    }
}
