package com.prog2.catalog.synchronization.infrastructure.persistence.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.prog2.catalog.synchronization.infrastructure.persistence.entity.WeeklyScheduleEntity;

/** Acceso Spring Data a las filas propias de weekly_schedule. */
public interface JpaWeeklyScheduleRepository extends JpaRepository<WeeklyScheduleEntity, Long> {
    /** @return todos los horarios del profesional, incluidas sus bajas lógicas */
    List<WeeklyScheduleEntity> findByProfessionalId(long professionalId);
}
