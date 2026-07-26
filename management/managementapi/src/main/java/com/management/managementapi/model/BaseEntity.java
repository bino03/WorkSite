package com.management.managementapi.model;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * base comum para todas as entidades:
 * - id UUID gerado pelo hibernate
 * - created_at/updated_at preenchidos pela base de dados (default + trigger)
 * NOTA: estes timestamps são read-only no JPA para não chocar com o trigger.
 */
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, updatable = false, insertable = false)
    private OffsetDateTime updatedAt;

    // getters e setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; } // normalmente não mexes; útil em updates PUT

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; } // read-only (BD)

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; } // read-only (BD)
}
