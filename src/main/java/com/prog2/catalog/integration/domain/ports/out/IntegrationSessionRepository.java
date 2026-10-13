package com.prog2.catalog.integration.domain.ports.out;

import com.prog2.catalog.integration.domain.model.IntegrationSession;

/**
 * Puerto de almacenamiento de la sesión técnica, siguiendo la separación del patrón Repository.
 * Permite consultar y sustituir una sesión completa sin exponer el mecanismo de almacenamiento.
 */
public interface IntegrationSessionRepository {
    /**
     * @return sesión actual no nula, incluida la sesión inicial sin acceso adquirido
     */
    IntegrationSession get();

    /**
     * Publica la sesión completa mediante un reemplazo atómico para los lectores concurrentes.
     *
     * @param session sesión inmutable que sustituye a la anterior
     * @throws NullPointerException si la sesión es nula
     */
    void replace(IntegrationSession session);
}
