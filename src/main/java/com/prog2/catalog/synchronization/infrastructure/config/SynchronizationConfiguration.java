package com.prog2.catalog.synchronization.infrastructure.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import com.prog2.catalog.infrastructure.config.IntegrationProperties;
import com.prog2.catalog.synchronization.application.usecases.SynchronizeCatalogUseCaseImpl;
import com.prog2.catalog.synchronization.domain.ports.in.SynchronizeCatalogUseCase;
import com.prog2.catalog.synchronization.domain.ports.out.*;
import com.prog2.catalog.synchronization.infrastructure.client.adapter.WebClientCatalogSnapshotAdapter;
import com.prog2.catalog.synchronization.infrastructure.client.mapper.CatalogSnapshotResponseMapper;

/** Composición del slice. Los límites son decisiones fijas del proyecto, no nuevos secretos. */
@Configuration(proxyBeanMethods = false)
public class SynchronizationConfiguration {
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
    SynchronizeCatalogUseCase synchronizeCatalogUseCase(CatalogSnapshotGateway gateway,
            CatalogSnapshotRepository repository, TechnicalAccessGateway access, Clock clock) {
        return new SynchronizeCatalogUseCaseImpl(gateway, repository, access, clock, Duration.ofSeconds(5));
    }
}
