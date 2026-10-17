package com.prog2.catalog.integration.domain.ports.in;

/** Operación pública para cerrar una sesión técnica rechazada, sin exponer su repositorio. */
public interface InvalidateTechnicalAccessUseCase {
    /** Descarta token y configuración. Recuperar el acceso requiere reiniciar Catálogo. */
    void invalidate();
}
