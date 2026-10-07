package com.minesafe.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tags")
public class Tag {

    public static final String TYPE_INDOOR = "indoor";
    public static final String TYPE_OUTDOOR = "outdoor";

    @Id
    @Column(length = 17)
    private String mac;

    @Column(nullable = false)
    private String type;

    @Column(name = "assigned_to", length = 10)
    private String assignedTo;

    protected Tag() {
    }

    public Tag(String mac, String type) {
        this.mac = mac;
        this.type = type;
    }

    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(String assignedTo) {
        this.assignedTo = assignedTo;
    }
}
