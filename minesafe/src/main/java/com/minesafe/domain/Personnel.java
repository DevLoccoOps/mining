package com.minesafe.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "personnel")
public class Personnel {

    @Id
    @Column(length = 10)
    private String id;

    @Column(nullable = false)
    private String name;

    private String role;

    private String shift;

    @Column(name = "assigned_mac", length = 17)
    private String assignedMac;

    protected Personnel() {
    }

    public Personnel(String id, String name, String role, String shift) {
        this.id = id;
        this.name = name;
        this.role = role;
        this.shift = shift;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getShift() {
        return shift;
    }

    public void setShift(String shift) {
        this.shift = shift;
    }

    public String getAssignedMac() {
        return assignedMac;
    }

    public void setAssignedMac(String assignedMac) {
        this.assignedMac = assignedMac;
    }
}
