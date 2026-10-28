package com.prog2.catalog.synchronization.infrastructure.persistence.service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import com.prog2.catalog.synchronization.domain.model.*;
import com.prog2.catalog.synchronization.infrastructure.persistence.mapper.CatalogSnapshotPersistenceMapper;
import com.prog2.catalog.synchronization.infrastructure.persistence.repository.*;
import com.prog2.catalog.synchronization.infrastructure.persistence.specification.ProfessionalSpecifications;

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
    private final JpaProfessionalCategoryRepository categories;
    private final CatalogSnapshotPersistenceMapper mapper;

    public TransactionalCatalogQueryReader(JpaCatalogSyncStateRepository states, JpaProfessionalRepository professionals,
            JpaWeeklyScheduleRepository schedules, JpaProfessionalCategoryRepository categories,
            CatalogSnapshotPersistenceMapper mapper) {
        this.states = states;
        this.professionals = professionals;
        this.schedules = schedules;
        this.categories = categories;
        this.mapper = mapper;
    }

    /** @return página y total de la vista que comienza con la lectura de metadata */
    public CatalogSearchView search(ProfessionalSearchCriteria criteria) {
        CatalogState state = state();
        if (!state.hasCopy()) { return new CatalogSearchView(state, List.of(), 0); }
        var page = professionals.findAll(ProfessionalSpecifications.matching(criteria),
                PageRequest.of(criteria.page(), criteria.size(), Sort.by("lastName", "firstName", "id")));
        var ids = page.getContent().stream().map(value -> value.getCategoryId()).distinct().toList();
        var byId = categories.findAllById(ids).stream().map(mapper::toDomain)
                .collect(Collectors.toMap(ProfessionalCategory::id, Function.identity()));
        var entries = page.getContent().stream().map(value -> new ProfessionalSearchEntry(mapper.toDomain(value),
                Objects.requireNonNull(byId.get(value.getCategoryId()), "CATALOG_CATEGORY_UNAVAILABLE"))).toList();
        return new CatalogSearchView(state, entries, page.getTotalElements());
    }

    /** @return categorías completas y metadata confirmada, incluidas las deshabilitadas */
    public CatalogCategoriesView readCategories() {
        CatalogState state = state();
        var content = state.hasCopy() ? categories.findAll(Sort.by("name", "id")).stream().map(mapper::toDomain).toList()
                : List.<ProfessionalCategory>of();
        return new CatalogCategoriesView(state, content);
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
