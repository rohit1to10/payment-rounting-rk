package com.platinumrx.paymentrouting.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.util.HashMap;
import java.util.Map;

public class CallbackRequest {

    @NotBlank(message = "order_id is required")
    @JsonProperty("order_id")
    private String orderId;

    @NotBlank(message = "status is required")
    private String status;

    @NotBlank(message = "gateway is required")
    private String gateway;

    private String reason;

    private Map<String, Object> extra = new HashMap<>();

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getGateway() { return gateway; }
    public void setGateway(String gateway) { this.gateway = gateway; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Map<String, Object> getExtra() { return extra; }

    @JsonAnySetter
    public void setExtra(String key, Object value) { extra.put(key, value); }
}
