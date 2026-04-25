package com.platinumrx.paymentrouting.callback;

import com.platinumrx.paymentrouting.dto.CallbackRequest;
import org.springframework.stereotype.Component;

/**
 * PayU uses status values: "success", "failure", "userCancelled", "pending".
 */
@Component
public class PayUCallbackParser implements CallbackParser {

    @Override
    public String supportedGateway() {
        return "payu";
    }

    @Override
    public ParsedCallback parse(CallbackRequest request) {
        boolean success = "success".equalsIgnoreCase(request.getStatus());

        return ParsedCallback.builder()
            .orderId(request.getOrderId())
            .gateway(supportedGateway())
            .success(success)
            .reason(request.getReason())
            .build();
    }
}
