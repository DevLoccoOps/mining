package com.minesafe.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Tag create/update payload.
 *
 * <p>{@code action} is optional:
 * <ul>
 *   <li>{@code assign}   - bind the tag to {@code assignedTo} (personnel id)</li>
 *   <li>{@code unassign} - release the tag from any personnel</li>
 *   <li>absent           - create or update the tag's {@code type}</li>
 * </ul>
 */
public record TagRequest(
        @NotBlank String mac,
        String type,
        String action,
        String assignedTo) {
}
