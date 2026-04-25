package com.platinumrx.paymentrouting.dto;

import java.time.Instant;

public class InitiateResponse {

    private String transactionId;
    private String orderId;
    private String gateway;
    private String status;
    private int attemptNumber;
    private Instant timestamp;
    private RoutingMetadata routingMetadata;

    private InitiateResponse() {}

    public String getTransactionId() { return transactionId; }
    public String getOrderId() { return orderId; }
    public String getGateway() { return gateway; }
    public String getStatus() { return status; }
    public int getAttemptNumber() { return attemptNumber; }
    public Instant getTimestamp() { return timestamp; }
    public RoutingMetadata getRoutingMetadata() { return routingMetadata; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final InitiateResponse r = new InitiateResponse();

        public Builder transactionId(String v) { r.transactionId = v; return this; }
        public Builder orderId(String v) { r.orderId = v; return this; }
        public Builder gateway(String v) { r.gateway = v; return this; }
        public Builder status(String v) { r.status = v; return this; }
        public Builder attemptNumber(int v) { r.attemptNumber = v; return this; }
        public Builder timestamp(Instant v) { r.timestamp = v; return this; }
        public Builder routingMetadata(RoutingMetadata v) { r.routingMetadata = v; return this; }
        public InitiateResponse build() { return r; }
    }
}
