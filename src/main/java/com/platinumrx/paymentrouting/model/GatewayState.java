package com.platinumrx.paymentrouting.model;

public enum GatewayState {
    /** Normal operation — accepts full traffic. */
    CLOSED,
    /** Unhealthy — blocked for cooldown period. */
    OPEN,
    /** Recovery probe — accepts limited traffic to verify stability. */
    HALF_OPEN
}
