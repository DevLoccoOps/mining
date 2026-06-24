package com.mining.bletagtracker.dto;

import java.util.List;

/** Tag config format for the BLE scanner microservice. */
public record ScannerTagResponse(
        String serialNumber,
        String name,
        List<String> ibeaconUuids,
        List<String> serviceUuids,
        List<String> mfgSignatures,
        int rssiThreshold
) {}
