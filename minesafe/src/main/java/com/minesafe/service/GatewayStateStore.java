package com.minesafe.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store of every scanner gateway that has forwarded tag telemetry,
 * keyed by reporter id (e.g. {@code KNOT_05}). Mirrors {@link MinerStateStore}
 * for the gateway side: gateways have no heartbeat of their own, so liveness is
 * inferred from the most recent telemetry they forwarded.
 */
@Component
public class GatewayStateStore {

    /** Gateways silent for this long are evicted (they reappear on next reading). */
    private static final double STALE_GATEWAY_TIMEOUT = 3600.0;

    private final ConcurrentHashMap<String, GatewaySnapshot> gateways = new ConcurrentHashMap<>();

    /** Immutable snapshot of a gateway's last known activity. */
    public record GatewaySnapshot(String id, String zone, double rssi, double lastSeenEpoch) {}

    public void seen(String reporter, double rssi) {
        seen(reporter, rssi, Instant.now().toEpochMilli() / 1000.0);
    }

    /** Test seam: same as {@link #seen(String, double)} with an explicit timestamp. */
    void seen(String reporter, double rssi, double epochSeconds) {
        if (reporter == null || reporter.isBlank()) {
            return;
        }
        String zone = reporter.replace("KNOT_", "");
        gateways.put(reporter, new GatewaySnapshot(reporter, zone, rssi, epochSeconds));
    }

    public Collection<GatewaySnapshot> all() {
        return gateways.values();
    }

    public int activeCount() {
        return gateways.size();
    }

    /** Evicts gateways silent for more than {@value STALE_GATEWAY_TIMEOUT} seconds. */
    public int evictStale() {
        double cutoff = Instant.now().toEpochMilli() / 1000.0 - STALE_GATEWAY_TIMEOUT;
        int evicted = 0;
        for (var entry : gateways.entrySet()) {
            if (entry.getValue().lastSeenEpoch() < cutoff) {
                gateways.remove(entry.getKey());
                evicted++;
            }
        }
        return evicted;
    }

    public static Comparator<GatewaySnapshot> byLastSeenDesc() {
        return Comparator.comparingDouble(GatewaySnapshot::lastSeenEpoch).reversed();
    }
}