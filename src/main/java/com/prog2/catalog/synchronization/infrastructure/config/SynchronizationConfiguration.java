package com.prog2.catalog.synchronization.infrastructure.config;

import java.time.Clock;
import java.time.Duration;
import com.prog2.catalog.synchronization.application.service.CatalogSynchronizationGuard;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import com.prog2.catalog.infrastructure.config.IntegrationProperties;
import com.prog2.catalog.synchronization.application.usecases.SynchronizeCatalogUseCaseImpl;
import com.prog2.catalog.synchronization.application.usecases.RecoverCatalogUseCaseImpl;
import com.prog2.catalog.synchronization.application.usecases.UpdateCatalogUseCaseImpl;
import com.prog2.catalog.synchronization.application.usecases.GetProfessionalAgendaUseCaseImpl;
import com.prog2.catalog.synchronization.application.usecases.GetCatalogStatusUseCaseImpl;
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

/** Composición del slice. Los límites son decisiones fijas del proyecto, no nuevos secretos. */
@Configuration(proxyBeanMethods = false)
public class SynchronizationConfiguration {
    @Bean(destroyMethod = "close")
    CatalogRemoteClients catalogRemoteClients(JsonMapper mapper, Clock clock) {
        return new CatalogRemoteClients(mapper, clock);
    }

    @Bean(destroyMethod = "close")
    RedisCatalogChangesAdapter catalogChangesGateway(CatalogRemoteClients clients, JsonMapper json,
            CatalogSnapshotResponseMapper entities) {
        return new RedisCatalogChangesAdapter(clients::redis, clients::namespace, json, entities,
                Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon(true).name("catalog-redis-reader").factory()),
                Duration.ofSeconds(20), 8 * 1024 * 1024);
    }

    @Bean
    RecoverCatalogUseCase recoverCatalogUseCase(SynchronizeCatalogUseCase snapshots, CatalogUpdateRepository repository,
            TechnicalAccessGateway access, Clock clock, CatalogSynchronizationGuard catalogSynchronizationLock) {
        return new RecoverCatalogUseCaseImpl(snapshots, repository, access, clock, catalogSynchronizationLock);
    }

    @Bean
    UpdateCatalogUseCase updateCatalogUseCase(CatalogChangesGateway changes, CatalogUpdateRepository repository,
            RecoverCatalogUseCase recovery, TechnicalAccessGateway access,
            Clock clock, CatalogSynchronizationGuard catalogSynchronizationLock) {
        return new UpdateCatalogUseCaseImpl(changes, repository, recovery, access, clock,
                Duration.ofSeconds(5), catalogSynchronizationLock);
    }

    @Bean(destroyMethod = "close")
    CatalogSynchronizationMonitor catalogSynchronizationMonitor(CatalogRemoteClients clients,
            CatalogConnectionSettingsGateway settings, CatalogSnapshotRepository snapshots, UpdateCatalogUseCase updates,
            ScheduledExecutorService catalogCheckScheduler) {
        return new CatalogSynchronizationMonitor(clients, settings, snapshots, updates, catalogCheckScheduler);
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
            CatalogSnapshotResponseMapper snapshotMapper) {
        return new WebClientCatalogSnapshotAdapter(properties.baseUrl(), mapper, snapshotMapper,
                Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(20), 8 * 1024 * 1024);
    }

    @Bean
    CatalogSynchronizationGuard catalogSynchronizationLock() {
        return new CatalogSynchronizationGuard();
    }

    @Bean
    CatalogStatusPolicy catalogStatusPolicy(Clock clock) { return new CatalogStatusPolicy(clock); }

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
            CatalogSynchronizationGuard catalogSynchronizationLock) {
        return new SynchronizeCatalogUseCaseImpl(gateway, repository, access, clock, Duration.ofSeconds(5),
                catalogSynchronizationLock);
    }
}
