package com.minesafe.service;

import com.minesafe.dto.Alert;
import com.minesafe.repository.AlertLogRepository;
import com.minesafe.ws.LiveHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scheduled broadcaster that aggregates the {@link MinerStateStore} into a
 * zone-grouped {@code state_update} payload and fans it out via {@link LiveHub}
 * every 500 ms, porting the legacy Python {@code broadcast_aggregated_state()}
 * at {@code app.py:253-286} and the 500 ms tick at {@code app.py:247-249}.
 *
 * <p>Also runs an hourly retention cleanup that deletes old {@code alert_logs}
 * rows exceeding the configured maximum, porting the Python cap at
 * {@code app.py:103}.
 */
@Component
public class StateBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(StateBroadcaster.class);

    private static final int MAX_BROADCAST_ALERTS = 50;

    private static final Comparator<AlertEntry> SEVERITY_ORDER =
            Comparator.comparingInt(a -> switch (a.type()) {
                case "critical" -> 0;
                case "danger" -> 1;
                case "warning" -> 2;
                default -> 3;
            });

    private final MinerStateStore minerStateStore;
    private final LiveHub liveHub;
    private final AlertLogRepository alertLogRepository;
    private final int maxRetention;

    public StateBroadcaster(MinerStateStore minerStateStore,
                            LiveHub liveHub,
                            AlertLogRepository alertLogRepository,
                            @Value("${app.alerts.max-retention:50000}") int maxRetention) {
        this.minerStateStore = minerStateStore;
        this.liveHub = liveHub;
        this.alertLogRepository = alertLogRepository;
        this.maxRetention = maxRetention;
    }

    /**
     * Broadcasts the aggregated state_update every 500 ms.
     * Evicts stale miners first, then builds zone summaries and a sorted alert list.
     */
    @Scheduled(fixedRate = 500)
    public void broadcastState() {
        int evicted = minerStateStore.evictStale();
        if (evicted > 0) {
            log.debug("Evicted {} stale miners", evicted);
        }

        // Note: broadcast even when empty (total_miners: 0) — matches app.py:286,
        // which always emits state_update so clients see miners disappear.
        var snapshots = List.copyOf(minerStateStore.all());

        // Group by zone and aggregate
        Map<String, ZoneAccumulator> zones = new HashMap<>();
        List<AlertEntry> allAlerts = new ArrayList<>();

        for (var snap : snapshots) {
            String zone = snap.zone() != null ? snap.zone() : "UNKNOWN";
            ZoneAccumulator za = zones.computeIfAbsent(zone, k -> new ZoneAccumulator());
            za.total++;
            if (snap.alerts() != null) {
                for (Alert a : snap.alerts()) {
                    switch (a.type()) {
                        case "critical" -> za.critical++;
                        case "danger" -> za.danger++;
                        case "warning" -> za.warning++;
                    }
                    allAlerts.add(new AlertEntry(snap.mac(), snap.name(), zone, a.type(), a.message()));
                }
            }
            if (snap.temperature() != null) {
                za.temps.add(snap.temperature().doubleValue());
            }
        }

        allAlerts.sort(SEVERITY_ORDER);
        if (allAlerts.size() > MAX_BROADCAST_ALERTS) {
            allAlerts = allAlerts.subList(0, MAX_BROADCAST_ALERTS);
        }

        List<ZoneSummary> zoneList = new ArrayList<>();
        for (var entry : zones.entrySet()) {
            ZoneAccumulator za = entry.getValue();
            String avgTemp = za.temps.isEmpty()
                    ? "N/A"
                    : BigDecimal.valueOf(za.temps.stream().mapToDouble(d -> d).average().orElse(0))
                            .setScale(1, RoundingMode.HALF_UP).toString();
            zoneList.add(new ZoneSummary(entry.getKey(), za.total, za.critical, za.danger, za.warning, avgTemp));
        }

        StateUpdate payload = new StateUpdate(snapshots.size(), zoneList, allAlerts);
        liveHub.broadcast(payload);
    }

    /**
     * Hourly retention cleanup: deletes old alert_logs rows exceeding the
     * configured maximum. Ported from the Python cap at {@code app.py:103}.
     */
    @Scheduled(fixedRate = 3600000)
    public void enforceRetention() {
        try {
            int deleted = alertLogRepository.deleteExceedingRetention(maxRetention);
            if (deleted > 0) {
                log.info("Retention cleanup: deleted {} old alert_logs rows (max={})", deleted, maxRetention);
            }
        } catch (Exception e) {
            log.warn("Retention cleanup failed: {}", e.getMessage());
        }
    }

    // --- Payload records (serialized as snake_case by Jackson) ---

    public record StateUpdate(int totalMiners, List<ZoneSummary> zones, List<AlertEntry> alerts) {}

    public record ZoneSummary(String zone, int total, int critical, int danger, int warning, String avgTemp) {}

    public record AlertEntry(String mac, String name, String zone, String type, String msg) {}

    private static final class ZoneAccumulator {
        int total;
        int critical;
        int danger;
        int warning;
        final List<Double> temps = new ArrayList<>();
    }
}