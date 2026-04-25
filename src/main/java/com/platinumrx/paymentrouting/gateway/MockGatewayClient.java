package com.platinumrx.paymentrouting.gateway;

import com.platinumrx.paymentrouting.model.Transaction;

public interface MockGatewayClient {
    String getGatewayName();
    GatewayResponse initiatePayment(Transaction transaction);
}
