package com.platinumrx.paymentrouting.controller;

import com.platinumrx.paymentrouting.dto.CallbackRequest;
import com.platinumrx.paymentrouting.dto.InitiateRequest;
import com.platinumrx.paymentrouting.dto.InitiateResponse;
import com.platinumrx.paymentrouting.model.Transaction;
import com.platinumrx.paymentrouting.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/initiate")
    public ResponseEntity<InitiateResponse> initiate(@Valid @RequestBody InitiateRequest request) {
        return ResponseEntity.ok(transactionService.initiate(request));
    }

    @PostMapping("/callback")
    public ResponseEntity<Map<String, String>> callback(@Valid @RequestBody CallbackRequest request) {
        return ResponseEntity.ok(transactionService.handleCallback(request));
    }

    @GetMapping
    public ResponseEntity<List<Transaction>> getAll() {
        return ResponseEntity.ok(transactionService.getAll());
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<Transaction> getById(@PathVariable String transactionId) {
        return transactionService.getByTransactionId(transactionId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<Transaction>> getByOrderId(@PathVariable String orderId) {
        return ResponseEntity.ok(transactionService.getByOrderId(orderId));
    }
}
