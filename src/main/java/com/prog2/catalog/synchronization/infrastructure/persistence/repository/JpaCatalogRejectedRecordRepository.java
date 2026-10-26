package com.prog2.catalog.synchronization.infrastructure.persistence.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.prog2.catalog.synchronization.infrastructure.persistence.entity.*;
/** Acceso JPA a rechazos identificados por coordenadas del registro. */
public interface JpaCatalogRejectedRecordRepository extends JpaRepository<CatalogRejectedRecordEntity, CatalogRejectedRecordKey> { }
