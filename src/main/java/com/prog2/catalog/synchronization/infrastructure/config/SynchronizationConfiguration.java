package com.prog2.catalog.synchronization.infrastructure.config;

import java.time.Clock;
import com.prog2.catalog.synchronization.application.service.CatalogSynchronizationGuard;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import com.prog2.catalog.infrastructure.config.IntegrationProperties;
import com.prog2.catalog.infrastructure.config.CatalogOperationalProperties;
import com.prog2.catalog.synchronization.application.usecases.SynchronizeCatalogUseCaseImpl;
import com.prog2.catalog.synchronization.application.usecases.RecoverCatalogUseCaseImpl;
import com.prog2.catalog.synchronization.application.usecases.UpdateCatalogUseCaseImpl;
import com.prog2.catalog.synchronization.application.usecases.GetProfessionalAgendaUseCaseImpl;
import com.prog2.catalog.synchronization.application.usecases.GetCatalogStatusUseCaseImpl;
import com.prog2.catalog.synchronization.application.usecases.SearchProfessionalsUseCaseImpl;
import com.prog2.catalog.synchronization.application.usecases.GetProfessionalCategoriesUseCaseImpl;
import com.prog2.catalog.synchronization.domain.ports.in.SearchProfessionalsUseCase;
import com.prog2.catalog.synchronization.domain.ports.in.GetProfessionalCategoriesUseCase;
import com.prog2.catalog.synchronization.application.service.CatalogStatusPolicy;
import com.prog2.catalog.synchronization.domain.ports.in.GetProfessionalAgendaUseCase;
import com.prog2.catalog.synchronization.domain.ports.in.GetCatalogStatusUseCase;
import com.prog2.catalog.synchronization.domain.ports.in.RecoverCatalogUseCase;
import com.prog2.catalog.synchronization.domain.ports.in.UpdateCatalogUseCase;
import com.prog2.catalog.infrastructure.config.CatalogRemoteClients;
import com.prog2.catalog.infrastructure.startup.CatalogSynchronizationMonitor;
import com.prog2.catalog.synchronization.infrastructure.redis.adapter.RedisCatalogChangesAdapter;
import com.prog2.catalog.synchronization.domain.ports.in.SynchronizeCatalogUseCase;
import com.prog2.catalog.synchronization.domain.ports.out.*;
import com.prog2.catalog.synchronization.infrastructure.client.adapter.WebClientCatalogSnapshotAdapter;
import com.prog2.catalog.synchronization.infrastructure.client.mapper.CatalogSnapshotResponseMapper;

/** Composición del slice con límites operativos locales validados al arrancar. */
@Configuration(proxyBeanMethods = false)
public class SynchronizationConfiguration {
    @Bean(destroyMethod = "close")
    CatalogRemoteClients catalogRemoteClients(JsonMapper mapper, Clock clock, CatalogOperationalProperties operational) {
        return new CatalogRemoteClients(mapper, clock, operational.redis().connectTimeout(),
                operational.redis().commandTimeout(), operational.kafkaTimeout());
    }

