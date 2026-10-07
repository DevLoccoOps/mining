package com.minesafe.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Create/update payload. When {@code id} is null a new record is created;
 * otherwise the record with that id is updated. An absent/null
 * {@code assignedMac} leaves the existing assignment untouched.
 */
public record PersonnelRequest(
        String id,
        @NotBlank String name,
        String role,
        String shift,
        String assignedMac) {
}
