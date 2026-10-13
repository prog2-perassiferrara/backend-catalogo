package com.prog2.catalog.integration.domain.ports.out;

import com.prog2.catalog.integration.domain.model.IntegrationConfiguration;
import com.prog2.catalog.integration.domain.model.IntegrationSession;

/**
 * Puerto de salida para login y configuración de cátedra.
 * Expone modelos de dominio para que aplicación pueda operar sin conocer HTTP ni JSON.
 */
public interface CentralIntegrationGateway {
    /**
     * Autentica la cuenta técnica previamente registrada, solicitando acceso de larga duración.
     * El caso de uso evalúa si el aprovisionamiento y la configuración permiten habilitar la sesión.
     *
     * @return sesión adquirida, todavía pendiente de evaluación
     * @throws com.prog2.catalog.integration.domain.exception.CentralIntegrationException
     *         si la comunicación, autenticación o interpretación de la respuesta falla
     */
    IntegrationSession authenticate();

    /**
     * Recupera la configuración actual utilizando el JWT adquirido, sin repetir el login.
     *
     * @param token JWT técnico usado para autenticar la consulta
     * @return configuración recibida de cátedra, que puede seguir pendiente de aprovisionamiento
     * @throws com.prog2.catalog.integration.domain.exception.CentralIntegrationException
     *         si la consulta o interpretación de la respuesta falla
     */
    IntegrationConfiguration getCurrentIntegration(String token);
}
