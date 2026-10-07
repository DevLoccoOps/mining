package com.minesafe.service;

import com.minesafe.dto.Alert;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stateful per-MAC safety evaluation, porting the legacy Python
 * {@code process_miner_safety()} at {@code app.py:154-177}.
 *
 * <p>Each MAC accumulates state across readings: the timestamp of the last
 * impact, whether fall detection is armed, how long the tag has been
 * stationary, and the last RSSI (used for indoor immobility detection).
 * State is held in memory and lost on restart.
 *
 * <p>Thread safety: each MAC's {@link MinerState} is obtained via
 * {@code computeIfAbsent} and the evaluate-and-mutate section is guarded by
 * {@code synchronized(state)} so concurrent Camel exchanges for the same MAC
 * are serialised.
 */
@Component
public class SafetyEngine {

    private static final double HARD_FALL_THRESHOLD = 1.3;
    private static final double IMMOBILITY_THRESHOLD = 0.15;
    private static final long STATIONARY_BREAK_LIMIT_SECONDS = 900;
    private static final double HEAT_STRESS_LIMIT = 32.0;

    private final ConcurrentHashMap<String, MinerState> states = new ConcurrentHashMap<>();

    /** Result of a safety evaluation, including the acceleration magnitude and motion flag. */
    public record EvaluationResult(List<Alert> alerts, double g, boolean moving) {}

    private static final class MinerState {
        double lastImpact;
        double stillSince;
        boolean fallArmed;
        double lastRssi;

        MinerState(double rssi) {
            this.lastRssi = rssi;
        }
    }

    /**
     * Evaluates safety conditions for a single reading and returns alerts,
     * acceleration magnitude, and motion flag.
     *
     * @param mac       tag MAC address
     * @param isOutdoor whether the tag is an outdoor type (hardware or registry)
     * @param tempC     temperature in Celsius, or null for indoor tags
     * @param accX      accelerometer X (g)
     * @param accY      accelerometer Y (g)
     * @param accZ      accelerometer Z (g)
     * @param rssi      smoothed RSSI (dBm)
     * @return evaluation result containing alerts, g magnitude, and moving flag
     */
    public EvaluationResult evaluate(String mac, boolean isOutdoor, BigDecimal tempC,
                                      double accX, double accY, double accZ, double rssi) {
        List<Alert> alerts = new ArrayList<>();
        double curr = Instant.now().getEpochSecond();

        MinerState st = states.computeIfAbsent(mac, k -> new MinerState(rssi));

        synchronized (st) {
            // Heat stress (outdoor only)
            if (isOutdoor && tempC != null && tempC.doubleValue() >= HEAT_STRESS_LIMIT) {
                alerts.add(new Alert("warning",
                        "⚠️ HEAT STRESS: " + tempC + "°C"));
            }

            // Acceleration magnitude
            double g = Math.sqrt(accX * accX + accY * accY + accZ * accZ);
            if (g < 0.1) {
                g = 1.0;
            }

            // Impact / hard fall (outdoor only)
            boolean impactNow = isOutdoor && g >= HARD_FALL_THRESHOLD;
            if (impactNow) {
                st.lastImpact = curr;
                st.fallArmed = true;
                alerts.add(new Alert("danger",
                        "💥 IMPACT: " + String.format("%.2f", g) + "g"));
            }

            // Immobility detection
            boolean still;
            if (isOutdoor) {
                still = Math.abs(g - 1.0) < IMMOBILITY_THRESHOLD;
            } else {
                still = Math.abs(rssi - st.lastRssi) < 2.0;
            }
            if (!isOutdoor) {
                st.lastRssi = rssi;
            }

            if (still) {
                if (st.stillSince == 0) {
                    st.stillSince = curr;
                }
                double elap = curr - st.stillSince;
                if (isOutdoor && st.fallArmed && (curr - st.lastImpact) < 30) {
                    alerts.add(new Alert("critical",
                            "🚨 CRITICAL: FALL + UNCONSCIOUS"));
                } else if (elap >= STATIONARY_BREAK_LIMIT_SECONDS) {
                    alerts.add(new Alert("warning",
                            "⏳ WELLNESS CHECK: Stationary " + (int) (elap / 60) + "m"));
                }
            } else {
                st.stillSince = 0;
                // An impact reading is motion by definition; disarming here would
                // clear the fall flag in the same evaluation that armed it and
                // make the CRITICAL FALL + UNCONSCIOUS alert unreachable (a bug
                // inherited from the legacy Python). Disarm only on later motion.
                if (!impactNow) {
                    st.fallArmed = false;
                }
            }

            return new EvaluationResult(alerts, g, !still);
        }
    }
}