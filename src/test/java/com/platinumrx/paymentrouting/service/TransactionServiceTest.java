package com.platinumrx.paymentrouting.service;

import com.platinumrx.paymentrouting.callback.*;
import com.platinumrx.paymentrouting.config.GatewayConfig;
import com.platinumrx.paymentrouting.dto.CallbackRequest;
import com.platinumrx.paymentrouting.dto.InitiateRequest;
import com.platinumrx.paymentrouting.dto.InitiateResponse;
import com.platinumrx.paymentrouting.gateway.CashfreeGatewayClient;
import com.platinumrx.paymentrouting.gateway.MockGatewayClient;
import com.platinumrx.paymentrouting.gateway.PayUGatewayClient;
import com.platinumrx.paymentrouting.gateway.RazorpayGatewayClient;
import com.platinumrx.paymentrouting.model.PaymentInstrument;
import com.platinumrx.paymentrouting.model.TransactionStatus;
import com.platinumrx.paymentrouting.routing.RoutingEngine;
import com.platinumrx.paymentrouting.store.GatewayStatsStore;
import com.platinumrx.paymentrouting.store.TransactionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionServiceTest {

    private TransactionService service;
    private TransactionStore transactionStore;
    private GatewayStatsStore statsStore;

    @BeforeEach
    void setUp() {
        GatewayConfig config = buildConfig();
        transactionStore = new TransactionStore();
        statsStore = new GatewayStatsStore(config);
        statsStore.init();

        RoutingEngine routingEngine = new RoutingEngine(statsStore, config);
        GatewayHealthService healthService = new GatewayHealthService(statsStore, config);

        List<CallbackParser> parsers = List.of(
            new RazorpayCallbackParser(),
            new PayUCallbackParser(),
            new CashfreeCallbackParser()
        );
        List<MockGatewayClient> clients = List.of(
            new RazorpayGatewayClient(config),
            new PayUGatewayClient(config),
            new CashfreeGatewayClient(config)
        );

        service = new TransactionService(transactionStore, routingEngine, healthService, parsers, clients);
    }

    @Test
    void initiateCreatesTransactionWithPendingStatus() {
        InitiateResponse response = service.initiate(buildInitiateRequest("ORD100", 499.0));

        assertThat(response.getTransactionId()).isNotBlank();
        assertThat(response.getOrderId()).isEqualTo("ORD100");
        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getAttemptNumber()).isEqualTo(1);
        assertThat(response.getGateway()).isIn("razorpay", "payu", "cashfree");
        assertThat(response.getRoutingMetadata()).isNotNull();
        assertThat(response.getRoutingMetadata().getGatewayScores()).isNotEmpty();
    }

    @Test
    void multipleAttemptsOnSameOrderIncrementAttemptNumber() {
        InitiateResponse first = service.initiate(buildInitiateRequest("ORD101", 100.0));
        InitiateResponse second = service.initiate(buildInitiateRequest("ORD101", 100.0));
        InitiateResponse third = service.initiate(buildInitiateRequest("ORD101", 100.0));

        assertThat(first.getAttemptNumber()).isEqualTo(1);
        assertThat(second.getAttemptNumber()).isEqualTo(2);
        assertThat(third.getAttemptNumber()).isEqualTo(3);
    }

    @Test
    void retryPrefersADifferentGateway() {
        // Run many transactions on ORD102 and collect the gateways used
        // With 3 attempts and retry penalty, the engine tries to diversify
        InitiateResponse first  = service.initiate(buildInitiateRequest("ORD102", 50.0));
        InitiateResponse second = service.initiate(buildInitiateRequest("ORD102", 50.0));
        InitiateResponse third  = service.initiate(buildInitiateRequest("ORD102", 50.0));

        // By the third attempt we should have seen at least 2 different gateways
        long distinctGateways = List.of(first.getGateway(), second.getGateway(), third.getGateway())
            .stream().distinct().count();
        assertThat(distinctGateways).isGreaterThanOrEqualTo(2);
    }

    @Test
    void callbackUpdatesTransactionStatusToSuccess() {
        InitiateResponse init = service.initiate(buildInitiateRequest("ORD103", 200.0));
        String gateway = init.getGateway();

        Map<String, String> result = service.handleCallback(
            buildCallbackRequest("ORD103", successStatusFor(gateway), gateway, null));

        assertThat(result.get("status")).isEqualTo("processed");
        assertThat(result.get("outcome")).isEqualTo("SUCCESS");

        // Verify the stored transaction
        var transactions = service.getByOrderId("ORD103");
        assertThat(transactions).hasSize(1);
        assertThat(transactions.getFirst().getStatus()).isEqualTo(TransactionStatus.SUCCESS);
    }

    @Test
    void callbackUpdatesTransactionStatusToFailure() {
        InitiateResponse init = service.initiate(buildInitiateRequest("ORD104", 300.0));
        String gateway = init.getGateway();

        service.handleCallback(
            buildCallbackRequest("ORD104", failureStatusFor(gateway), gateway, "Insufficient funds"));

        var tx = service.getByOrderId("ORD104").getFirst();
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.FAILURE);
        assertThat(tx.getReason()).isEqualTo("Insufficient funds");
    }

    @Test
    void callbackForUnknownOrderReturnsNoPendingTransaction() {
        Map<String, String> result = service.handleCallback(
            buildCallbackRequest("UNKNOWN_ORDER", "captured", "razorpay", null));

        assertThat(result.get("status")).isEqualTo("no_pending_transaction");
    }

    @Test
    void callbackForUnsupportedGatewayThrows() {
        assertThatThrownBy(() ->
            service.handleCallback(buildCallbackRequest("ORD105", "success", "stripe", null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported gateway");
    }

    @Test
    void getAllReturnsAllTransactions() {
        service.initiate(buildInitiateRequest("ORD106", 1.0));
        service.initiate(buildInitiateRequest("ORD107", 2.0));

        assertThat(service.getAll()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void routingMetadataContainsScoresAndFallbackCandidates() {
        InitiateResponse response = service.initiate(buildInitiateRequest("ORD108", 99.0));

        assertThat(response.getRoutingMetadata().getAvailableGateways()).isNotEmpty();
        assertThat(response.getRoutingMetadata().getScore()).isGreaterThan(0);
        assertThat(response.getRoutingMetadata().getReason()).isNotBlank();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private InitiateRequest buildInitiateRequest(String orderId, double amount) {
        PaymentInstrument pi = new PaymentInstrument();
        pi.setType("card");
        pi.setCardNumber("****");
        pi.setExpiry("12/26");

        InitiateRequest req = new InitiateRequest();
        req.setOrderId(orderId);
        req.setAmount(amount);
        req.setPaymentInstrument(pi);
        return req;
    }

    private CallbackRequest buildCallbackRequest(String orderId, String status, String gateway, String reason) {
        CallbackRequest req = new CallbackRequest();
        req.setOrderId(orderId);
        req.setStatus(status);
        req.setGateway(gateway);
        req.setReason(reason);
        return req;
    }

    /** Returns the "success" status string for a given gateway. */
    private String successStatusFor(String gateway) {
        return switch (gateway.toLowerCase()) {
            case "razorpay" -> "captured";
            case "cashfree" -> "SUCCESS";
            default -> "success";
        };
    }

    /** Returns a "failure" status string for a given gateway. */
    private String failureStatusFor(String gateway) {
        return switch (gateway.toLowerCase()) {
            case "cashfree" -> "FAILED";
            case "payu" -> "failure";
            default -> "failed";
        };
    }

    private GatewayConfig buildConfig() {
        GatewayConfig config = new GatewayConfig();
        GatewayConfig.HealthConfig health = new GatewayConfig.HealthConfig();
        health.setWindowMinutes(15);
        health.setThreshold(0.90);
        health.setCooldownMinutes(30);
        health.setMinSamples(5);
        health.setHalfOpenTrafficPercent(5);
        config.setHealth(health);

        Map<String, GatewayConfig.GatewayProperties> gateways = new HashMap<>();

        GatewayConfig.GatewayProperties rp = new GatewayConfig.GatewayProperties();
        rp.setWeight(40); rp.setFailureRate(0); rp.setLatencyMs(10);
        gateways.put("razorpay", rp);

        GatewayConfig.GatewayProperties pu = new GatewayConfig.GatewayProperties();
        pu.setWeight(35); pu.setFailureRate(0); pu.setLatencyMs(10);
        gateways.put("payu", pu);

        GatewayConfig.GatewayProperties cf = new GatewayConfig.GatewayProperties();
        cf.setWeight(25); cf.setFailureRate(0); cf.setLatencyMs(10);
        gateways.put("cashfree", cf);

        config.setGateways(gateways);
        return config;
    }
}
