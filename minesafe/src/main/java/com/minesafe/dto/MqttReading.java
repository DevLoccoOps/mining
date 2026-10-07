package com.minesafe.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Single BLE advertisement record as published by the MikroTik KNOT gateway.
 * The MQTT topic {@code knot/ble/tags} carries a top-level JSON object keyed by
 * MAC address, where each value is a list of these records. Only the last
 * record in each list is consumed (matching the legacy Python app at
 * {@code app.py:197}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MqttReading(long ts, MqttValues values) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MqttValues(String reporter, Integer rssi, String data) {}
}