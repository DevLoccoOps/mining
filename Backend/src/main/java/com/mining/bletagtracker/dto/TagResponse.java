package com.mining.bletagtracker.dto;

import java.time.LocalDateTime;
import java.util.List;

public record TagResponse(
        String serialNumber,
        String name,
        String description,
        String location,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<String> ibeaconUuids,
        List<String> serviceUuids,
        List<String> mfgSignatures
) {}
