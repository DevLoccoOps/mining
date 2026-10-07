package com.minesafe.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minesafe.domain.AlertLog;
import com.minesafe.domain.Personnel;
import com.minesafe.domain.Tag;
import com.minesafe.domain.Telemetry;
import com.minesafe.dto.Alert;
import com.minesafe.dto.MqttReading;
import com.minesafe.service.BlePayloadParser.ParsedPayload;
import com.minesafe.service.MinerStateStore.MinerSnapshot;
import com.minesafe.service.SafetyEngine.EvaluationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates the full ingestion pipeline, invoked by {@code MqttIngestRoute}
 * on every MQTT message. Ports the legacy Python handler at
 * {@code app.py:186-245}.
 *
 * <p>Per message: parse JSON → for each MAC take the last record → smooth RSSI
 * (EMA) → look up personnel/tag from DB → parse BLE hex → compute distance →
 * evaluate safety → persist {@link AlertLog} for each real alert → update
 * {@link MinerStateStore} (gated by handover hysteresis).
 *
 * <p>Broadcasting is handled separately by {@link StateBroadcaster} on a 500 ms
 * tick, not per-message.
 *
 * <p>All exceptions are caught and logged at WARN so a malformed payload never
 * propagates back into the Camel error handler.
 */
@Component
public class TelemetryIngester {

    private static final Logger log = LoggerFactory.getLogger(TelemetryIngester.class);

    private static final double EMA_ALPHA = 0.15;
    private static final double RSSI_1_METER = -59.0;
    private static final double PATH_LOSS_EXPONENT = 2.5;

    private final ObjectMapper objectMapper;
    private final BlePayloadParser blePayloadParser;
    private final SafetyEngine safetyEngine;
    private final AlertLogService alertLogService;
    private final PersonnelService personnelService;
    private final TagService tagService;
    private final MinerStateStore minerStateStore;
    private final GatewayStateStore gatewayStateStore;
    private final TelemetryService telemetryService;

    private final ConcurrentHashMap<String, Double> rssiEma = new ConcurrentHashMap<>();

    public TelemetryIngester(ObjectMapper objectMapper,
                             BlePayloadParser blePayloadParser,
                             SafetyEngine safetyEngine,
                             AlertLogService alertLogService,
                             PersonnelService personnelService,
                             TagService tagService,
                             MinerStateStore minerStateStore,
                             GatewayStateStore gatewayStateStore,
                             TelemetryService telemetryService) {
        this.objectMapper = objectMapper;
        this.blePayloadParser = blePayloadParser;
        this.safetyEngine = safetyEngine;
        this.alertLogService = alertLogService;
        this.personnelService = personnelService;
        this.tagService = tagService;
        this.minerStateStore = minerStateStore;
        this.gatewayStateStore = gatewayStateStore;
        this.telemetryService = telemetryService;
    }

    public void ingest(String body) {
        if (body == null || body.isBlank() || body.equals("{") || body.equals("}")) {
            return;
        }

        try {
            Map<String, List<MqttReading>> message = objectMapper.readValue(body,
                    new TypeReference<>() {});

            for (Map.Entry<String, List<MqttReading>> entry : message.entrySet()) {
                String mac = entry.getKey();
                List<MqttReading> recs = entry.getValue();
                if (recs == null || recs.isEmpty()) {
                    continue;
                }
                MqttReading last = recs.get(recs.size() - 1);
                processReading(mac, last);
            }
        } catch (Exception e) {
            log.warn("Failed to process BLE payload: {}", e.getMessage());
        }
    }

