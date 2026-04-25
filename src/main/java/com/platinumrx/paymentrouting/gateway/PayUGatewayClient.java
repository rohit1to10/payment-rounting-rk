package com.platinumrx.paymentrouting.gateway;

import com.platinumrx.paymentrouting.config.GatewayConfig;
import com.platinumrx.paymentrouting.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.UUID;

@Component
public class PayUGatewayClient implements MockGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(PayUGatewayClient.class);
    private static final String NAME = "payu";

    private final GatewayConfig config;
    private final Random random = new Random();

    public PayUGatewayClient(GatewayConfig config) {
        this.config = config;
    }

    @Override
    public String getGatewayName() { return NAME; }

    @Override
    public GatewayResponse initiatePayment(Transaction transaction) {
        GatewayConfig.GatewayProperties props = config.getGateways().get(NAME);
        int failureRate = props != null ? props.getFailureRate() : 0;
        long latencyMs  = props != null ? props.getLatencyMs()  : 150;

        boolean accepted = random.nextInt(100) >= failureRate;
        log.debug("[PAYU] txId={} orderId={} accepted={} latency={}ms",
            transaction.getTransactionId(), transaction.getOrderId(), accepted, latencyMs);

        return GatewayResponse.builder()
            .accepted(accepted)
            .gatewayTransactionId("pu_" + UUID.randomUUID().toString().substring(0, 8))
            .message(accepted ? "Transaction accepted" : "Bank declined")
            .simulatedLatencyMs(latencyMs)
            .build();
    }
}
