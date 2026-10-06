package com.prog2.catalog.infrastructure.config;

import java.util.List;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
public class DatabaseConfiguration {

    @Bean
    static BeanFactoryPostProcessor requiredDatabaseSettings(Environment environment) {
        return beanFactory -> {
            // Validar antes de abrir conexiones; el diagnóstico identifica la variable, no su valor.
            for (String setting : List.of("DB_NAME", "DB_USER", "DB_PASSWORD")) {
                if (!StringUtils.hasText(environment.getProperty(setting))) {
                    throw new BeanCreationException("databaseConfiguration",
                            "Required database setting is missing or blank: " + setting);
                }
            }
        };
    }
}
