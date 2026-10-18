package com.prog2.catalog.synchronization.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.prog2.catalog.synchronization.infrastructure.persistence.entity.ProfessionalCategoryEntity;

/** Acceso Spring Data a las filas propias de professional_category. */
public interface JpaProfessionalCategoryRepository extends JpaRepository<ProfessionalCategoryEntity, Long> { }
