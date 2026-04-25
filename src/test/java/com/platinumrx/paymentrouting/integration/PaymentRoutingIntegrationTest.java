package com.platinumrx.paymentrouting.integration;

import com.platinumrx.paymentrouting.dto.GatewayStatsResponse;
import com.platinumrx.paymentrouting.model.GatewayState;
import com.platinumrx.paymentrouting.store.GatewayStatsStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests using the full Spring context and a real HTTP server.
 * No mocking — validates the actual REST API behaviour.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaymentRoutingIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private GatewayStatsStore statsStore;

    private String base;

    @BeforeEach
    void setUp() {
        base = "http://localhost:" + port;
        // Reset all gateways to CLOSED before each test
        statsStore.getAll().forEach(g -> {
            g.setState(GatewayState.CLOSED);
            g.setCooldownUntil(null);
        });
    }

    // ─── Initiate API ────────────────────────────────────────────────────────

    @Test
    void initiate_returnsTransactionWithPendingStatus() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
            base + "/transactions/initiate",
            initiateBody("ORD-INT-001", 499.0),
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("transactionId")).isNotNull();
        assertThat(body.get("status")).isEqualTo("PENDING");
        assertThat(body.get("orderId")).isEqualTo("ORD-INT-001");
        assertThat(body.get("gateway")).isIn("razorpay", "payu", "cashfree");
        assertThat(body.get("routingMetadata")).isNotNull();
    }

    @Test
    void initiate_routingMetadataContainsAllScores() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
            base + "/transactions/initiate",
            initiateBody("ORD-INT-002", 100.0),
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> metadata = (Map<?, ?>) response.getBody().get("routingMetadata");
        assertThat(metadata.get("gatewayScores")).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, ?> scores = (Map<String, ?>) metadata.get("gatewayScores");
        assertThat(scores).containsKeys("razorpay", "payu", "cashfree");
    }

    @Test
    void initiate_missingOrderId_returns400() {
        String badBody = """
            {"amount": 100.0, "payment_instrument": {"type": "card"}}
            """;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.postForEntity(
            base + "/transactions/initiate",
            new HttpEntity<>(badBody, headers),
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void initiate_multipleAttemptsOnSameOrder_incrementAttemptNumber() {
        HttpEntity<String> body = initiateBody("ORD-INT-003", 200.0);
        Map first  = restTemplate.postForObject(base + "/transactions/initiate", body, Map.class);
        Map second = restTemplate.postForObject(base + "/transactions/initiate", body, Map.class);

        assertThat(first.get("attemptNumber")).isEqualTo(1);
        assertThat(second.get("attemptNumber")).isEqualTo(2);
    }

    // ─── Callback API ────────────────────────────────────────────────────────

    @Test
    void callback_successUpdatesTransactionAndGatewayStats() {
        // 1. Initiate
        Map initResp = restTemplate.postForObject(
            base + "/transactions/initiate",
            initiateBody("ORD-INT-004", 299.0),
            Map.class
        );
        String gateway = (String) initResp.get("gateway");

        // 2. Callback success
        ResponseEntity<Map> cbResp = restTemplate.postForEntity(
            base + "/transactions/callback",
            callbackBody("ORD-INT-004", successStatusFor(gateway), gateway, null),
            Map.class
        );

        assertThat(cbResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cbResp.getBody().get("status")).isEqualTo("processed");
        assertThat(cbResp.getBody().get("outcome")).isEqualTo("SUCCESS");

        // 3. Verify gateway success count incremented
        GatewayStatsResponse stats = getGatewayStats(gateway);
        assertThat(stats.getTotalSuccess()).isGreaterThan(0);
    }

    @Test
    void callback_failureTriggersWeightDecay() {
        Map initResp = restTemplate.postForObject(
            base + "/transactions/initiate",
            initiateBody("ORD-INT-005", 150.0),
            Map.class
        );
        String gateway = (String) initResp.get("gateway");
        double weightBefore = getGatewayStats(gateway).getDynamicWeight();

        restTemplate.postForEntity(
            base + "/transactions/callback",
            callbackBody("ORD-INT-005", failureStatusFor(gateway), gateway, "Bank error"),
            Map.class
        );

        double weightAfter = getGatewayStats(gateway).getDynamicWeight();
        assertThat(weightAfter).isLessThan(weightBefore);
    }

    @Test
    void callback_unknownGateway_returns400() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
            base + "/transactions/callback",
            callbackBody("ORD-INT-006", "success", "stripe", null),
            Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void callback_noPendingTransaction_returnsNoPendingStatus() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
            base + "/transactions/callback",
            callbackBody("NO_SUCH_ORDER", "captured", "razorpay", null),
            Map.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("no_pending_transaction");
    }

    // ─── Gateway Management API ──────────────────────────────────────────────

    @Test
    void gatewayStats_returnsAllThreeGateways() {
        ResponseEntity<List> response = restTemplate.getForEntity(
            base + "/gateways/stats", List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(3);
    }

    @Test
    void forceUnhealthy_marksGatewayAsOpen() {
        restTemplate.postForEntity(base + "/gateways/razorpay/force-unhealthy", null, Map.class);

        GatewayStatsResponse stats = getGatewayStats("razorpay");
        assertThat(stats.getState()).isEqualTo(GatewayState.OPEN);
        assertThat(stats.getCooldownUntil()).isNotNull();
    }

    @Test
    void resetGateway_restoresClosedState() {
        // Force unhealthy first
        restTemplate.postForEntity(base + "/gateways/razorpay/force-unhealthy", null, Map.class);
        assertThat(getGatewayStats("razorpay").getState()).isEqualTo(GatewayState.OPEN);

        // Then reset
        restTemplate.postForEntity(base + "/gateways/razorpay/reset", null, Map.class);
        assertThat(getGatewayStats("razorpay").getState()).isEqualTo(GatewayState.CLOSED);
    }

    @Test
    void injectFailures_degradesGatewayHealth() {
        // Inject 10 failures into razorpay
        restTemplate.postForEntity(
            base + "/gateways/razorpay/inject-failures?count=10", null, Map.class);

        GatewayStatsResponse stats = getGatewayStats("razorpay");
        // Should have failures recorded
        assertThat(stats.getTotalFailure()).isGreaterThanOrEqualTo(10);
        // State should be OPEN (10 failures / (0+10) = 0% success, below 90% threshold)
        assertThat(stats.getState()).isEqualTo(GatewayState.OPEN);
    }

    @Test
    void failover_trafficShiftsWhenGatewayUnhealthy() {
        // Make razorpay OPEN
        restTemplate.postForEntity(base + "/gateways/razorpay/force-unhealthy", null, Map.class);

        // Next transaction must NOT go to razorpay
        Map response = restTemplate.postForObject(
            base + "/transactions/initiate",
            initiateBody("ORD-INT-007", 500.0),
            Map.class
        );

        assertThat(response.get("gateway")).isNotEqualTo("razorpay");
        List<?> available = (List<?>) ((Map<?, ?>) response.get("routingMetadata")).get("availableGateways");
        assertThat(available).noneMatch(e -> "razorpay".equals(e));
    }

    @Test
    void queryTransactionById_returnsCorrectTransaction() {
        Map init = restTemplate.postForObject(
            base + "/transactions/initiate",
            initiateBody("ORD-INT-008", 75.0),
            Map.class
        );
        String txId = (String) init.get("transactionId");

        ResponseEntity<Map> tx = restTemplate.getForEntity(
            base + "/transactions/" + txId, Map.class);

        assertThat(tx.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tx.getBody().get("transactionId")).isEqualTo(txId);
        assertThat(tx.getBody().get("orderId")).isEqualTo("ORD-INT-008");
    }

    @Test
    void queryByOrderId_returnsAllAttempts() {
        restTemplate.postForObject(base + "/transactions/initiate", initiateBody("ORD-INT-009", 10.0), Map.class);
        restTemplate.postForObject(base + "/transactions/initiate", initiateBody("ORD-INT-009", 10.0), Map.class);

        ResponseEntity<List> response = restTemplate.getForEntity(
            base + "/transactions/order/ORD-INT-009", List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private HttpEntity<String> initiateBody(String orderId, double amount) {
        String json = String.format("""
            {
              "order_id": "%s",
              "amount": %.1f,
              "payment_instrument": {"type": "card", "card_number": "****", "expiry": "12/26"}
            }
            """, orderId, amount);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(json, h);
    }

    private HttpEntity<String> callbackBody(String orderId, String status, String gateway, String reason) {
        String reasonJson = reason != null ? String.format(", \"reason\": \"%s\"", reason) : "";
        String json = String.format("""
            {"order_id": "%s", "status": "%s", "gateway": "%s"%s}
            """, orderId, status, gateway, reasonJson);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(json, h);
    }

    @SuppressWarnings("unchecked")
    private GatewayStatsResponse getGatewayStats(String gateway) {
        ResponseEntity<List> resp = restTemplate.getForEntity(base + "/gateways/stats", List.class);
        for (Object o : resp.getBody()) {
            Map<String, Object> m = (Map<String, Object>) o;
            if (gateway.equals(m.get("gatewayName"))) {
                return GatewayStatsResponse.builder()
                    .gatewayName((String) m.get("gatewayName"))
                    .state(GatewayState.valueOf((String) m.get("state")))
                    .dynamicWeight(((Number) m.get("dynamicWeight")).doubleValue())
                    .totalSuccess(((Number) m.get("totalSuccess")).longValue())
                    .totalFailure(((Number) m.get("totalFailure")).longValue())
                    .recentSuccessRate(((Number) m.get("recentSuccessRate")).doubleValue())
                    .cooldownUntil(m.get("cooldownUntil") != null
                        ? java.time.Instant.parse((String) m.get("cooldownUntil")) : null)
                    .build();
            }
        }
        throw new AssertionError("Gateway not found: " + gateway);
    }

    private String successStatusFor(String gateway) {
        return switch (gateway.toLowerCase()) {
            case "razorpay" -> "captured";
            case "cashfree" -> "SUCCESS";
            default -> "success";
        };
    }

    private String failureStatusFor(String gateway) {
        return switch (gateway.toLowerCase()) {
            case "cashfree" -> "FAILED";
            case "payu" -> "failure";
            default -> "failed";
        };
    }
}
