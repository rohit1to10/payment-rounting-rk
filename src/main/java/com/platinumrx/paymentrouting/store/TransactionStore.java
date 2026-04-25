package com.platinumrx.paymentrouting.store;

import com.platinumrx.paymentrouting.model.Transaction;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class TransactionStore {

    private final Map<String, Transaction> byTransactionId = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<String>> orderToTxIds = new ConcurrentHashMap<>();

    public void save(Transaction tx) {
        byTransactionId.put(tx.getTransactionId(), tx);
        orderToTxIds
            .computeIfAbsent(tx.getOrderId(), k -> new CopyOnWriteArrayList<>())
            .add(tx.getTransactionId());
    }

    public Optional<Transaction> findById(String transactionId) {
        return Optional.ofNullable(byTransactionId.get(transactionId));
    }

    public List<Transaction> findByOrderId(String orderId) {
        List<String> txIds = orderToTxIds.getOrDefault(orderId, new CopyOnWriteArrayList<>());
        return txIds.stream()
            .map(byTransactionId::get)
            .filter(Objects::nonNull)
            .toList();
    }

    public Collection<Transaction> getAll() {
        return Collections.unmodifiableCollection(byTransactionId.values());
    }
}
