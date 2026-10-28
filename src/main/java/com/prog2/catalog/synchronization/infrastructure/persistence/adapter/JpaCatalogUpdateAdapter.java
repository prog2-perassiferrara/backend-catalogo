package com.prog2.catalog.synchronization.infrastructure.persistence.adapter;

import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import com.prog2.catalog.synchronization.domain.enums.SynchronizationPhase;
import com.prog2.catalog.synchronization.domain.exception.*;
import com.prog2.catalog.synchronization.domain.model.*;
import com.prog2.catalog.synchronization.domain.ports.out.CatalogUpdateRepository;
import com.prog2.catalog.synchronization.infrastructure.persistence.mapper.CatalogStorageFailureMapper;
import com.prog2.catalog.synchronization.infrastructure.persistence.service.TransactionalCatalogUpdateWriter;

/** Adapter de persistencia que incluye los errores de confirmación del proxy transaccional. */
@Component
public class JpaCatalogUpdateAdapter implements CatalogUpdateRepository {
    private final TransactionalCatalogUpdateWriter writer;
    public JpaCatalogUpdateAdapter(TransactionalCatalogUpdateWriter writer) { this.writer = writer; }
    @Override public CatalogState getState() { return storage(writer::getState); }
    @Override public boolean isProcessed(String id) { return storage(() -> writer.isProcessed(id)); }
    @Override public boolean isRejected(RejectedCatalogRecord value) { return storage(() -> writer.isRejected(value)); }
    @Override public CatalogState apply(UUID id, CatalogChangesBatch batch, CatalogNotification notification) {
        return storage(() -> writer.apply(id, batch, notification));
    }
    @Override public void recordCovered(CatalogNotification value) {
        storage(() -> { writer.recordCovered(value); return null; });
    }
    @Override public void recordRejection(RejectedCatalogRecord value) {
        storage(() -> { writer.recordRejection(value); return null; });
    }
    @Override public CatalogState checked(CatalogPublication value) { return storage(() -> writer.checked(value)); }
    @Override public CatalogState report(SynchronizationPhase phase, String safeCode) {
        return storage(() -> writer.report(phase, safeCode));
    }

    private static <T> T storage(Supplier<T> operation) {
        try { return operation.get(); }
        catch (CatalogSnapshotException | CatalogDiscontinuityException failure) { throw failure; }
        catch (RuntimeException failure) {
            throw CatalogStorageFailureMapper.translate(failure);
        }
    }
}
