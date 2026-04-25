package com.platinumrx.paymentrouting.model;

import java.time.Instant;

public class Transaction {

    private String transactionId;
    private String orderId;
    private String gateway;
    private TransactionStatus status;
    private int attemptNumber;
    private Instant timestamp;
    private String reason;
    private double amount;
    private PaymentInstrument paymentInstrument;
    private double routingScore;
    private String routingReason;

    private Transaction() {}

    public String getTransactionId() { return transactionId; }
    public String getOrderId() { return orderId; }
    public String getGateway() { return gateway; }
    public TransactionStatus getStatus() { return status; }
    public void setStatus(TransactionStatus status) { this.status = status; }
    public int getAttemptNumber() { return attemptNumber; }
    public Instant getTimestamp() { return timestamp; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public double getAmount() { return amount; }
    public PaymentInstrument getPaymentInstrument() { return paymentInstrument; }
    public double getRoutingScore() { return routingScore; }
    public String getRoutingReason() { return routingReason; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final Transaction t = new Transaction();

        public Builder transactionId(String v) { t.transactionId = v; return this; }
        public Builder orderId(String v) { t.orderId = v; return this; }
        public Builder gateway(String v) { t.gateway = v; return this; }
        public Builder status(TransactionStatus v) { t.status = v; return this; }
        public Builder attemptNumber(int v) { t.attemptNumber = v; return this; }
        public Builder timestamp(Instant v) { t.timestamp = v; return this; }
        public Builder reason(String v) { t.reason = v; return this; }
        public Builder amount(double v) { t.amount = v; return this; }
        public Builder paymentInstrument(PaymentInstrument v) { t.paymentInstrument = v; return this; }
        public Builder routingScore(double v) { t.routingScore = v; return this; }
        public Builder routingReason(String v) { t.routingReason = v; return this; }
        public Transaction build() { return t; }
    }
}
