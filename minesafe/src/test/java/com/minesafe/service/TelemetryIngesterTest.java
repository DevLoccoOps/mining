package com.minesafe.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minesafe.domain.AlertLog;
import com.minesafe.domain.Tag;
import com.minesafe.domain.Telemetry;
import com.minesafe.dto.MqttReading;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-end ingestion tests over a mocked persistence layer, covering the
 * pipeline ported from the legacy Python handler: parse -> RSSI smoothing ->
 * registry lookup -> BLE decode -> safety evaluation -> alert/telemetry writes.
 */
@ExtendWith(MockitoExtension.class)
class TelemetryIngesterTest {

    @Mock
    private AlertLogService alertLogService;
    @Mock
    private PersonnelService personnelService;
    @Mock
    private TagService tagService;
    @Mock
    private TelemetryService telemetryService;

    private GatewayStateStore gatewayStateStore;
    private TelemetryIngester ingester;

    // 22-byte outdoor frame: signature 15ff4f09, accZ = 1.0g, temp = 35.00°C
    // (heat stress), battery = 90%.
    private static final String OUTDOOR_HEX =
            "15ff4f09" + "00000000" + "0000" + "0000" + "0004" + "0023" + "0000000000" + "5a";

    @BeforeEach
    void setUp() {
        gatewayStateStore = new GatewayStateStore();
        ingester = new TelemetryIngester(new ObjectMapper(),
                new BlePayloadParser(), new SafetyEngine(),
                alertLogService, personnelService, tagService,
                new MinerStateStore(), gatewayStateStore, telemetryService);
    }

    private String mqttBody(String hex) {
        MqttReading reading = new MqttReading(1, new MqttReading.MqttValues("KNOT_ZONE1", -70, hex));
        Map<String, List<MqttReading>> body = Map.of("AA:BB:CC:DD:EE:FF", List.of(reading));
        try {
            return new ObjectMapper().writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void outdoorHeatStressIsLoggedAndTelemetryPersisted() {
        when(personnelService.findByMacOrNull(anyString())).thenReturn(Optional.empty());
        when(tagService.findByMacOrNull(anyString()))
                .thenReturn(Optional.of(new Tag("AABBCCDDEEFF", "outdoor")));

        ingester.ingest(mqttBody(OUTDOOR_HEX));

        ArgumentCaptor<AlertLog> alertCaptor = ArgumentCaptor.forClass(AlertLog.class);
        verify(alertLogService).record(alertCaptor.capture());
        assertEquals("AA:BB:CC:DD:EE:FF", alertCaptor.getValue().getMac());
        assertEquals("warning", alertCaptor.getValue().getAlertType());
        assertTrue(alertCaptor.getValue().getAlertMsg().contains("HEAT STRESS"));
        assertEquals("ZONE1", alertCaptor.getValue().getZone());

        ArgumentCaptor<Telemetry> telemetryCaptor = ArgumentCaptor.forClass(Telemetry.class);
        verify(telemetryService).record(telemetryCaptor.capture());
        assertEquals("AA:BB:CC:DD:EE:FF", telemetryCaptor.getValue().getMac());
        assertEquals(90, telemetryCaptor.getValue().getBattery());
        assertTrue(telemetryCaptor.getValue().getOutdoor());
    }

    @Test
    void unregisteredTagWarnsButIsNeverPersistedAsAlert() {
        when(personnelService.findByMacOrNull(anyString())).thenReturn(Optional.empty());
        when(tagService.findByMacOrNull(anyString())).thenReturn(Optional.empty());

        // Indoor frame (no manufacturer signature) so no heat stress can fire;
        // UNREGISTERED is then the only alert and must stay dashboard-only.
        ingester.ingest(mqttBody("4c000215" + "0102030405060708090a0b0c0d0e0f10" + "0001" + "0002" + "c5"));

        verify(alertLogService, never()).record(any());
        ArgumentCaptor<Telemetry> telemetryCaptor = ArgumentCaptor.forClass(Telemetry.class);
        verify(telemetryService).record(telemetryCaptor.capture());
        assertEquals(Boolean.FALSE, telemetryCaptor.getValue().getRegistered());
    }

    @Test
    void malformedJsonIsSwallowed() {
        ingester.ingest("not json at all");
        ingester.ingest("{");
        ingester.ingest("");
        verify(alertLogService, never()).record(any());
        verify(telemetryService, never()).record(any(Telemetry.class));
    }

    @Test
    void emptyRecordListIsSkipped() {
        ingester.ingest("{\"AA:BB:CC:DD:EE:FF\":[]}");
        verify(telemetryService, never()).record(any(Telemetry.class));
    }

    @Test
    void ingestRecordsTheForwardingGateway() {
        when(personnelService.findByMacOrNull(anyString())).thenReturn(Optional.empty());
        when(tagService.findByMacOrNull(anyString())).thenReturn(Optional.empty());

        ingester.ingest(mqttBody("4c000215" + "0102030405060708090a0b0c0d0e0f10" + "0001" + "0002" + "c5"));

        assertThat(gatewayStateStore.activeCount()).isEqualTo(1);
        var gw = gatewayStateStore.all().iterator().next();
        assertThat(gw.id()).isEqualTo("KNOT_ZONE1");
        assertThat(gw.zone()).isEqualTo("ZONE1");
    }
}