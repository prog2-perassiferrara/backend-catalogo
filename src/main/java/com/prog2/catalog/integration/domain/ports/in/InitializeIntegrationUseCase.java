package com.prog2.catalog.integration.domain.ports.in;

import com.prog2.catalog.integration.domain.model.InitializationResult;

/** Puerto de entrada para adquirir acceso técnico mediante un intento de integración. */
public interface InitializeIntegrationUseCase {
    /**
     * Obtiene y evalúa la sesión, conservando el JWT mientras el aprovisionamiento está pendiente.
     * Una sesión fallida o vencida requiere reiniciar el backend para comenzar otro ciclo.
     * Las pausas y el límite de intentos corresponden al coordinador de arranque.
     *
     * @return resultado disponible, recuperable o terminal, con diagnóstico sin secretos
     */
    InitializationResult initialize();
}
