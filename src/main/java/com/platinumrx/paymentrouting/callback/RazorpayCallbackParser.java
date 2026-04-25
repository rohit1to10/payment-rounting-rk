package com.platinumrx.paymentrouting.callback;

import com.platinumrx.paymentrouting.dto.CallbackRequest;
import org.springframework.stereotype.Component;

/**
 * Razorpay uses status values: "captured" (success), "failed", "refunded", etc.
 */
@Component
public class RazorpayCallbackParser implements CallbackParser {

    @Override
    public String supportedGateway() {
        return "razorpay";
    }

    @Override
    public ParsedCallback parse(CallbackRequest request) {
        boolean success = "captured".equalsIgnoreCase(request.getStatus())
            || "success".equalsIgnoreCase(request.getStatus());

        return ParsedCallback.builder()
            .orderId(request.getOrderId())
            .gateway(supportedGateway())
            .success(success)
            .reason(request.getReason())
            .build();
    }
}
