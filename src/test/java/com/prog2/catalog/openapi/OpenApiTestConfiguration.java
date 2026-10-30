package com.prog2.catalog.openapi;

import java.time.Clock;
import java.time.ZoneOffset;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

import com.prog2.catalog.infrastructure.config.OpenApiConfiguration;
import com.prog2.catalog.infrastructure.security.config.SecurityConfiguration;
import com.prog2.catalog.integration.domain.ports.in.GetTechnicalTokenUseCase;
import com.prog2.catalog.integration.infrastructure.web.controller.IntegrationTokenController;
import com.prog2.catalog.security.JwtTestTokens;
import com.prog2.catalog.synchronization.domain.ports.in.*;
import com.prog2.catalog.synchronization.infrastructure.web.controller.CatalogQueryController;
import com.prog2.catalog.synchronization.infrastructure.web.controller.CatalogSearchController;
import com.prog2.catalog.synchronization.infrastructure.web.exception.CatalogQueryExceptionHandler;
import com.prog2.catalog.synchronization.infrastructure.web.mapper.CatalogQueryDtoMapper;
import com.prog2.catalog.synchronization.infrastructure.web.mapper.CatalogSearchDtoMapper;

import static org.mockito.Mockito.mock;

/** Generador, MVC y seguridad reales; los puertos permiten detectar llamadas de negocio indebidas. */
@Configuration(proxyBeanMethods = false)
@Profile("openapi-test")
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
        FlywayAutoConfiguration.class})
@Import({OpenApiConfiguration.class, SecurityConfiguration.class, IntegrationTokenController.class,
        CatalogQueryController.class, CatalogSearchController.class, CatalogQueryExceptionHandler.class,
        CatalogQueryDtoMapper.class, CatalogSearchDtoMapper.class})
class OpenApiTestConfiguration {
    @Bean Clock clock() { return Clock.fixed(JwtTestTokens.NOW, ZoneOffset.UTC); }
    @Bean SearchProfessionalsUseCase search() { return mock(SearchProfessionalsUseCase.class); }
    @Bean GetProfessionalCategoriesUseCase categories() { return mock(GetProfessionalCategoriesUseCase.class); }
    @Bean GetProfessionalAgendaUseCase agendas() { return mock(GetProfessionalAgendaUseCase.class); }
    @Bean GetCatalogStatusUseCase statuses() { return mock(GetCatalogStatusUseCase.class); }
    @Bean GetTechnicalTokenUseCase tokens() { return mock(GetTechnicalTokenUseCase.class); }
}
