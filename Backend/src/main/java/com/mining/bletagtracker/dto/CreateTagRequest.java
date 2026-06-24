package com.mining.bletagtracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateTagRequest(
        @NotBlank(message = "Serial number is required")
        @Size(max = 64, message = "Serial number must be at most 64 characters")
        String serialNumber,

        @Size(max = 128, message = "Name must be at most 128 characters")
        String name,

        @Size(max = 500, message = "Description must be at most 500 characters")
        String description,

        @Size(max = 256, message = "Location must be at most 256 characters")
        String location,

        List<String> ibeaconUuids,

        List<String> serviceUuids,

        List<String> mfgSignatures
) {}
