package com.prog2.catalog.synchronization.infrastructure.persistence.entity;

import java.io.Serializable;
import java.util.Objects;
import jakarta.persistence.*;

/** Identidad estable de un registro Kafka, incluso cuando su cuerpo no contiene eventId. */
@Embeddable
public class CatalogRejectedRecordKey implements Serializable {
    private static final long serialVersionUID = 1L;
    @Column(name = "topic", length = 249)
    private String topic;
    @Column(name = "partition_id")
    private int partition;
    @Column(name = "record_offset")
    private long offset;

    protected CatalogRejectedRecordKey() { }
    public CatalogRejectedRecordKey(String topic, int partition, long offset) {
        this.topic = topic;
        this.partition = partition;
        this.offset = offset;
    }
    @Override
    public boolean equals(Object other) {
        return other instanceof CatalogRejectedRecordKey key && partition == key.partition
                && offset == key.offset && Objects.equals(topic, key.topic);
    }
    @Override
    public int hashCode() { return Objects.hash(topic, partition, offset); }
}
