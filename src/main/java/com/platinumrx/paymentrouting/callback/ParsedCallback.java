package com.platinumrx.paymentrouting.callback;

public class ParsedCallback {

    private String orderId;
    private String gateway;
    private boolean success;
    private String reason;

    private ParsedCallback() {}

    public String getOrderId() { return orderId; }
    public String getGateway() { return gateway; }
    public boolean isSuccess() { return success; }
    public String getReason() { return reason; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final ParsedCallback r = new ParsedCallback();

        public Builder orderId(String v) { r.orderId = v; return this; }
        public Builder gateway(String v) { r.gateway = v; return this; }
        public Builder success(boolean v) { r.success = v; return this; }
        public Builder reason(String v) { r.reason = v; return this; }
        public ParsedCallback build() { return r; }
    }
}
