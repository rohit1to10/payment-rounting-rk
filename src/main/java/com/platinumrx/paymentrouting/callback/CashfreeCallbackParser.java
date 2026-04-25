package com.platinumrx.paymentrouting.callback;

import com.platinumrx.paymentrouting.dto.CallbackRequest;
import org.springframework.stereotype.Component;

/**
 * Cashfree uses uppercase status values: "SUCCESS", "FAILED", "USER_DROPPED", "PENDING".
 */
@Component
public class CashfreeCallbackParser implements CallbackParser {

    @Override
    public String supportedGateway() {
        return "cashfree";
    }

    @Override
    public ParsedCallback parse(CallbackRequest request) {
        boolean success = "SUCCESS".equalsIgnoreCase(request.getStatus());

        return ParsedCallback.builder()
            .orderId(request.getOrderId())
            .gateway(supportedGateway())
            .success(success)
            .reason(request.getReason())
            .build();
    }
}
