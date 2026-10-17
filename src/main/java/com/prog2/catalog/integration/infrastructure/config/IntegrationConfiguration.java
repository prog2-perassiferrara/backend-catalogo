package com.prog2.catalog.integration.infrastructure.config;

import java.time.Duration;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import com.prog2.catalog.infrastructure.config.IntegrationProperties;
import com.prog2.catalog.integration.domain.ports.in.InitializeIntegrationUseCase;
import com.prog2.catalog.integration.domain.ports.out.CentralIntegrationGateway;
import com.prog2.catalog.integration.domain.ports.out.IntegrationSessionRepository;
import com.prog2.catalog.integration.infrastructure.client.adapter.WebClientCentralIntegrationAdapter;

/**
 * Construye y conecta los componentes Spring de la integración técnica mediante inyección.
 * Utiliza las propiedades compartidas para configurar timeouts de 5 segundos,
 * un executor de un hilo y pausas de 5 segundos entre intentos.
 * Al cerrar el contexto, Spring cierra el coordinador y solicita detener su tarea.
 */
@Configuration(proxyBeanMethods = false)
public class IntegrationConfiguration {

    @Bean
    CentralIntegrationGateway centralIntegrationGateway(IntegrationProperties properties,
            JsonMapper mapper) {
        return new WebClientCentralIntegrationAdapter(properties, mapper, Duration.ofSeconds(5));
    }

    @Bean(destroyMethod = "close")
    IntegrationStartupRunner integrationStartupRunner(InitializeIntegrationUseCase initialize,
            IntegrationSessionRepository sessions) {
        return new IntegrationStartupRunner(initialize, sessions,
                Executors.newSingleThreadExecutor(Thread.ofPlatform().name("catalog-integration").factory()),
                Duration.ofSeconds(5));
    }
}
