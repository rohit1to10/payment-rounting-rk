package com.platinumrx.paymentrouting.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

public class PaymentInstrument {

    private String type;

    @JsonProperty("card_number")
    private String cardNumber;

    private String expiry;

    private Map<String, Object> extra = new HashMap<>();

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getCardNumber() { return cardNumber; }
    public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }
    public String getExpiry() { return expiry; }
    public void setExpiry(String expiry) { this.expiry = expiry; }
    public Map<String, Object> getExtra() { return extra; }

    @JsonAnySetter
    public void setExtra(String key, Object value) { extra.put(key, value); }
}
