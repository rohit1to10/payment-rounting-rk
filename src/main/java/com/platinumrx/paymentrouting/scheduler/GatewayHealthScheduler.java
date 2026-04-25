package com.platinumrx.paymentrouting.scheduler;

import com.platinumrx.paymentrouting.config.GatewayConfig;
import com.platinumrx.paymentrouting.model.GatewayState;
import com.platinumrx.paymentrouting.store.GatewayStatsStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class GatewayHealthScheduler {

    private static final Logger log = LoggerFactory.getLogger(GatewayHealthScheduler.class);

    private final GatewayStatsStore statsStore;
    private final GatewayConfig config;

    public GatewayHealthScheduler(GatewayStatsStore statsStore, GatewayConfig config) {
        this.statsStore = statsStore;
        this.config = config;
    }

    /** Checks every minute whether OPEN gateways have served their cooldown. */
    @Scheduled(fixedRate = 60_000)
    public void checkCooldowns() {
        statsStore.getAll().forEach(stats -> {
            if (stats.getState() == GatewayState.OPEN
                && stats.getCooldownUntil() != null
                && Instant.now().isAfter(stats.getCooldownUntil())) {

                log.info("[SCHEDULER] {} cooldown expired — entering HALF_OPEN (probing recovery)",
                    stats.getGatewayName());
                stats.setState(GatewayState.HALF_OPEN);
            }
        });
    }

    /** Purges stale sliding-window entries every 5 minutes to free memory. */
    @Scheduled(fixedRate = 300_000)
    public void purgeOldWindowEvents() {
        Instant cutoff = Instant.now().minus(config.getHealth().getWindowMinutes(), ChronoUnit.MINUTES);
        statsStore.getAll().forEach(stats -> {
            stats.getSuccessWindow().removeIf(t -> t.isBefore(cutoff));
            stats.getFailureWindow().removeIf(t -> t.isBefore(cutoff));
        });
        log.debug("[SCHEDULER] Sliding-window purge complete (cutoff={})", cutoff);
    }

    /** Logs a health snapshot every 2 minutes for observability. */
    @Scheduled(fixedRate = 120_000)
    public void logHealthSnapshot() {
        log.info("[SNAPSHOT] Gateway health:");
        statsStore.getAll().forEach(stats -> log.info(
            "  {} | state={} | weight={} | total_ok={} | total_fail={} | cooldown_until={}",
            stats.getGatewayName(), stats.getState(),
            String.format("%.1f", stats.getDynamicWeight()),
            stats.getTotalSuccess(), stats.getTotalFailure(),
            stats.getCooldownUntil() != null ? stats.getCooldownUntil() : "N/A"
        ));
    }
}
