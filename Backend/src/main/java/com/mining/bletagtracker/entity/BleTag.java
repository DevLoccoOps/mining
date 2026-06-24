package com.mining.bletagtracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "ble_tags")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BleTag {

    @Id
    @Column(nullable = false, unique = true, length = 64)
    private String serialNumber;

    @Column(length = 128)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(length = 256)
    private String location;

    @Column(columnDefinition = "TEXT[]")
    private List<String> ibeaconUuids;

    @Column(columnDefinition = "TEXT[]")
    private List<String> serviceUuids;

    @Column(columnDefinition = "TEXT[]")
    private List<String> mfgSignatures;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