    private void processReading(String mac, MqttReading reading) {
        MqttReading.MqttValues values = reading.values();
        if (values == null) {
            return;
        }
        String hexData = values.data();
        String reporter = values.reporter() != null ? values.reporter() : "UNKNOWN";
        int rawRssi = values.rssi() != null ? values.rssi() : -100;

        double rssi = smoothRssi(mac, rawRssi);

        // Track the forwarding gateway for liveness/observability (it has no heartbeat).
        gatewayStateStore.seen(reporter, rssi);

        Optional<Personnel> personnel = personnelService.findByMacOrNull(mac);
        Optional<Tag> tag = tagService.findByMacOrNull(mac);

        String shortMac = mac.length() >= 5 ? mac.substring(0, 5) : mac;
        String minerName = personnel.map(Personnel::getName)
                .orElse("Unknown (" + shortMac + "..)");
        String tagType = tag.map(Tag::getType).orElse("unregistered");
        String zone = reporter.replace("KNOT_", "");

        ParsedPayload payload = blePayloadParser.parse(hexData);
        boolean isOutdoor = payload.outdoor() || "outdoor".equals(tagType);

        Double txPower = payload.txPower() != null ? payload.txPower().doubleValue() : null;
        double distance = calculateDistance(rssi, txPower);

        EvaluationResult result = safetyEngine.evaluate(
                mac, isOutdoor, payload.temperature(),
                payload.accX(), payload.accY(), payload.accZ(), rssi);

        List<Alert> alerts = new ArrayList<>(result.alerts());

        // UNREGISTERED dedup: only add if not already in stored state (app.py:221-224)
        if ("unregistered".equals(tagType) && !minerStateStore.hasUnregisteredAlert(mac)) {
            alerts.add(new Alert("warning", "⚠️ UNREGISTERED TAG"));
        }

        BigDecimal distBd = BigDecimal.valueOf(distance).setScale(2, RoundingMode.HALF_UP);

        // Persist real alerts (excluding UNREGISTERED) — always, regardless of hysteresis
        for (Alert alert : alerts) {
            if (alert.message().contains("UNREGISTERED")) {
                continue;
            }
            AlertLog alertLog = new AlertLog(
                    mac,
                    minerName,
                    zone,
                    distBd,
                    payload.temperature(),
                    payload.battery(),
                    alert.type(),
                    alert.message());
            alertLogService.record(alertLog);
        }

        // Hysteresis: only update the dashboard snapshot if the signal warrants it (app.py:228-233)
        if (minerStateStore.shouldUpdate(mac, rssi)) {
            MinerSnapshot snapshot = new MinerSnapshot(
                    mac,
                    minerName,
                    zone,
                    reporter,
                    payload.temperature(),
                    payload.battery(),
                    distance,
                    rssi,
                    alerts,
                    isOutdoor,
                    result.moving(),
                    Instant.now().toEpochMilli() / 1000.0,
                    personnel.isPresent());
            minerStateStore.update(snapshot);
        }

        // Persist the continuous reading (independent of hysteresis/alerts). Isolated
        // in its own try/catch so a telemetry write failure can never interrupt the
        // alert persistence or state update above.
        persistTelemetry(mac, minerName, zone, reporter, distBd, payload, rssi,
                isOutdoor, result.moving(), personnel.isPresent());
    }

    private void persistTelemetry(String mac, String minerName, String zone, String reporter,
                                  BigDecimal distBd, ParsedPayload payload, double rssi,
                                  boolean isOutdoor, boolean moving, boolean registered) {
        try {
            telemetryService.record(new Telemetry(
                    mac, minerName, zone, reporter,
                    distBd, payload.temperature(), payload.battery(),
                    BigDecimal.valueOf(rssi),
                    BigDecimal.valueOf(payload.accX()),
                    BigDecimal.valueOf(payload.accY()),
                    BigDecimal.valueOf(payload.accZ()),
                    isOutdoor, moving, registered));
        } catch (Exception e) {
            log.warn("Failed to persist telemetry for {}: {}", mac, e.getMessage());
        }
    }

    private double smoothRssi(String mac, int newRssi) {
        return rssiEma.compute(mac, (k, old) -> {
            if (old == null) {
                return (double) newRssi;
            }
            double smoothed = EMA_ALPHA * newRssi + (1 - EMA_ALPHA) * old;
            return Math.round(smoothed * 10.0) / 10.0;
        });
    }

    private double calculateDistance(double rssi, Double txPower) {
        if (rssi >= 0) {
            return 0.0;
        }
        double refRssi = txPower != null ? txPower : RSSI_1_METER;
        double dist = Math.pow(10, (refRssi - rssi) / (10 * PATH_LOSS_EXPONENT));
        return Math.round(dist * 100.0) / 100.0;
    }
}