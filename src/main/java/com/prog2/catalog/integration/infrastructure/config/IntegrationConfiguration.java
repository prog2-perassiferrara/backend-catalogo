package com.prog2.catalog.integration.infrastructure.config;

import com.prog2.catalog.infrastructure.config.CatalogOperationalProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import com.prog2.catalog.infrastructure.config.IntegrationProperties;
import com.prog2.catalog.integration.domain.ports.out.CentralIntegrationGateway;
import com.prog2.catalog.integration.infrastructure.client.adapter.WebClientCentralIntegrationAdapter;

/**
 * Construye y conecta los componentes Spring de la integración técnica mediante inyección.
 * Utiliza las propiedades compartidas para configurar los límites operativos técnicos.
 * La coordinación entre acceso técnico y snapshot pertenece al arranque del backend.
 */
@Configuration(proxyBeanMethods = false)
public class IntegrationConfiguration {

    @Bean
    CentralIntegrationGateway centralIntegrationGateway(IntegrationProperties properties,
            JsonMapper mapper, CatalogOperationalProperties operational) {
        var settings = operational.integration();
        return new WebClientCentralIntegrationAdapter(properties, mapper, settings.connectTimeout(),
                settings.readTimeout(), settings.requestTimeout());
    }

}
