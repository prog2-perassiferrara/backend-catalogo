package com.prog2.catalog.synchronization.domain.ports.in;

import com.prog2.catalog.synchronization.domain.model.SynchronizationResult;

/** Entrada de carga completa reutilizable por el arranque y la futura recuperación. */
public interface SynchronizeCatalogUseCase {
    /**
     * Descarga y aplica una copia, con tres intentos totales y exclusión de ciclos concurrentes.
     * Reutiliza la copia validada en reintentos locales; reconcilia escrituras inciertas antes de repetir.
     *
     * @return resultado seguro del ciclo; BUSY si ya se ejecuta otro
     */
    SynchronizationResult synchronize();
}
