package com.platinumrx.paymentrouting.store;

import com.platinumrx.paymentrouting.config.GatewayConfig;
import com.platinumrx.paymentrouting.model.GatewayStats;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GatewayStatsStore {

    private final GatewayConfig config;
    private final Map<String, GatewayStats> statsMap = new ConcurrentHashMap<>();

    public GatewayStatsStore(GatewayConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        config.getGateways().forEach((name, props) ->
            statsMap.put(name, new GatewayStats(name, props.getWeight()))
        );
    }

    public GatewayStats get(String gateway) {
        GatewayStats stats = statsMap.get(gateway.toLowerCase());
        if (stats == null) {
            throw new IllegalArgumentException("Unknown gateway: " + gateway);
        }
        return stats;
    }

    public Collection<GatewayStats> getAll() {
        return Collections.unmodifiableCollection(statsMap.values());
    }

    public void recordSuccess(String gateway) {
        GatewayStats stats = get(gateway);
        stats.getSuccessWindow().addLast(Instant.now());
        stats.incrementSuccess();
    }

    public void recordFailure(String gateway) {
        GatewayStats stats = get(gateway);
        stats.getFailureWindow().addLast(Instant.now());
        stats.incrementFailure();
    }

    public boolean containsGateway(String gateway) {
        return statsMap.containsKey(gateway.toLowerCase());
    }
}
