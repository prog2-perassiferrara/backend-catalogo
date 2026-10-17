package com.prog2.catalog.synchronization.infrastructure.persistence.repository;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import com.prog2.catalog.synchronization.infrastructure.persistence.entity.CatalogSyncStateEntity;

/** Metadata única. Las escrituras toman el mismo bloqueo antes de comparar o publicar versiones. */
public interface JpaCatalogSyncStateRepository extends JpaRepository<CatalogSyncStateEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from CatalogSyncStateEntity state where state.id = 1")
    Optional<CatalogSyncStateEntity> lockState();
}
