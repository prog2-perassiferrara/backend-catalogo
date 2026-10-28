package com.prog2.catalog.synchronization.infrastructure.persistence.service;

import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import com.prog2.catalog.synchronization.domain.model.*;
import com.prog2.catalog.synchronization.infrastructure.persistence.mapper.CatalogSnapshotPersistenceMapper;
import com.prog2.catalog.synchronization.infrastructure.persistence.repository.*;

/**
 * Frontera de lectura: los SELECT comparten snapshot MySQL sin bloquear al escritor.
 * El adaptador llama a este bean mediante el proxy transaccional de Spring.
 */
@Component
@Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW,
        isolation = Isolation.REPEATABLE_READ, timeout = 5)
public class TransactionalCatalogQueryReader {
    private final JpaCatalogSyncStateRepository states;
    private final JpaProfessionalRepository professionals;
    private final JpaWeeklyScheduleRepository schedules;
    private final CatalogSnapshotPersistenceMapper mapper;

    public TransactionalCatalogQueryReader(JpaCatalogSyncStateRepository states, JpaProfessionalRepository professionals,
            JpaWeeklyScheduleRepository schedules, CatalogSnapshotPersistenceMapper mapper) {
        this.states = states;
        this.professionals = professionals;
        this.schedules = schedules;
        this.mapper = mapper;
    }

    /** @return metadata, profesional y horarios materializados de una misma copia confirmada */
    public CatalogReadView readProfessional(long professionalId) {
        CatalogState state = state();
        var professional = professionals.findById(professionalId).map(mapper::toDomain);
        List<WeeklySchedule> weekly = professional.isEmpty() ? List.of() : schedules.findByProfessionalId(professionalId)
                .stream().map(mapper::toDomain)
                .sorted(Comparator.comparing(WeeklySchedule::dayOfWeek).thenComparing(WeeklySchedule::startTime)
                        .thenComparingLong(WeeklySchedule::id)).toList();
        return new CatalogReadView(state, professional, weekly);
    }

    /** @return únicamente metadata confirmada, sin acceso a filas de profesionales u horarios */
    public CatalogState readState() { return state(); }

    private CatalogState state() { return mapper.toDomain(states.findById(1L).orElseThrow()); }
}
