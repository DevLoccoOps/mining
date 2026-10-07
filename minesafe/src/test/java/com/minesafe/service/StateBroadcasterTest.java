package com.minesafe.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minesafe.dto.Alert;
import com.minesafe.repository.AlertLogRepository;
import com.minesafe.ws.LiveHub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StateBroadcasterTest {

    @Mock MinerStateStore minerStateStore;
    @Mock GatewayStateStore gatewayStateStore;
    @Mock LiveHub liveHub;
    @Mock AlertLogRepository alertLogRepository;

    private StateBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        broadcaster = new StateBroadcaster(minerStateStore, gatewayStateStore, liveHub, alertLogRepository, 50000);
    }

    @Test
    void broadcastIncludesPerMinerEntries() {
        var miner = new MinerStateStore.MinerSnapshot(
                "AABBCCDDEEFF", "Test Miner", "ZONE1", "GW-01",
                new BigDecimal("35.5"), 90, 4.2, -62.0,
                List.of(new Alert("danger", "HEAT STRESS: 35.50C")),
                true, true, 1000.0, true);
        org.mockito.Mockito.when(minerStateStore.all()).thenReturn(List.of(miner));

        broadcaster.broadcastState();

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(liveHub).broadcast(captor.capture());
        var payload = (StateBroadcaster.StateUpdate) captor.getValue();

        assertThat(payload.totalMiners()).isEqualTo(1);
        assertThat(payload.zones()).hasSize(1);
        assertThat(payload.zones().get(0).zone()).isEqualTo("ZONE1");
        assertThat(payload.alerts()).hasSize(1);
        assertThat(payload.alerts().get(0).msg()).contains("HEAT STRESS");

        assertThat(payload.miners()).hasSize(1);
        var entry = payload.miners().get(0);
        assertThat(entry.mac()).isEqualTo("AABBCCDDEEFF");
        assertThat(entry.name()).isEqualTo("Test Miner");
        assertThat(entry.battery()).isEqualTo(90);
        assertThat(entry.rssi()).isEqualTo(-62.0);
        assertThat(entry.moving()).isTrue();
        assertThat(entry.registered()).isTrue();
    }

    @Test
    void broadcastIncludesGatewayEntriesWithMinerCounts() {
        var miner = new MinerStateStore.MinerSnapshot(
                "AABBCCDDEEFF", "Test Miner", "ZONE1", "KNOT_05",
                new BigDecimal("35.5"), 90, 4.2, -62.0,
                List.of(), true, true, 1000.0, true);
        org.mockito.Mockito.when(minerStateStore.all()).thenReturn(List.of(miner));
        org.mockito.Mockito.when(gatewayStateStore.all()).thenReturn(List.of(
                new GatewayStateStore.GatewaySnapshot("KNOT_05", "05", -58.0, 999.0),
                new GatewayStateStore.GatewaySnapshot("KNOT_02", "02", -70.0, 500.0)));

        broadcaster.broadcastState();

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(liveHub).broadcast(captor.capture());
        var payload = (StateBroadcaster.StateUpdate) captor.getValue();

        assertThat(payload.gateways()).hasSize(2);
        // Sorted by last_seen descending.
        assertThat(payload.gateways().get(0).id()).isEqualTo("KNOT_05");
        assertThat(payload.gateways().get(0).zone()).isEqualTo("05");
        assertThat(payload.gateways().get(0).miners()).isEqualTo(1);
        assertThat(payload.gateways().get(1).id()).isEqualTo("KNOT_02");
        assertThat(payload.gateways().get(1).miners()).isZero();
    }

    @Test
    void payloadSerializesToExpectedSnakeCaseShape() throws Exception {
        var mapper = new ObjectMapper();
        // Match production config: snake_case to keep parity with the legacy Python API.
        mapper.setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE);

        var miner = new MinerStateStore.MinerSnapshot(
                "AABBCCDDEEFF", null, "ZONE1", "GW-01",
                new BigDecimal("35.5"), 90, 4.2, -62.0,
                List.of(new Alert("critical", "FALL DETECTED")),
                false, false, 1000.0, false);
        org.mockito.Mockito.when(minerStateStore.all()).thenReturn(List.of(miner));

        broadcaster.broadcastState();

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(liveHub).broadcast(captor.capture());
        String json = mapper.writeValueAsString(captor.getValue());

        JsonNode root = mapper.readTree(json);
        assertThat(root.get("total_miners").asInt()).isEqualTo(1);
        assertThat(root.get("zones").get(0).get("avg_temp").asText()).isEqualTo("35.5");
        assertThat(root.get("miners").get(0).get("mac").asText()).isEqualTo("AABBCCDDEEFF");
        assertThat(root.get("miners").get(0).get("last_seen").asDouble()).isEqualTo(1000.0);
        assertThat(root.get("miners").get(0).get("alerts").get(0).get("message").asText()).isEqualTo("FALL DETECTED");
        assertThat(root.get("gateways").isArray()).isTrue();
    }

    @Test
    void emptyStoreBroadcastsEmptyPayload() {
        org.mockito.Mockito.when(minerStateStore.all()).thenReturn(List.of());

        broadcaster.broadcastState();

        verify(liveHub).broadcast(any());
    }
}