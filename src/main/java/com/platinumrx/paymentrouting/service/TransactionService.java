package com.platinumrx.paymentrouting.service;

import com.platinumrx.paymentrouting.callback.CallbackParser;
import com.platinumrx.paymentrouting.callback.ParsedCallback;
import com.platinumrx.paymentrouting.dto.*;
import com.platinumrx.paymentrouting.gateway.MockGatewayClient;
import com.platinumrx.paymentrouting.model.Transaction;
import com.platinumrx.paymentrouting.model.TransactionStatus;
import com.platinumrx.paymentrouting.routing.RoutingEngine;
import com.platinumrx.paymentrouting.routing.RoutingResult;
import com.platinumrx.paymentrouting.store.TransactionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionStore transactionStore;
    private final RoutingEngine routingEngine;
    private final GatewayHealthService healthService;
    private final List<CallbackParser> callbackParsers;
    private final Map<String, MockGatewayClient> gatewayClients;

    public TransactionService(
        TransactionStore transactionStore,
        RoutingEngine routingEngine,
        GatewayHealthService healthService,
        List<CallbackParser> callbackParsers,
        List<MockGatewayClient> gatewayClientList
    ) {
        this.transactionStore = transactionStore;
        this.routingEngine = routingEngine;
        this.healthService = healthService;
        this.callbackParsers = callbackParsers;
        this.gatewayClients = gatewayClientList.stream()
            .collect(Collectors.toMap(MockGatewayClient::getGatewayName, c -> c));
    }

    public InitiateResponse initiate(InitiateRequest request) {
        List<Transaction> previousAttempts = transactionStore.findByOrderId(request.getOrderId());
        Set<String> previousGateways = previousAttempts.stream()
            .map(Transaction::getGateway)
            .collect(Collectors.toSet());

        RoutingResult routing = routingEngine.selectGateway(previousGateways);

        Transaction tx = Transaction.builder()
            .transactionId(UUID.randomUUID().toString())
            .orderId(request.getOrderId())
            .gateway(routing.getSelectedGateway())
            .status(TransactionStatus.PENDING)
            .attemptNumber(previousAttempts.size() + 1)
            .timestamp(Instant.now())
            .amount(request.getAmount())
            .paymentInstrument(request.getPaymentInstrument())
            .routingScore(routing.getScore())
            .routingReason(routing.getReason())
            .build();

        MockGatewayClient client = gatewayClients.get(routing.getSelectedGateway());
        if (client != null) {
            client.initiatePayment(tx);
        }

        transactionStore.save(tx);

        log.info("[INITIATE] orderId={} txId={} gateway={} attempt={}",
            tx.getOrderId(), tx.getTransactionId(), tx.getGateway(), tx.getAttemptNumber());

        return InitiateResponse.builder()
            .transactionId(tx.getTransactionId())
            .orderId(tx.getOrderId())
            .gateway(tx.getGateway())
            .status(tx.getStatus().name())
            .attemptNumber(tx.getAttemptNumber())
            .timestamp(tx.getTimestamp())
            .routingMetadata(RoutingMetadata.builder()
                .score(routing.getScore())
                .reason(routing.getReason())
                .availableGateways(routing.getAvailableGateways())
                .fallbackCandidates(routing.getFallbackCandidates())
                .gatewayScores(routing.getAllScores())
                .build())
            .build();
    }

    public Map<String, String> handleCallback(CallbackRequest request) {
        String gatewayName = request.getGateway().toLowerCase();

        CallbackParser parser = callbackParsers.stream()
            .filter(p -> p.supportedGateway().equalsIgnoreCase(gatewayName))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported gateway: " + request.getGateway()));

        ParsedCallback parsed = parser.parse(request);

        List<Transaction> candidates = transactionStore.findByOrderId(parsed.getOrderId()).stream()
            .filter(t -> t.getGateway().equalsIgnoreCase(parsed.getGateway()))
            .filter(t -> t.getStatus() == TransactionStatus.PENDING)
            .sorted(Comparator.comparing(Transaction::getTimestamp).reversed())
            .toList();

        if (candidates.isEmpty()) {
            log.warn("[CALLBACK] No pending transaction for orderId={}, gateway={}",
                parsed.getOrderId(), parsed.getGateway());
            return Map.of(
                "status",  "no_pending_transaction",
                "order_id", parsed.getOrderId(),
                "gateway", parsed.getGateway()
            );
        }

        Transaction tx = candidates.getFirst();
        tx.setStatus(parsed.isSuccess() ? TransactionStatus.SUCCESS : TransactionStatus.FAILURE);
        tx.setReason(parsed.getReason());

        healthService.processCallback(parsed.getGateway(), parsed.isSuccess());

        log.info("[CALLBACK] orderId={} txId={} gateway={} outcome={} reason={}",
            tx.getOrderId(), tx.getTransactionId(), tx.getGateway(),
            tx.getStatus(), tx.getReason());

        return Map.of(
            "status", "processed",
            "transaction_id", tx.getTransactionId(),
            "outcome", tx.getStatus().name()
        );
    }

    public List<Transaction> getAll() {
        return new ArrayList<>(transactionStore.getAll());
    }

    public Optional<Transaction> getByTransactionId(String txId) {
        return transactionStore.findById(txId);
    }

    public List<Transaction> getByOrderId(String orderId) {
        return transactionStore.findByOrderId(orderId);
    }
}
