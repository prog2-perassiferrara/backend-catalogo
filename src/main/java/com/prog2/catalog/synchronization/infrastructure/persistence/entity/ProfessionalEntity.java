package com.prog2.catalog.synchronization.infrastructure.persistence.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de la réplica local. El ID y los tiempos proceden de cátedra, sin generación local. */
@Entity
@Table(name = "professional")
public class ProfessionalEntity {
    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "category_id", nullable = false)
    private long categoryId;

    @Column(name = "first_name", nullable = false, columnDefinition = "longtext")
    private String firstName;

    @Column(name = "last_name", nullable = false, columnDefinition = "longtext")
    private String lastName;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProfessionalEntity() { }

    public ProfessionalEntity(Long id, long categoryId, String firstName, String lastName, boolean enabled, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.categoryId = categoryId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public long getCategoryId() {
        return categoryId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public boolean getEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
