package com.jdbctd2.model;

import java.time.Instant;

public class Order {
    private Integer id;
    private String reference;
    private Instant creationDatetime;
    private Integer tableId;
    private java.time.LocalDateTime installationDatetime;
    private java.time.LocalDateTime departureDatetime;

    public Order() {
    }

    public Order(String reference, Integer tableId, java.time.LocalDateTime installationDatetime) {
        this.reference = reference;
        this.tableId = tableId;
        this.installationDatetime = installationDatetime;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public Instant getCreationDatetime() {
        return creationDatetime;
    }

    public void setCreationDatetime(Instant creationDatetime) {
        this.creationDatetime = creationDatetime;
    }

    public Integer getTableId() {
        return tableId;
    }

    public void setTableId(Integer tableId) {
        this.tableId = tableId;
    }

    public java.time.LocalDateTime getInstallationDatetime() {
        return installationDatetime;
    }

    public void setInstallationDatetime(java.time.LocalDateTime installationDatetime) {
        this.installationDatetime = installationDatetime;
    }

    public java.time.LocalDateTime getDepartureDatetime() {
        return departureDatetime;
    }

    public void setDepartureDatetime(java.time.LocalDateTime departureDatetime) {
        this.departureDatetime = departureDatetime;
    }

    @Override
    public String toString() {
        return "Order{" +
                "id=" + id +
                ", reference='" + reference + '\'' +
                ", creationDatetime=" + creationDatetime +
                ", tableId=" + tableId +
                ", installationDatetime=" + installationDatetime +
                ", departureDatetime=" + departureDatetime +
                '}';
    }
}
