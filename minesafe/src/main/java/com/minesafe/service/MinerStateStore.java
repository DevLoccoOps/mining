package com.minesafe.service;

import com.minesafe.dto.Alert;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store of every currently tracked miner, porting the legacy Python
 * {@code active_miners} dict at {@code app.py:35,236-240}.
 *
 * <p>Provides handover hysteresis (only update a MAC's snapshot if the new RSSI
 * is within {@value HANDOVER_HYSTERESIS} dBm of the old, or the last update was
 * more than {@value STALE_UPDATE_SECONDS} seconds ago), stale miner eviction
 * (miners not seen for {@value STALE_MINER_TIMEOUT} seconds), and UNREGISTERED
 * alert deduplication.
 *
 * <p>Thread safety: backed by {@link ConcurrentHashMap}. The hysteresis check
 * and subsequent update are not atomic together, but the consequence of a
 * race is at worst a double update for the same MAC, which is harmless.
 */
@Component
public class MinerStateStore {

    private static final double HANDOVER_HYSTERESIS = 5.0;
    private static final double STALE_UPDATE_SECONDS = 5.0;
    private static final double STALE_MINER_TIMEOUT = 60.0;

    private final ConcurrentHashMap<String, MinerSnapshot> miners = new ConcurrentHashMap<>();

    /** Immutable snapshot of a miner's current state, matching Python's active_miners entry. */
    public record MinerSnapshot(
            String mac,
            String name,
            String zone,
            String reporter,
            BigDecimal temperature,
            Integer battery,
            double distance,
            double rssi,
            List<Alert> alerts,
            boolean outdoor,
            boolean moving,
            double lastSeenEpoch,
            boolean registered) {}

    /**
     * Handover hysteresis check, ported from {@code app.py:228-233}.
     *
     * @return true if the snapshot should be updated (MAC is new, RSSI is strong
     *         enough, or the last update was more than 5 seconds ago)
     */
    public boolean shouldUpdate(String mac, double newRssi) {
        MinerSnapshot existing = miners.get(mac);
        if (existing == null) {
            return true;
        }
        double curr = nowEpoch();
        if (newRssi > existing.rssi() - HANDOVER_HYSTERESIS) {
            return true;
        }
        return curr - existing.lastSeenEpoch() > STALE_UPDATE_SECONDS;
    }

    public void update(MinerSnapshot snapshot) {
        miners.put(snapshot.mac(), snapshot);
    }

    public MinerSnapshot get(String mac) {
        return miners.get(mac);
    }

    public Collection<MinerSnapshot> all() {
        return miners.values();
    }

    public int activeCount() {
        return miners.size();
    }

    /**
     * Evicts miners not seen for {@value STALE_MINER_TIMEOUT} seconds, ported
     * from {@code app.py:259}.
     *
     * @return number of miners evicted
     */
    public int evictStale() {
        double cutoff = nowEpoch() - STALE_MINER_TIMEOUT;
        int evicted = 0;
        for (var entry : miners.entrySet()) {
            if (entry.getValue().lastSeenEpoch() < cutoff) {
                miners.remove(entry.getKey());
                evicted++;
            }
        }
        return evicted;
    }

    /**
     * Checks if the stored snapshot for this MAC already has an UNREGISTERED
     * alert, for deduplication. Ported from {@code app.py:223}.
     */
    public boolean hasUnregisteredAlert(String mac) {
        MinerSnapshot existing = miners.get(mac);
        if (existing == null || existing.alerts() == null) {
            return false;
        }
        return existing.alerts().stream()
                .anyMatch(a -> a.message().contains("UNREGISTERED"));
    }

    private static double nowEpoch() {
        return Instant.now().toEpochMilli() / 1000.0;
    }
}