package com.platinumrx.paymentrouting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "gateway")
public class GatewayConfig {

    private HealthConfig health = new HealthConfig();
    private Map<String, GatewayProperties> gateways = new HashMap<>();

    public HealthConfig getHealth() { return health; }
    public void setHealth(HealthConfig health) { this.health = health; }
    public Map<String, GatewayProperties> getGateways() { return gateways; }
    public void setGateways(Map<String, GatewayProperties> gateways) { this.gateways = gateways; }

    public static class HealthConfig {
        private int windowMinutes = 15;
        private double threshold = 0.90;
        private int cooldownMinutes = 30;
        private int minSamples = 5;
        private int halfOpenTrafficPercent = 5;

        public int getWindowMinutes() { return windowMinutes; }
        public void setWindowMinutes(int v) { this.windowMinutes = v; }
        public double getThreshold() { return threshold; }
        public void setThreshold(double v) { this.threshold = v; }
        public int getCooldownMinutes() { return cooldownMinutes; }
        public void setCooldownMinutes(int v) { this.cooldownMinutes = v; }
        public int getMinSamples() { return minSamples; }
        public void setMinSamples(int v) { this.minSamples = v; }
        public int getHalfOpenTrafficPercent() { return halfOpenTrafficPercent; }
        public void setHalfOpenTrafficPercent(int v) { this.halfOpenTrafficPercent = v; }
    }

    public static class GatewayProperties {
        private int weight = 33;
        private int failureRate = 0;
        private int latencyMs = 100;

        public int getWeight() { return weight; }
        public void setWeight(int v) { this.weight = v; }
        public int getFailureRate() { return failureRate; }
        public void setFailureRate(int v) { this.failureRate = v; }
        public int getLatencyMs() { return latencyMs; }
        public void setLatencyMs(int v) { this.latencyMs = v; }
    }
}
