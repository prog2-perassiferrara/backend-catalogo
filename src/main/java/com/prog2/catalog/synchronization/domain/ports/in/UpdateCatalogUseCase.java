package com.prog2.catalog.synchronization.domain.ports.in;

import com.prog2.catalog.synchronization.domain.model.*;

/** CU-02. Todas las entradas comparten presupuesto y versión persistida. */
public interface UpdateCatalogUseCase {
    /** @return recibo durable o instrucción de espera/parada; nunca un ACK por mera recepción */
    CatalogUpdateResult process(CatalogNotification notification);
    /** @return rechazo durable, sin aplicar el mensaje incompatible */
    CatalogUpdateResult reject(RejectedCatalogRecord rejection);
    /** Comprueba atraso aunque no llegue Kafka, sin fabricar una notificación. */
    SynchronizationResult check();
}
