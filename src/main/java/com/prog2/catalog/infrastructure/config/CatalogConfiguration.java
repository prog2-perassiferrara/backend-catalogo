package com.prog2.catalog.infrastructure.config;

import java.time.Clock;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

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
}
