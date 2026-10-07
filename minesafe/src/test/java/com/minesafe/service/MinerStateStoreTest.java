package com.minesafe.service;

import com.minesafe.dto.Alert;
import com.minesafe.service.MinerStateStore.MinerSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinerStateStoreTest {

    private final MinerStateStore store = new MinerStateStore();

    private MinerSnapshot snapshot(String mac, double rssi, double lastSeenEpoch) {
        return new MinerSnapshot(mac, "Test", "ZONE1", "KNOT_ZONE1",
                BigDecimal.valueOf(20), 90, 3.5, rssi,
                List.of(), false, true, lastSeenEpoch, true);
    }

    @Test
    void newMacAlwaysUpdates() {
        assertTrue(store.shouldUpdate("AA:BB:CC:DD:EE:FF", -90));
    }

    @Test
    void hysteresisBlocksWeakerRssi() {
        store.update(snapshot("AA:BB:CC:DD:EE:FF", -60, now()));

        // 10 dBm weaker than the stored -60 and within the 5s stale window: blocked.
        assertFalse(store.shouldUpdate("AA:BB:CC:DD:EE:FF", -70));
        // Within 5 dBm of the stored value: allowed.
        assertTrue(store.shouldUpdate("AA:BB:CC:DD:EE:FF", -56));
    }

    @Test
    void staleSnapshotAlwaysUpdates() {
        // Last seen 10s ago: even a much weaker RSSI must refresh the snapshot.
        store.update(snapshot("AA:BB:CC:DD:EE:FF", -60, now() - 10));
        assertTrue(store.shouldUpdate("AA:BB:CC:DD:EE:FF", -90));
    }

    @Test
    void evictStaleRemovesOnlyExpiredMiners() {
        store.update(snapshot("AA:BB:CC:DD:EE:01", -60, now()));
        store.update(snapshot("AA:BB:CC:DD:EE:02", -60, now() - 120));

        assertEquals(1, store.evictStale());
        assertEquals(1, store.activeCount());
        assertTrue(store.get("AA:BB:CC:DD:EE:02") == null);
    }

    @Test
    void unregisteredDedupChecksStoredSnapshot() {
        assertFalse(store.hasUnregisteredAlert("AA:BB:CC:DD:EE:FF"));

        MinerSnapshot unregistered = new MinerSnapshot("AA:BB:CC:DD:EE:FF", "Unknown", "ZONE1",
                "KNOT_ZONE1", null, null, 1.0, -60,
                List.of(new Alert("warning", "⚠️ UNREGISTERED TAG")), false, true, now(), false);
        store.update(unregistered);

        assertTrue(store.hasUnregisteredAlert("AA:BB:CC:DD:EE:FF"));
    }

    private static double now() {
        return System.currentTimeMillis() / 1000.0;
    }
}