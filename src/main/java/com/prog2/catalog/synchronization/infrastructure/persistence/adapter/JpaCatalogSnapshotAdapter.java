package com.prog2.catalog.synchronization.infrastructure.persistence.adapter;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

import com.prog2.catalog.synchronization.domain.enums.*;
import com.prog2.catalog.synchronization.domain.exception.CatalogSnapshotException;
import com.prog2.catalog.synchronization.domain.model.*;
import com.prog2.catalog.synchronization.domain.ports.out.CatalogSnapshotRepository;
import com.prog2.catalog.synchronization.infrastructure.persistence.service.TransactionalCatalogSnapshotWriter;

/** Adapter del Repository. Traduce fallos del cuerpo y del commit sin exponer excepciones SQL. */
@Component
public class JpaCatalogSnapshotAdapter implements CatalogSnapshotRepository {
    private final TransactionalCatalogSnapshotWriter writer;

    public JpaCatalogSnapshotAdapter(TransactionalCatalogSnapshotWriter writer) {
        this.writer = writer;
    }

    @Override
    public CatalogState getState() {
        return storage(writer::getState);
    }

    @Override
    public void beginAttempt(UUID runId, Instant now) {
        storage(() -> { writer.beginAttempt(runId, now); return null; });
    }

    @Override
    public CatalogState replace(UUID runId, CatalogSnapshot snapshot) {
        return storage(() -> writer.replace(runId, snapshot));
    }

    @Override
    public CatalogState finishAttempt(UUID runId, SynchronizationPhase phase, String diagnostic) {
        return storage(() -> writer.finishAttempt(runId, phase, diagnostic));
    }

    private static <T> T storage(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (CatalogSnapshotException failure) {
            throw failure;
        } catch (TransientDataAccessException _) {
            throw new CatalogSnapshotException(SnapshotFailureKind.TEMPORARY, "LOCAL_STORAGE_TEMPORARY");
        } catch (DataAccessResourceFailureException | TransactionException _) {
            // Perder conexión o confirmación no demuestra que el servidor haya hecho rollback.
            throw new CatalogSnapshotException(SnapshotFailureKind.UNCERTAIN, "LOCAL_STORAGE_UNCERTAIN");
        } catch (RuntimeException _) {
            throw new CatalogSnapshotException(SnapshotFailureKind.TERMINAL, "LOCAL_STORAGE_FAILURE");
        }
    }
}
