package com.platinumrx.paymentrouting.callback;

import com.platinumrx.paymentrouting.dto.CallbackRequest;

public interface CallbackParser {
    String supportedGateway();
    ParsedCallback parse(CallbackRequest request);
}
