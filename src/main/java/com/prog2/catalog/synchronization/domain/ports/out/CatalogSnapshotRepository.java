package com.prog2.catalog.synchronization.domain.ports.out;

import java.time.Instant;
import java.util.UUID;
import com.prog2.catalog.synchronization.domain.enums.SynchronizationPhase;
import com.prog2.catalog.synchronization.domain.model.CatalogSnapshot;
import com.prog2.catalog.synchronization.domain.model.CatalogState;

/** Repository del catálogo completo y su metadata; ninguna operación expone entidades JPA. */
public interface CatalogSnapshotRepository {
    /** @return metadata confirmada en una lectura nueva, necesaria para reconciliar un commit incierto */
    CatalogState getState();

    /** Registra un intento conservando la copia y versión previamente confirmadas. */
    void beginAttempt(UUID runId, Instant now);

    /**
     * Sustituye datos y versión atómicamente. Repetir el mismo runId no vuelve a escribir.
     *
     * @param runId identidad estable de esta aplicación
     * @param snapshot copia completa validada
     * @return metadata confirmada al regresar
     * @throws com.prog2.catalog.synchronization.domain.exception.CatalogSnapshotException ante retroceso o fallo local
     */
    CatalogState replace(UUID runId, CatalogSnapshot snapshot);

    /**
     * Guarda un resultado seguro sin borrar la aplicación confirmada.
     *
     * @return metadata bajo bloqueo, preservando un commit que terminó mientras se esperaba
     */
    CatalogState finishAttempt(UUID runId, SynchronizationPhase phase, String safeDiagnostic);
}
