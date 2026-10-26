package com.prog2.catalog.synchronization.domain.ports.out;

import com.prog2.catalog.synchronization.domain.model.CatalogChangesBatch;

/** Obtiene un rango completo o informa discontinuidad; nunca entrega un lote parcial. */
public interface CatalogChangesGateway {
    /**
     * Lee todos los cambios posteriores a la base y las entidades actuales afectadas.
     * La publicación debe permanecer estable durante la lectura, bajo el supuesto del productor.
     *
     * @param baseVersion versión confirmada localmente
     * @return lote completo con su publicación comprobada, incluso si no hay cambios
     * @throws com.prog2.catalog.synchronization.domain.exception.CatalogDiscontinuityException
     *         si no se puede justificar el rango con el historial disponible
     * @throws com.prog2.catalog.synchronization.domain.exception.CatalogSnapshotException
     *         ante indisponibilidad temporal o rechazo permanente del acceso
     */
    CatalogChangesBatch readSince(long baseVersion);
}
