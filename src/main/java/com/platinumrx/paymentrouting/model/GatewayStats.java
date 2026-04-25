package com.platinumrx.paymentrouting.model;

import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe in-memory state for a payment gateway.
 */
public class GatewayStats {

    private final String gatewayName;
    private volatile double dynamicWeight;
    private volatile GatewayState state;
    private volatile Instant cooldownUntil;

    /** Timestamps of successful transactions in the sliding window. */
    private final ConcurrentLinkedDeque<Instant> successWindow = new ConcurrentLinkedDeque<>();
    /** Timestamps of failed transactions in the sliding window. */
    private final ConcurrentLinkedDeque<Instant> failureWindow = new ConcurrentLinkedDeque<>();

    private final AtomicLong totalSuccess = new AtomicLong();
    private final AtomicLong totalFailure = new AtomicLong();

    public GatewayStats(String gatewayName, double initialWeight) {
        this.gatewayName = gatewayName;
        this.dynamicWeight = initialWeight;
        this.state = GatewayState.CLOSED;
    }

    public String getGatewayName() { return gatewayName; }

    public double getDynamicWeight() { return dynamicWeight; }
    public void setDynamicWeight(double dynamicWeight) { this.dynamicWeight = dynamicWeight; }

    public GatewayState getState() { return state; }
    public void setState(GatewayState state) { this.state = state; }

    public Instant getCooldownUntil() { return cooldownUntil; }
    public void setCooldownUntil(Instant cooldownUntil) { this.cooldownUntil = cooldownUntil; }

    public ConcurrentLinkedDeque<Instant> getSuccessWindow() { return successWindow; }
    public ConcurrentLinkedDeque<Instant> getFailureWindow() { return failureWindow; }

    public long getTotalSuccess() { return totalSuccess.get(); }
    public long getTotalFailure() { return totalFailure.get(); }

    public void incrementSuccess() { totalSuccess.incrementAndGet(); }
    public void incrementFailure() { totalFailure.incrementAndGet(); }
}
