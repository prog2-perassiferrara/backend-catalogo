package com.prog2.catalog.integration.infrastructure.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import com.prog2.catalog.infrastructure.config.IntegrationProperties;
import com.prog2.catalog.integration.domain.ports.out.CentralIntegrationGateway;
import com.prog2.catalog.integration.infrastructure.client.adapter.WebClientCentralIntegrationAdapter;

/**
 * Construye y conecta los componentes Spring de la integración técnica mediante inyección.
 * Utiliza las propiedades compartidas para configurar timeouts técnicos de 5 segundos.
 * La coordinación entre acceso técnico y snapshot pertenece al arranque del backend.
 */
@Configuration(proxyBeanMethods = false)
public class IntegrationConfiguration {

    @Bean
    CentralIntegrationGateway centralIntegrationGateway(IntegrationProperties properties,
            JsonMapper mapper) {
        return new WebClientCentralIntegrationAdapter(properties, mapper, Duration.ofSeconds(5));
    }

}
