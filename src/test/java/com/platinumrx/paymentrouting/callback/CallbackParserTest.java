package com.platinumrx.paymentrouting.callback;

import com.platinumrx.paymentrouting.dto.CallbackRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the strategy-pattern callback parsers for each gateway.
 * Each gateway interprets "status" differently — these tests verify that mapping.
 */
class CallbackParserTest {

    // ─── Razorpay ───────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"captured", "CAPTURED", "success", "SUCCESS"})
    void razorpay_successStatuses(String status) {
        RazorpayCallbackParser parser = new RazorpayCallbackParser();
        ParsedCallback result = parser.parse(buildRequest("ORD1", status, "razorpay", null));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getGateway()).isEqualTo("razorpay");
        assertThat(result.getOrderId()).isEqualTo("ORD1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"failed", "FAILED", "refunded", "pending"})
    void razorpay_failureStatuses(String status) {
        RazorpayCallbackParser parser = new RazorpayCallbackParser();
        ParsedCallback result = parser.parse(buildRequest("ORD1", status, "razorpay", "Card declined"));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getReason()).isEqualTo("Card declined");
    }

    // ─── PayU ───────────────────────────────────────────────────────────────

    @Test
    void payu_successStatus() {
        PayUCallbackParser parser = new PayUCallbackParser();
        ParsedCallback result = parser.parse(buildRequest("ORD2", "success", "payu", null));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getGateway()).isEqualTo("payu");
    }

    @ParameterizedTest
    @ValueSource(strings = {"failure", "FAILURE", "userCancelled", "pending"})
    void payu_failureStatuses(String status) {
        PayUCallbackParser parser = new PayUCallbackParser();
        ParsedCallback result = parser.parse(buildRequest("ORD2", status, "payu", "Customer Cancelled"));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getReason()).isEqualTo("Customer Cancelled");
    }

    // ─── Cashfree ────────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {"SUCCESS", "success"})
    void cashfree_successStatuses(String status) {
        CashfreeCallbackParser parser = new CashfreeCallbackParser();
        ParsedCallback result = parser.parse(buildRequest("ORD3", status, "cashfree", null));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getGateway()).isEqualTo("cashfree");
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAILED", "USER_DROPPED", "CANCELLED", "PENDING"})
    void cashfree_failureStatuses(String status) {
        CashfreeCallbackParser parser = new CashfreeCallbackParser();
        ParsedCallback result = parser.parse(buildRequest("ORD3", status, "cashfree", "Insufficient funds"));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getReason()).isEqualTo("Insufficient funds");
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    private CallbackRequest buildRequest(String orderId, String status, String gateway, String reason) {
        CallbackRequest req = new CallbackRequest();
        req.setOrderId(orderId);
        req.setStatus(status);
        req.setGateway(gateway);
        req.setReason(reason);
        return req;
    }
}
