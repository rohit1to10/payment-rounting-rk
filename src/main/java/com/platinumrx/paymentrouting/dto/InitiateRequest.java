package com.platinumrx.paymentrouting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.platinumrx.paymentrouting.model.PaymentInstrument;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class InitiateRequest {

    @NotBlank(message = "order_id is required")
    @JsonProperty("order_id")
    private String orderId;

    @NotNull(message = "amount is required")
    @Positive(message = "amount must be positive")
    private Double amount;

    @NotNull(message = "payment_instrument is required")
    @Valid
    @JsonProperty("payment_instrument")
    private PaymentInstrument paymentInstrument;

    @JsonProperty("idempotency_key")
    private String idempotencyKey;

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public PaymentInstrument getPaymentInstrument() { return paymentInstrument; }
    public void setPaymentInstrument(PaymentInstrument paymentInstrument) { this.paymentInstrument = paymentInstrument; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
}
