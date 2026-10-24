package com.prog2.catalog.integration.domain.ports.in;

import com.prog2.catalog.integration.domain.model.IntegrationConfiguration;

/** Consulta Java interna de la configuración adquirida; no es un endpoint ni entrega datos a KMP. */
public interface GetIntegrationConfigurationUseCase {
    /** @return configuración de una sesión técnica utilizable, sin renovar el acceso */
    IntegrationConfiguration getConfiguration();
}
