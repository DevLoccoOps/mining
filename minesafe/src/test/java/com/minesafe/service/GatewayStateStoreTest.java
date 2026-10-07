package com.minesafe.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayStateStoreTest {

    @Test
    void seenStripsKnotPrefixIntoZone() {
        var store = new GatewayStateStore();

        store.seen("KNOT_05", -58.0);

        assertThat(store.activeCount()).isEqualTo(1);
        var gw = store.all().iterator().next();
        assertThat(gw.id()).isEqualTo("KNOT_05");
        assertThat(gw.zone()).isEqualTo("05");
        assertThat(gw.rssi()).isEqualTo(-58.0);
    }

    @Test
    void seenIgnoresBlankReporters() {
        var store = new GatewayStateStore();

        store.seen(null, -60.0);
        store.seen("", -60.0);
        store.seen("  ", -60.0);

        assertThat(store.activeCount()).isZero();
    }

    @Test
    void seenUpdatesExistingGateway() {
        var store = new GatewayStateStore();

        store.seen("KNOT_01", -60.0, 1000.0);
        store.seen("KNOT_01", -55.0, 2000.0);

        assertThat(store.activeCount()).isEqualTo(1);
        var gw = store.all().iterator().next();
        assertThat(gw.rssi()).isEqualTo(-55.0);
        assertThat(gw.lastSeenEpoch()).isEqualTo(2000.0);
    }

    @Test
    void evictStaleRemovesOnlySilentGateways() {
        var store = new GatewayStateStore();
        double now = Instant.now().toEpochMilli() / 1000.0;

        store.seen("KNOT_FRESH", -60.0, now);
        store.seen("KNOT_OLD", -60.0, now - 3700.0);

        int evicted = store.evictStale();

        assertThat(evicted).isEqualTo(1);
        assertThat(store.activeCount()).isEqualTo(1);
        assertThat(store.all().iterator().next().id()).isEqualTo("KNOT_FRESH");
    }

    @Test
    void byLastSeenDescOrdersNewestFirst() {
        var a = new GatewayStateStore.GatewaySnapshot("A", "A", -60.0, 100.0);
        var b = new GatewayStateStore.GatewaySnapshot("B", "B", -60.0, 300.0);
        var c = new GatewayStateStore.GatewaySnapshot("C", "C", -60.0, 200.0);

        List<GatewayStateStore.GatewaySnapshot> sorted =
                new java.util.ArrayList<>(List.of(a, b, c));
        sorted.sort(GatewayStateStore.byLastSeenDesc());

        assertThat(sorted).extracting(GatewayStateStore.GatewaySnapshot::id)
                .containsExactly("B", "C", "A");
    }
}