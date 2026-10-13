package com.prog2.catalog.integration.infrastructure.config;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import tools.jackson.databind.json.JsonMapper;

import com.prog2.catalog.integration.domain.ports.in.InitializeIntegrationUseCase;
import com.prog2.catalog.integration.domain.ports.out.CentralIntegrationGateway;
import com.prog2.catalog.integration.domain.ports.out.IntegrationSessionRepository;
import com.prog2.catalog.integration.infrastructure.client.adapter.WebClientCentralIntegrationAdapter;

/**
 * Construye y conecta los componentes Spring de la integración técnica mediante inyección.
 * Valida propiedades antes de crear los beans habituales, proporciona un reloj UTC y configura
 * timeouts de 5 segundos, un executor de un hilo y pausas de 5 segundos entre intentos.
 * Al cerrar el contexto, Spring cierra el coordinador y solicita detener su tarea.
 */
@Configuration(proxyBeanMethods = false)
public class IntegrationConfiguration {

    /**
     * Adelanta la validación para impedir abrir conexiones con configuración local inválida.
     * El bean de propiedades se construye después para inyectarlo en los componentes.
     */
    @Bean
    static BeanFactoryPostProcessor requiredIntegrationSettings(Environment environment) {
        return beanFactory -> IntegrationProperties.from(environment);
    }

    @Bean
    IntegrationProperties integrationProperties(Environment environment) {
        return IntegrationProperties.from(environment);
    }

    @Bean
    Clock integrationClock() {
        return Clock.systemUTC();
    }

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
