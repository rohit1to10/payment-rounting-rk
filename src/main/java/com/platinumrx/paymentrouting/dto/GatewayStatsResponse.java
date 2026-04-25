package com.platinumrx.paymentrouting.dto;

import com.platinumrx.paymentrouting.model.GatewayState;

import java.time.Instant;

public class GatewayStatsResponse {

    private String gatewayName;
    private GatewayState state;
    private double dynamicWeight;
    private long recentSuccessCount;
    private long recentFailureCount;
    private double recentSuccessRate;
    private long totalSuccess;
    private long totalFailure;
    private Instant cooldownUntil;

    private GatewayStatsResponse() {}

    public String getGatewayName() { return gatewayName; }
    public GatewayState getState() { return state; }
    public double getDynamicWeight() { return dynamicWeight; }
    public long getRecentSuccessCount() { return recentSuccessCount; }
    public long getRecentFailureCount() { return recentFailureCount; }
    public double getRecentSuccessRate() { return recentSuccessRate; }
    public long getTotalSuccess() { return totalSuccess; }
    public long getTotalFailure() { return totalFailure; }
    public Instant getCooldownUntil() { return cooldownUntil; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final GatewayStatsResponse r = new GatewayStatsResponse();

        public Builder gatewayName(String v) { r.gatewayName = v; return this; }
        public Builder state(GatewayState v) { r.state = v; return this; }
        public Builder dynamicWeight(double v) { r.dynamicWeight = v; return this; }
        public Builder recentSuccessCount(long v) { r.recentSuccessCount = v; return this; }
        public Builder recentFailureCount(long v) { r.recentFailureCount = v; return this; }
        public Builder recentSuccessRate(double v) { r.recentSuccessRate = v; return this; }
        public Builder totalSuccess(long v) { r.totalSuccess = v; return this; }
        public Builder totalFailure(long v) { r.totalFailure = v; return this; }
        public Builder cooldownUntil(Instant v) { r.cooldownUntil = v; return this; }
        public GatewayStatsResponse build() { return r; }
    }
}
