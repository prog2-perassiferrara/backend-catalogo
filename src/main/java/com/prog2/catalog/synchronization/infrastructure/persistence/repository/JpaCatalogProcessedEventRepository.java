package com.prog2.catalog.synchronization.infrastructure.persistence.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.prog2.catalog.synchronization.infrastructure.persistence.entity.CatalogProcessedEventEntity;
/** Acceso JPA a recibos, sin participación directa de la capa de aplicación. */
public interface JpaCatalogProcessedEventRepository extends JpaRepository<CatalogProcessedEventEntity, byte[]> { }
