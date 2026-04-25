package com.platinumrx.paymentrouting.service;

import com.platinumrx.paymentrouting.config.GatewayConfig;
import com.platinumrx.paymentrouting.model.GatewayState;
import com.platinumrx.paymentrouting.model.GatewayStats;
import com.platinumrx.paymentrouting.store.GatewayStatsStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayHealthServiceTest {

    private GatewayHealthService healthService;
    private GatewayStats razorpay;

    @BeforeEach
    void setUp() {
        GatewayConfig config = new GatewayConfig();
        GatewayConfig.HealthConfig health = new GatewayConfig.HealthConfig();
        health.setWindowMinutes(15);
        health.setThreshold(0.90);
        health.setCooldownMinutes(30);
        health.setMinSamples(5);
        config.setHealth(health);

        GatewayConfig.GatewayProperties props = new GatewayConfig.GatewayProperties();
        props.setWeight(40);
        Map<String, GatewayConfig.GatewayProperties> gateways = new HashMap<>();
        gateways.put("razorpay", props);
        config.setGateways(gateways);

        GatewayStatsStore statsStore = new GatewayStatsStore(config);
        statsStore.init();

        razorpay = statsStore.get("razorpay");
        healthService = new GatewayHealthService(statsStore, config);
    }

    @Test
    void gatewayRemainsClosedWithHighSuccessRate() {
        // 9 successes + 1 failure = 90% success rate (exactly meets threshold)
        for (int i = 0; i < 9; i++) healthService.processCallback("razorpay", true);
        healthService.processCallback("razorpay", false);

        assertThat(razorpay.getState()).isEqualTo(GatewayState.CLOSED);
    }

    @Test
    void gatewayMarkedOpenWhenSuccessRateDropsBelowThreshold() {
        // 5 successes + 5 failures = 50% success rate (well below 90%)
        for (int i = 0; i < 5; i++) healthService.processCallback("razorpay", true);
        for (int i = 0; i < 5; i++) healthService.processCallback("razorpay", false);

        assertThat(razorpay.getState()).isEqualTo(GatewayState.OPEN);
        assertThat(razorpay.getCooldownUntil()).isNotNull();
    }

    @Test
    void halfOpenGatewayRecoversWhenSuccessRateRestores() {
        razorpay.setState(GatewayState.HALF_OPEN);

        // 9 successes + 1 failure = 90% — exactly meets threshold
        for (int i = 0; i < 9; i++) healthService.processCallback("razorpay", true);
        healthService.processCallback("razorpay", false);

        assertThat(razorpay.getState()).isEqualTo(GatewayState.CLOSED);
    }

    @Test
    void halfOpenGatewayRevertsToOpenWhenStillUnhealthy() {
        razorpay.setState(GatewayState.HALF_OPEN);

        // 2 successes + 5 failures = 28% — well below threshold
        for (int i = 0; i < 2; i++) healthService.processCallback("razorpay", true);
        for (int i = 0; i < 5; i++) healthService.processCallback("razorpay", false);

        assertThat(razorpay.getState()).isEqualTo(GatewayState.OPEN);
    }

    @Test
    void dynamicWeightIncreasesOnSuccess() {
        double initialWeight = razorpay.getDynamicWeight();
        healthService.processCallback("razorpay", true);
        assertThat(razorpay.getDynamicWeight()).isGreaterThan(initialWeight);
    }

    @Test
    void dynamicWeightDecreasesOnFailure() {
        double initialWeight = razorpay.getDynamicWeight();
        healthService.processCallback("razorpay", false);
        assertThat(razorpay.getDynamicWeight()).isLessThan(initialWeight);
    }
}
