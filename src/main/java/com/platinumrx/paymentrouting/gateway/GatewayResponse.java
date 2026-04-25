package com.platinumrx.paymentrouting.gateway;

public class GatewayResponse {

    private boolean accepted;
    private String gatewayTransactionId;
    private String message;
    private long simulatedLatencyMs;

    private GatewayResponse() {}

    public boolean isAccepted() { return accepted; }
    public String getGatewayTransactionId() { return gatewayTransactionId; }
    public String getMessage() { return message; }
    public long getSimulatedLatencyMs() { return simulatedLatencyMs; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final GatewayResponse r = new GatewayResponse();

        public Builder accepted(boolean v) { r.accepted = v; return this; }
        public Builder gatewayTransactionId(String v) { r.gatewayTransactionId = v; return this; }
        public Builder message(String v) { r.message = v; return this; }
        public Builder simulatedLatencyMs(long v) { r.simulatedLatencyMs = v; return this; }
        public GatewayResponse build() { return r; }
    }
}
