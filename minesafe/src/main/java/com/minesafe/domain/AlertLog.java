package com.minesafe.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "alert_logs")
public class AlertLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String mac;

    @Column(name = "miner_name")
    private String minerName;

    private String zone;

    @Column(name = "dist_m", precision = 10, scale = 2)
    private BigDecimal distM;

    @Column(name = "temp_c", precision = 6, scale = 2)
    private BigDecimal tempC;

    private Integer battery;

    @Column(name = "alert_type")
    private String alertType;

    @Column(name = "alert_msg")
    private String alertMsg;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AlertLog() {
    }

    public AlertLog(String mac, String minerName, String zone, BigDecimal distM,
                    BigDecimal tempC, Integer battery, String alertType, String alertMsg) {
        this.mac = mac;
        this.minerName = minerName;
        this.zone = zone;
        this.distM = distM;
        this.tempC = tempC;
        this.battery = battery;
        this.alertType = alertType;
        this.alertMsg = alertMsg;
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

    public String getAlertType() {
        return alertType;
    }

    public void setAlertType(String alertType) {
        this.alertType = alertType;
    }

    public String getAlertMsg() {
        return alertMsg;
    }

    public void setAlertMsg(String alertMsg) {
        this.alertMsg = alertMsg;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
