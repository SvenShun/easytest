package com.testhub.core.feature.uielement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Reusable UI locator (Sonic Elements parity). */
@Entity
@Table(name = "ui_element")
public class UiElement {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "name", length = 128, nullable = false, unique = true)
    private String name;

    /** TEXT | ID | XPATH | COORD. */
    @Column(name = "element_type", length = 16, nullable = false)
    private String elementType;

    @Column(name = "element_value", length = 2000, nullable = false)
    private String elementValue;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UiElement() {
    }

    public UiElement(String id, String name, String elementType, String elementValue, String description,
                     String createdBy, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.elementType = elementType;
        this.elementValue = elementValue;
        this.description = description;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getElementType() { return elementType; }
    public String getElementValue() { return elementValue; }
    public String getDescription() { return description; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setName(String name) { this.name = name; }
    public void setElementType(String elementType) { this.elementType = elementType; }
    public void setElementValue(String elementValue) { this.elementValue = elementValue; }
    public void setDescription(String description) { this.description = description; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
