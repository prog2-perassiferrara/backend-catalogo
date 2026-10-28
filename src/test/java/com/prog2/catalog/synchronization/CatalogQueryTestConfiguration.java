package com.prog2.catalog.synchronization;

import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import com.prog2.catalog.infrastructure.security.config.SecurityConfiguration;
import com.prog2.catalog.integration.application.usecases.*;
import com.prog2.catalog.integration.infrastructure.persistence.adapter.InMemoryIntegrationSessionAdapter;
import com.prog2.catalog.integration.infrastructure.web.controller.IntegrationTokenController;
import com.prog2.catalog.synchronization.application.service.*;
import com.prog2.catalog.synchronization.application.usecases.*;
import com.prog2.catalog.synchronization.domain.ports.in.*;
import com.prog2.catalog.synchronization.domain.ports.out.*;
import com.prog2.catalog.synchronization.infrastructure.integration.adapter.IntegrationTechnicalAccessAdapter;
import com.prog2.catalog.synchronization.infrastructure.persistence.adapter.*;
import com.prog2.catalog.synchronization.infrastructure.persistence.entity.CatalogSyncStateEntity;
import com.prog2.catalog.synchronization.infrastructure.persistence.mapper.CatalogSnapshotPersistenceMapper;
import com.prog2.catalog.synchronization.infrastructure.persistence.repository.JpaCatalogSyncStateRepository;
import com.prog2.catalog.synchronization.infrastructure.persistence.service.*;
import com.prog2.catalog.synchronization.infrastructure.web.controller.CatalogQueryController;
import com.prog2.catalog.synchronization.infrastructure.web.controller.CatalogSearchController;
import com.prog2.catalog.synchronization.infrastructure.web.exception.CatalogQueryExceptionHandler;
import com.prog2.catalog.synchronization.infrastructure.web.mapper.CatalogQueryDtoMapper;
import com.prog2.catalog.synchronization.infrastructure.web.mapper.CatalogSearchDtoMapper;
import com.prog2.catalog.security.JwtTestTokens;

/** Contexto real de consultas, SQL, seguridad y sesión en memoria, sin runners ni clientes centrales. */
@Configuration(proxyBeanMethods = false)
@Profile("catalog-query-test")
@EnableAutoConfiguration
@EntityScan(basePackageClasses = CatalogSyncStateEntity.class)
@EnableJpaRepositories(basePackageClasses = JpaCatalogSyncStateRepository.class)
@Import({TransactionalCatalogSnapshotWriter.class, JpaCatalogSnapshotAdapter.class,
        TransactionalCatalogUpdateWriter.class, JpaCatalogUpdateAdapter.class,
        TransactionalCatalogQueryReader.class, JpaCatalogQueryAdapter.class, CatalogSnapshotPersistenceMapper.class,
        InMemoryIntegrationSessionAdapter.class, GetTechnicalTokenUseCaseImpl.class,
        GetIntegrationConfigurationUseCaseImpl.class, InvalidateTechnicalAccessUseCaseImpl.class,
        IntegrationTechnicalAccessAdapter.class, IntegrationTokenController.class,
        SecurityConfiguration.class, CatalogQueryController.class, CatalogQueryDtoMapper.class,
        CatalogSearchController.class, CatalogSearchDtoMapper.class,
        CatalogQueryExceptionHandler.class, CatalogQueryTestData.class})
public class CatalogQueryTestConfiguration {
    @Bean QueryClock clock() { return new QueryClock(); }
    @Bean CatalogSynchronizationGuard guard() { return new CatalogSynchronizationGuard(); }
    @Bean CatalogStatusPolicy policy(Clock clock) { return new CatalogStatusPolicy(clock); }
    @Bean SearchProfessionalsUseCase search(CatalogQueryRepository repository, TechnicalAccessGateway access,
            CatalogSynchronizationGuard guard, CatalogStatusPolicy policy) {
        return new SearchProfessionalsUseCaseImpl(repository, access, guard, policy);
    }
    @Bean GetProfessionalCategoriesUseCase categories(CatalogQueryRepository repository, TechnicalAccessGateway access,
            CatalogSynchronizationGuard guard, CatalogStatusPolicy policy) {
        return new GetProfessionalCategoriesUseCaseImpl(repository, access, guard, policy);
    }
    @Bean GetProfessionalAgendaUseCase agendas(CatalogQueryRepository repository, TechnicalAccessGateway access,
            CatalogSynchronizationGuard guard, CatalogStatusPolicy policy) {
        return new GetProfessionalAgendaUseCaseImpl(repository, access, guard, policy);
    }
    @Bean GetCatalogStatusUseCase status(CatalogQueryRepository repository, TechnicalAccessGateway access,
            CatalogSynchronizationGuard guard, CatalogStatusPolicy policy) {
        return new GetCatalogStatusUseCaseImpl(repository, access, guard, policy);
    }

    /**
     * Reloj controlado entre hilos, sin sleeps para probar vencimientos.
     * Conserva igualdad por identidad: el instante mutable no define el valor del reloj.
     */
    @SuppressWarnings("java:S2160")
    static final class QueryClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(JwtTestTokens.NOW);
        void set(Instant instant) { now.set(instant); }
        @Override public Instant instant() { return now.get(); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
    }
}
