package com.prog2.catalog.synchronization.infrastructure.persistence.entity;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.Instant;
import jakarta.persistence.*;

/** Rechazo durable sin copiar el contenido externo ni afirmar aplicación de catálogo. */
@Entity
@Table(name = "catalog_rejected_record")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CatalogRejectedRecordEntity {
    @EmbeddedId
    private CatalogRejectedRecordKey id;
    @Column(name = "safe_code", nullable = false, length = 100)
    private String safeCode;
    @Column(name = "rejected_at", nullable = false)
    private Instant rejectedAt;

}
