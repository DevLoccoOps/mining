package com.minesafe.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A single consumed BLE telemetry reading, persisted for every MAC on each
 * MQTT message (unlike {@link AlertLog}, which is only written when a safety
 * alert fires). Backs durable history for position, environment, and battery.
 * Capped by a time-based retention job.
 */
@Entity
@Table(name = "telemetry")
public class Telemetry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String mac;

    @Column(name = "miner_name")
    private String minerName;

    private String zone;

    private String reporter;

    @Column(name = "dist_m", precision = 10, scale = 2)
    private BigDecimal distM;

    @Column(name = "temp_c", precision = 6, scale = 2)
    private BigDecimal tempC;

    private Integer battery;

    @Column(precision = 6, scale = 2)
    private BigDecimal rssi;

    @Column(name = "acc_x", precision = 6, scale = 3)
    private BigDecimal accX;

    @Column(name = "acc_y", precision = 6, scale = 3)
    private BigDecimal accY;

    @Column(name = "acc_z", precision = 6, scale = 3)
    private BigDecimal accZ;

    private Boolean outdoor;

    private Boolean moving;

    private Boolean registered;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Telemetry() {
    }

    public Telemetry(String mac, String minerName, String zone, String reporter,
                     BigDecimal distM, BigDecimal tempC, Integer battery, BigDecimal rssi,
                     BigDecimal accX, BigDecimal accY, BigDecimal accZ,
                     boolean outdoor, boolean moving, boolean registered) {
        this.mac = mac;
        this.minerName = minerName;
        this.zone = zone;
        this.reporter = reporter;
        this.distM = distM;
        this.tempC = tempC;
        this.battery = battery;
        this.rssi = rssi;
        this.accX = accX;
        this.accY = accY;
        this.accZ = accZ;
        this.outdoor = outdoor;
        this.moving = moving;
        this.registered = registered;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    public String getMinerName() {
        return minerName;
    }

    public void setMinerName(String minerName) {
        this.minerName = minerName;
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public String getReporter() {
        return reporter;
    }

    public void setReporter(String reporter) {
        this.reporter = reporter;
    }

    public BigDecimal getDistM() {
        return distM;
    }

    public void setDistM(BigDecimal distM) {
        this.distM = distM;
    }

    public BigDecimal getTempC() {
        return tempC;
    }

    public void setTempC(BigDecimal tempC) {
        this.tempC = tempC;
    }

    public Integer getBattery() {
        return battery;
    }

    public void setBattery(Integer battery) {
        this.battery = battery;
    }

    public BigDecimal getRssi() {
        return rssi;
    }

    public void setRssi(BigDecimal rssi) {
        this.rssi = rssi;
    }

    public BigDecimal getAccX() {
        return accX;
    }

    public void setAccX(BigDecimal accX) {
        this.accX = accX;
    }

    public BigDecimal getAccY() {
        return accY;
    }

    public void setAccY(BigDecimal accY) {
        this.accY = accY;
    }

    public BigDecimal getAccZ() {
        return accZ;
    }

    public void setAccZ(BigDecimal accZ) {
        this.accZ = accZ;
    }

    public Boolean getOutdoor() {
        return outdoor;
    }

    public void setOutdoor(Boolean outdoor) {
        this.outdoor = outdoor;
    }

    public Boolean getMoving() {
        return moving;
    }

    public void setMoving(Boolean moving) {
        this.moving = moving;
    }

    public Boolean getRegistered() {
        return registered;
    }

    public void setRegistered(Boolean registered) {
        this.registered = registered;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
