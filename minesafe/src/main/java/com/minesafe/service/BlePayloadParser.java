package com.minesafe.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HexFormat;

/**
 * Parses raw BLE advertising payloads (hex strings) from the MikroTik KNOT
 * gateway into a {@link ParsedPayload}.
 *
 * <p><b>Outdoor tags</b> embed a manufacturer-specific frame identified by the
 * signature {@code 15ff4f09}. A 22-byte frame is extracted starting at the
 * signature offset and carries accelerometer, temperature, and battery data
 * (all little-endian). Ported from {@code app.py:126-140}.
 *
 * <p><b>Indoor tags</b> are identified by the absence of the manufacturer
 * signature. They may carry an iBeacon prefix ({@code 4C 00 02 15}) from which
 * txPower is read, and battery may be encoded in the last byte. They have no
 * accelerometer or temperature sensors. Ported from {@code app.py:142-152}.
 */
@Component
public class BlePayloadParser {

    private static final String MANUFACTURER_SIGNATURE = "15ff4f09";
    private static final byte[] IBEACON_PREFIX = {0x4c, 0x00, 0x02, 0x15};
    private static final int OUTDOOR_FRAME_LENGTH = 22;

    /**
     * Parsed BLE telemetry.
     *
     * @param outdoor     true if the outdoor manufacturer signature was detected
     * @param temperature degrees Celsius, or null when not available (indoor)
     * @param battery     0-100, or null when not available
     * @param txPower     calibrated RSSI at 1 m, or null when not available
     * @param accX        accelerometer X axis (g), 0.0 for indoor
     * @param accY        accelerometer Y axis (g), 0.0 for indoor
     * @param accZ        accelerometer Z axis (g), 0.0 for indoor
     */
    public record ParsedPayload(
            boolean outdoor,
            BigDecimal temperature,
            Integer battery,
            Integer txPower,
            double accX,
            double accY,
            double accZ) {

        public static ParsedPayload indoorDefault() {
            return new ParsedPayload(false, null, null, null, 0.0, 0.0, 0.0);
        }
    }

    public ParsedPayload parse(String hexData) {
        if (hexData == null || hexData.isBlank()) {
            return ParsedPayload.indoorDefault();
        }

        ParsedPayload outdoor = parseOutdoor(hexData);
        if (outdoor != null) {
            return outdoor;
        }

        ParsedPayload indoor = parseIndoor(hexData);
        if (indoor != null) {
            return indoor;
        }

        return ParsedPayload.indoorDefault();
    }

    private ParsedPayload parseOutdoor(String hexData) {
        String lowerHex = hexData.toLowerCase();
        int sigIndex = lowerHex.indexOf(MANUFACTURER_SIGNATURE);
        if (sigIndex == -1 || sigIndex % 2 != 0) {
            return null;
        }

        byte[] raw;
        try {
            raw = HexFormat.of().parseHex(hexData);
        } catch (IllegalArgumentException e) {
            return null;
        }

        int byteOffset = sigIndex / 2;
        if (byteOffset + OUTDOOR_FRAME_LENGTH > raw.length) {
            return null;
        }

        short tempRaw = readInt16LE(raw, byteOffset + 14);
        double temperature = tempRaw / 256.0;
        if (temperature <= -100) {
            return null;
        }

        int battery = raw[byteOffset + 21] & 0xFF;
        double accX = readInt16LE(raw, byteOffset + 8) / 1024.0;
        double accY = readInt16LE(raw, byteOffset + 10) / 1024.0;
        double accZ = readInt16LE(raw, byteOffset + 12) / 1024.0;

        return new ParsedPayload(
                true,
                BigDecimal.valueOf(temperature).setScale(2, RoundingMode.HALF_UP),
                battery,
                null,
                round3(accX), round3(accY), round3(accZ));
    }

    private ParsedPayload parseIndoor(String hexData) {
        byte[] raw;
        try {
            raw = HexFormat.of().parseHex(hexData);
        } catch (IllegalArgumentException e) {
            return null;
        }

        Integer txPower = null;
        int idx = indexOf(raw, IBEACON_PREFIX);
        if (idx != -1 && raw.length >= idx + 23) {
            txPower = (int) raw[idx + 22];
        }

        Integer battery = null;
        if (raw.length > 5) {
            int last = raw[raw.length - 1];
            if (last >= 1 && last <= 100) {
                battery = last;
            }
        }

        return new ParsedPayload(false, null, battery, txPower, 0.0, 0.0, 0.0);
    }

    private static short readInt16LE(byte[] data, int offset) {
        int lo = data[offset] & 0xFF;
        int hi = data[offset + 1];
        return (short) (lo | (hi << 8));
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static double round3(double value) {
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }
}