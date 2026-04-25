package com.platinumrx.paymentrouting.dto;

import java.util.List;
import java.util.Map;

public class RoutingMetadata {

    private double score;
    private String reason;
    private List<String> availableGateways;
    private List<String> fallbackCandidates;
    private Map<String, Double> gatewayScores;

    private RoutingMetadata() {}

    public double getScore() { return score; }
    public String getReason() { return reason; }
    public List<String> getAvailableGateways() { return availableGateways; }
    public List<String> getFallbackCandidates() { return fallbackCandidates; }
    public Map<String, Double> getGatewayScores() { return gatewayScores; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final RoutingMetadata m = new RoutingMetadata();

        public Builder score(double v) { m.score = v; return this; }
        public Builder reason(String v) { m.reason = v; return this; }
        public Builder availableGateways(List<String> v) { m.availableGateways = v; return this; }
        public Builder fallbackCandidates(List<String> v) { m.fallbackCandidates = v; return this; }
        public Builder gatewayScores(Map<String, Double> v) { m.gatewayScores = v; return this; }
        public RoutingMetadata build() { return m; }
    }
}