    @Bean(destroyMethod = "close")
    RedisCatalogChangesAdapter catalogChangesGateway(CatalogRemoteClients clients, JsonMapper json,
            CatalogSnapshotResponseMapper entities, CatalogOperationalProperties operational) {
        return new RedisCatalogChangesAdapter(clients::redis, clients::namespace, json, entities,
                Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon(true).name("catalog-redis-reader").factory()),
                operational.redis().readTimeout(), 8 * 1024 * 1024);
    }

    @Bean
    RecoverCatalogUseCase recoverCatalogUseCase(SynchronizeCatalogUseCase snapshots, CatalogUpdateRepository repository,
            TechnicalAccessGateway access, Clock clock, CatalogSynchronizationGuard catalogSynchronizationLock,
            CatalogOperationalProperties operational) {
        var settings = operational.recovery();
        return new RecoverCatalogUseCaseImpl(snapshots, repository, access, clock, settings.maxCycles(),
                settings.cycleDelay(), settings.blockDelay(), catalogSynchronizationLock);
    }

    @Bean
    UpdateCatalogUseCase updateCatalogUseCase(CatalogChangesGateway changes, CatalogUpdateRepository repository,
            RecoverCatalogUseCase recovery, TechnicalAccessGateway access,
            Clock clock, CatalogSynchronizationGuard catalogSynchronizationLock, CatalogOperationalProperties operational) {
        var settings = operational.incremental();
        return new UpdateCatalogUseCaseImpl(changes, repository, recovery, access, clock,
                settings.maxAttempts(), settings.retryDelay(), settings.retryCooldown(), catalogSynchronizationLock);
    }

    @Bean(destroyMethod = "close")
    CatalogSynchronizationMonitor catalogSynchronizationMonitor(CatalogRemoteClients clients,
            CatalogConnectionSettingsGateway settings, CatalogSnapshotRepository snapshots, UpdateCatalogUseCase updates,
            ScheduledExecutorService catalogCheckScheduler, CatalogOperationalProperties operational) {
        return new CatalogSynchronizationMonitor(clients, settings, snapshots, updates, catalogCheckScheduler,
                operational.monitorInterval());
    }

    /** Spring garantiza el cierre del recurso también si otro bean impide completar el arranque. */
    @Bean(destroyMethod = "shutdownNow")
    ScheduledExecutorService catalogCheckScheduler() {
        return Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("catalog-checks").factory());
    }
    @Bean
    CatalogSnapshotResponseMapper catalogSnapshotResponseMapper(JsonMapper mapper) {
        return new CatalogSnapshotResponseMapper(mapper);
    }

    @Bean
    CatalogSnapshotGateway catalogSnapshotGateway(IntegrationProperties properties, JsonMapper mapper,
            CatalogSnapshotResponseMapper snapshotMapper, CatalogOperationalProperties operational) {
        var settings = operational.snapshot();
        return new WebClientCatalogSnapshotAdapter(properties.baseUrl(), mapper, snapshotMapper,
                settings.connectTimeout(), settings.readTimeout(), settings.requestTimeout(), 8 * 1024 * 1024);
    }

    @Bean
    CatalogSynchronizationGuard catalogSynchronizationLock() {
        return new CatalogSynchronizationGuard();
    }

    @Bean
    CatalogStatusPolicy catalogStatusPolicy(Clock clock) { return new CatalogStatusPolicy(clock); }

    @Bean
    SearchProfessionalsUseCase searchProfessionalsUseCase(CatalogQueryRepository repository,
            TechnicalAccessGateway access, CatalogSynchronizationGuard guard, CatalogStatusPolicy policy) {
        return new SearchProfessionalsUseCaseImpl(repository, access, guard, policy);
    }

    @Bean
    GetProfessionalCategoriesUseCase getProfessionalCategoriesUseCase(CatalogQueryRepository repository,
            TechnicalAccessGateway access, CatalogSynchronizationGuard guard, CatalogStatusPolicy policy) {
        return new GetProfessionalCategoriesUseCaseImpl(repository, access, guard, policy);
    }

    @Bean
    GetProfessionalAgendaUseCase getProfessionalAgendaUseCase(CatalogQueryRepository repository,
            TechnicalAccessGateway access, CatalogSynchronizationGuard guard, CatalogStatusPolicy policy) {
        return new GetProfessionalAgendaUseCaseImpl(repository, access, guard, policy);
    }

    @Bean
    GetCatalogStatusUseCase getCatalogStatusUseCase(CatalogQueryRepository repository,
            TechnicalAccessGateway access, CatalogSynchronizationGuard guard, CatalogStatusPolicy policy) {
        return new GetCatalogStatusUseCaseImpl(repository, access, guard, policy);
    }

    @Bean
    SynchronizeCatalogUseCase synchronizeCatalogUseCase(CatalogSnapshotGateway gateway,
            CatalogSnapshotRepository repository, TechnicalAccessGateway access, Clock clock,
            CatalogSynchronizationGuard catalogSynchronizationLock, CatalogOperationalProperties operational) {
        var settings = operational.snapshot();
        return new SynchronizeCatalogUseCaseImpl(gateway, repository, access, clock, settings.maxAttempts(), settings.retryDelay(),
                catalogSynchronizationLock);
    }
}
