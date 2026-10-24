package com.prog2.catalog.synchronization.domain.ports.out;

import java.util.UUID;
import com.prog2.catalog.synchronization.domain.enums.SynchronizationPhase;
import com.prog2.catalog.synchronization.domain.model.*;

/** Persistencia de unidades incrementales y reconocimientos, con commit previo al regreso. */
public interface CatalogUpdateRepository {
    /** Reconoce la identidad exacta del evento, sin truncarla ni normalizar mayúsculas. */
    boolean isProcessed(String eventId);
    boolean isRejected(RejectedCatalogRecord rejection);
    /**
     * Confirma datos, versión, runId y recibo en una transacción nueva; no devuelve antes del commit.
     * Un error de confirmación incierta exige consultar el runId antes de repetir la escritura.
     *
     * @param runId identidad estable de esta unidad, reutilizada al reconciliar/reintentar
     * @param batch rango completo cuya base debe coincidir con la versión persistida
     * @param notification puede ser null en comprobaciones sin mensaje Kafka
     * @return estado confirmado después de aplicar la unidad, o de reconocer su aplicación previa
     * @throws com.prog2.catalog.synchronization.domain.exception.CatalogSnapshotException
     *         ante fallo temporal, terminal o confirmación incierta, con clasificación tipada
     */
    CatalogState apply(UUID runId, CatalogChangesBatch batch, CatalogNotification notification);
    /** Reconoce solo si la versión persistida ya cubre la notificación. */
    void recordCovered(CatalogNotification notification);
    /** Confirma el rechazo sin alterar datos, versión ni recibos de aplicación. */
    void recordRejection(RejectedCatalogRecord rejection);
    /** Registra una comprobación únicamente si coincide con la versión aplicada. */
    CatalogState checked(CatalogPublication publication);
    /** Registra diagnóstico y fase conservando la copia confirmada. */
    CatalogState report(SynchronizationPhase phase, String safeCode);
}
