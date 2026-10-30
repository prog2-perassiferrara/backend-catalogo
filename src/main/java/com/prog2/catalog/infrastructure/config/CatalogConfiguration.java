package com.prog2.catalog.infrastructure.config;

import java.time.Clock;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.prog2.catalog.infrastructure.startup.CatalogStartupRunner;
import com.prog2.catalog.infrastructure.startup.CatalogSynchronizationMonitor;
import com.prog2.catalog.integration.domain.ports.in.InitializeIntegrationUseCase;
import com.prog2.catalog.integration.domain.ports.in.InvalidateTechnicalAccessUseCase;
import com.prog2.catalog.synchronization.domain.ports.in.SynchronizeCatalogUseCase;

/**
 * Proporciona las propiedades locales de cátedra y el reloj UTC compartidos por el backend.
 * La sesión técnica y el cableado específico de cada slice permanecen en su infraestructura.
 */
@Configuration(proxyBeanMethods = false)
public class CatalogConfiguration {

    /**
     * Adelanta la validación para impedir abrir conexiones con configuración local inválida.
     * El bean de propiedades se construye después para inyectarlo en los componentes.
     */
    @Bean
    static BeanFactoryPostProcessor requiredIntegrationSettings(Environment environment) {
        return beanFactory -> {
            IntegrationProperties.from(environment);
            CatalogOperationalProperties.from(environment);
        };
    }

    @Bean
    IntegrationProperties integrationProperties(Environment environment) {
        return IntegrationProperties.from(environment);
    }

    @Bean
    CatalogOperationalProperties catalogOperationalProperties(Environment environment) {
        return CatalogOperationalProperties.from(environment);
    }

    @Bean
    Clock integrationClock() {
        return Clock.systemUTC();
    }

    @Bean(destroyMethod = "close")
    CatalogStartupRunner catalogStartupRunner(InitializeIntegrationUseCase initialize,
            InvalidateTechnicalAccessUseCase invalidation, SynchronizeCatalogUseCase synchronization,
            CatalogSynchronizationMonitor monitoring, CatalogOperationalProperties operational) {
        return new CatalogStartupRunner(initialize, invalidation, synchronization,
                Executors.newSingleThreadExecutor(Thread.ofPlatform().name("catalog-integration").factory()),
                operational.integration().maxAttempts(), operational.integration().retryDelay(), monitoring::start);
    }
}
