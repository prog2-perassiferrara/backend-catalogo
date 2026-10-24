package com.prog2.catalog.synchronization;

import java.time.Clock;
import java.time.ZoneOffset;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import com.prog2.catalog.synchronization.infrastructure.persistence.adapter.*;
import com.prog2.catalog.synchronization.infrastructure.persistence.entity.CatalogSyncStateEntity;
import com.prog2.catalog.synchronization.infrastructure.persistence.mapper.CatalogSnapshotPersistenceMapper;
import com.prog2.catalog.synchronization.infrastructure.persistence.repository.JpaCatalogSyncStateRepository;
import com.prog2.catalog.synchronization.infrastructure.persistence.service.*;

/** Contexto SQL real aislado; no crea cuenta técnica, clientes remotos ni runner de producción. */
@Configuration(proxyBeanMethods=false)
@Profile("update-persistence-test")
@EnableAutoConfiguration
@EntityScan(basePackageClasses=CatalogSyncStateEntity.class)
@EnableJpaRepositories(basePackageClasses=JpaCatalogSyncStateRepository.class)
@Import({TransactionalCatalogSnapshotWriter.class, JpaCatalogSnapshotAdapter.class,
        TransactionalCatalogUpdateWriter.class, JpaCatalogUpdateAdapter.class, CatalogSnapshotPersistenceMapper.class})
public class CatalogUpdateTestConfiguration {
    @Bean Clock clock() { return Clock.fixed(SnapshotFixtures.TIME, ZoneOffset.UTC); }
}
