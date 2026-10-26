package com.prog2.catalog.synchronization.infrastructure.persistence.entity;

import java.time.Instant;
import jakarta.persistence.*;

/** Recibo confirmado junto al efecto. La huella indexa, el ID completo verifica identidad exacta. */
@Entity
@Table(name = "catalog_processed_event")
public class CatalogProcessedEventEntity {
    @Id
    @Column(name = "event_hash", columnDefinition = "binary(32)")
    private byte[] eventHash;
    @Column(name = "event_id", nullable = false, columnDefinition = "longtext")
    private String eventId;
    @Column(name = "new_version", nullable = false)
    private long newVersion;
    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected CatalogProcessedEventEntity() { }
    public CatalogProcessedEventEntity(byte[] hash, String eventId, long version, Instant now) {
        this.eventHash = hash.clone();
        this.eventId = eventId;
        this.newVersion = version;
        this.processedAt = now;
    }
    public String getEventId() { return eventId; }
}
