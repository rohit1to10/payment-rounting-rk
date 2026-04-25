package com.platinumrx.paymentrouting.routing;

import java.util.List;
import java.util.Map;

public class RoutingResult {

    private String selectedGateway;
    private double score;
    private String reason;
    private List<String> availableGateways;
    private List<String> fallbackCandidates;
    private Map<String, Double> allScores;
    private boolean fallbackUsed;

    private RoutingResult() {}

    public String getSelectedGateway() { return selectedGateway; }
    public double getScore() { return score; }
    public String getReason() { return reason; }
    public List<String> getAvailableGateways() { return availableGateways; }
    public List<String> getFallbackCandidates() { return fallbackCandidates; }
    public Map<String, Double> getAllScores() { return allScores; }
    public boolean isFallbackUsed() { return fallbackUsed; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final RoutingResult r = new RoutingResult();

        public Builder selectedGateway(String v) { r.selectedGateway = v; return this; }
        public Builder score(double v) { r.score = v; return this; }
        public Builder reason(String v) { r.reason = v; return this; }
        public Builder availableGateways(List<String> v) { r.availableGateways = v; return this; }
        public Builder fallbackCandidates(List<String> v) { r.fallbackCandidates = v; return this; }
        public Builder allScores(Map<String, Double> v) { r.allScores = v; return this; }
        public Builder fallbackUsed(boolean v) { r.fallbackUsed = v; return this; }
        public RoutingResult build() { return r; }
    }
}
