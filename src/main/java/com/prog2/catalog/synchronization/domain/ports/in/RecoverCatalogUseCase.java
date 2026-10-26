package com.prog2.catalog.synchronization.domain.ports.in;

import java.util.Optional;
import com.prog2.catalog.synchronization.domain.enums.CatalogRecoveryReason;
import com.prog2.catalog.synchronization.domain.model.SynchronizationResult;

/** CU-03. Coordina ciclos de CU-01 sin renovar sesión ni reiniciar límites por mensaje. */
public interface RecoverCatalogUseCase {
    /**
     * Inicia o continúa la recuperación pendiente, respetando su presupuesto compartido.
     * @param reason causa observada; una nueva notificación no reemplaza el ciclo pendiente
     * @return resultado de un ciclo habilitado o espera/parada, sin dormir durante la pausa larga
     */
    SynchronizationResult recover(CatalogRecoveryReason reason);
    /** @return vacío si no hay recuperación pendiente; en otro caso ejecuta solo un ciclo habilitado */
    Optional<SynchronizationResult> resume();
}
