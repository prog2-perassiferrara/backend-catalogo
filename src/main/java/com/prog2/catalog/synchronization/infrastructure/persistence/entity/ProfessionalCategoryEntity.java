package com.prog2.catalog.synchronization.infrastructure.persistence.entity;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Getter;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila de la réplica local. El ID y los tiempos proceden de cátedra, sin generación local. */
@Entity
@Table(name = "professional_category")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ProfessionalCategoryEntity {
    @Id
    @Column(name = "id", nullable = false)
    @Getter
    private Long id;

    @Column(name = "name", nullable = false, columnDefinition = "longtext")
    @Getter
    private String name;

    @Column(name = "description", columnDefinition = "longtext")
    @Getter
    private String description;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    @Getter
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @Getter
    private Instant updatedAt;

    public boolean getEnabled() {
        return enabled;
    }

}
