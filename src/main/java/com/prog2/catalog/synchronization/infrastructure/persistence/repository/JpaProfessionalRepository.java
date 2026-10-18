package com.prog2.catalog.synchronization.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.prog2.catalog.synchronization.infrastructure.persistence.entity.ProfessionalEntity;

/** Acceso Spring Data a las filas propias de professional. */
public interface JpaProfessionalRepository extends JpaRepository<ProfessionalEntity, Long> { }
