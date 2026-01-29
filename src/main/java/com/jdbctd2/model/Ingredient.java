package com.jdbctd2.model;

import java.math.BigDecimal;
import java.time.Instant;

public class Ingredient {
    private Integer id;
    private String name;
    private String description;
    private BigDecimal quantityInStock;
    private String unit;
    private Instant createdAt;

    public Ingredient() {
    }

    public Ingredient(String name, String description, BigDecimal quantityInStock, String unit) {
        this.name = name;
        this.description = description;
        this.quantityInStock = quantityInStock;
        this.unit = unit;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getQuantityInStock() {
        return quantityInStock;
    }

    public void setQuantityInStock(BigDecimal quantityInStock) {
        this.quantityInStock = quantityInStock;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Ingredient{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", quantityInStock=" + quantityInStock +
                ", unit='" + unit + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
