package com.minesafe.service;

import com.minesafe.service.BlePayloadParser.ParsedPayload;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlePayloadParserTest {

    private final BlePayloadParser parser = new BlePayloadParser();

    @Test
    void parsesOutdoorFrameWithTemperatureBatteryAndAccel() {
        // 22-byte outdoor frame: signature 15ff4f09, accX/accY = 0, accZ = 1.0g
        // (1024/1024), temp raw 0x2300 = 35.00°C, battery 0x5A = 90%.
        String hex = "15ff4f09" + "00000000"   // signature + reserved bytes 4..7
                + "0000"                        // accX
                + "0000"                        // accY
                + "0004"                        // accZ (LE 0x0400 = 1024)
                + "0023"                        // temp (LE 0x2300 = 8960 -> 35.00°C)
                + "0000000000"                  // bytes 16..20
                + "5a";                         // battery
        ParsedPayload payload = parser.parse(hex);

        assertTrue(payload.outdoor());
        assertEquals(0, BigDecimal.valueOf(35).compareTo(payload.temperature()));
        assertEquals(90, payload.battery());
        assertNull(payload.txPower());
        assertEquals(0.0, payload.accX());
        assertEquals(0.0, payload.accY());
        assertEquals(1.0, payload.accZ());
    }

    @Test
    void rejectsOutdoorFrameWithImpossibleTemperature() {
        // temp raw 0x8000 = -128°C falls under the -100 sentinel: treated as corrupt.
        String hex = "15ff4f09" + "00000000" + "0000" + "0000" + "0004" + "0080" + "0000000000" + "5a";
        ParsedPayload payload = parser.parse(hex);

        assertFalse(payload.outdoor());
        assertNull(payload.temperature());
    }

    @Test
    void parsesIndoorIBeaconTxPowerAndBattery() {
        // iBeacon layout: prefix 4C 00 02 15 at byte 0, 16-byte UUID (4..19),
        // major (20..21); the parser reads txPower from byte idx+22 (0xC5 = -59
        // as a signed byte), and the trailing byte as battery (0x50 = 80).
        String hex = "4c000215"
                + "0102030405060708090a0b0c0d0e0f10"   // UUID, bytes 4..19
                + "0001"                                // major, bytes 20..21
                + "c5"                                  // txPower, byte 22
                + "50";                                 // battery, last byte
        ParsedPayload payload = parser.parse(hex);

        assertFalse(payload.outdoor());
        assertEquals(-59, payload.txPower());
        assertEquals(80, payload.battery());
        assertNull(payload.temperature());
        assertEquals(0.0, payload.accX());
    }

    @Test
    void blankOrNullYieldsIndoorDefault() {
        ParsedPayload payload = parser.parse(null);
        assertFalse(payload.outdoor());
        assertNull(payload.temperature());
        assertNull(payload.battery());

        assertEquals(payload, parser.parse(""));
    }

    @Test
    void invalidHexYieldsIndoorDefault() {
        ParsedPayload payload = parser.parse("zzzz-not-hex");
        assertFalse(payload.outdoor());
        assertNull(payload.battery());
    }
}