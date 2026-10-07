package com.minesafe.service;

import com.minesafe.dto.Alert;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafetyEngineTest {

    private final SafetyEngine engine = new SafetyEngine();

    @Test
    void impactAboveThresholdRaisesDangerAlert() {
        var result = engine.evaluate("AA:BB:CC:DD:EE:FF", true, null, 1.5, 0.0, 0.0, -70);

        assertTrue(result.alerts().stream().anyMatch(a -> a.type().equals("danger")
                && a.message().contains("IMPACT")));
    }

    @Test
    void fallThenImmobilityEscalatesToCritical() {
        String mac = "AA:BB:CC:DD:EE:01";
        // 1. Impact arms fall detection.
        engine.evaluate(mac, true, null, 1.5, 0.0, 0.0, -70);
        // 2. Subsequent still reading (1g) within 30s of impact -> critical.
        var result = engine.evaluate(mac, true, null, 0.0, 0.0, 1.0, -70);

        assertTrue(result.alerts().stream().anyMatch(a -> a.type().equals("critical")
                && a.message().contains("FALL + UNCONSCIOUS")));
        assertFalse(result.moving());
    }

    @Test
    void motionAfterImpactDisarmsFallDetection() {
        String mac = "AA:BB:CC:DD:EE:07";
        // 1. Impact arms fall detection (reading itself counts as motion).
        engine.evaluate(mac, true, null, 1.5, 0.0, 0.0, -70);
        // 2. Later motion that is not another impact must disarm it...
        engine.evaluate(mac, true, null, 0.5, 0.0, 0.0, -70);
        // 3. ...so the following stillness raises no critical alert.
        var result = engine.evaluate(mac, true, null, 0.0, 0.0, 1.0, -70);

        assertTrue(result.alerts().stream().noneMatch(a -> a.type().equals("critical")));
    }

    @Test
    void heatStressWarnsForOutdoorTag() {
        var result = engine.evaluate("AA:BB:CC:DD:EE:02", true, BigDecimal.valueOf(35), 0.0, 0.0, 1.0, -70);

        assertTrue(result.alerts().stream().anyMatch(a -> a.type().equals("warning")
                && a.message().contains("HEAT STRESS")));
    }

    @Test
    void heatStressIgnoredForIndoorTag() {
        var result = engine.evaluate("AA:BB:CC:DD:EE:03", false, BigDecimal.valueOf(35), 0.0, 0.0, 1.0, -70);

        assertTrue(result.alerts().isEmpty());
    }

    @Test
    void indoorStillnessDetectedViaFlatRssi() {
        String mac = "AA:BB:CC:DD:EE:04";
        var first = engine.evaluate(mac, false, null, 0.0, 0.0, 0.0, -70);
        var second = engine.evaluate(mac, false, null, 0.0, 0.0, 0.0, -70);

        assertFalse(first.moving());
        assertFalse(second.moving());
        // A still indoor tag only starts alerting after 15 minutes; a fresh
        // still period must not raise a wellness check immediately.
        assertTrue(second.alerts().stream().noneMatch(a -> a.message().contains("WELLNESS")));
    }

    @Test
    void motionResetsStationaryTimer() {
        String mac = "AA:BB:CC:DD:EE:05";
        // Still, then a sharp RSSI change on an indoor tag counts as motion.
        engine.evaluate(mac, false, null, 0.0, 0.0, 0.0, -70);
        var moved = engine.evaluate(mac, false, null, 0.0, 0.0, 0.0, -50);

        assertTrue(moved.moving());
        assertTrue(moved.alerts().isEmpty());
    }

    @Test
    void multipleAlertsAccumulate() {
        String mac = "AA:BB:CC:DD:EE:06";
        // Impact + heat stress in the same reading.
        var result = engine.evaluate(mac, true, BigDecimal.valueOf(40), 1.4, 0.0, 0.0, -70);
        List<String> types = result.alerts().stream().map(Alert::type).toList();

        assertTrue(types.contains("danger"));
        assertTrue(types.contains("warning"));
    }
}