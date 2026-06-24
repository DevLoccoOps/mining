package com.mining.bletagtracker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateSignalRequest(
        @NotBlank(message = "Serial number is required")
        String serialNumber,

        @NotNull(message = "RSSI is required")
        @Min(value = -120, message = "RSSI must be at least -120 dBm")
        @Max(value = 127, message = "RSSI must be at most 127 dBm")
        Integer rssi,

        @NotNull(message = "Timestamp is required")
        java.time.LocalDateTime timestamp
) {}
