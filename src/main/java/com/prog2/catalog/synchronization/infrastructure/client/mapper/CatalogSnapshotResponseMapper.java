package com.prog2.catalog.synchronization.infrastructure.client.mapper;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Objects;

import tools.jackson.databind.json.JsonMapper;
import com.prog2.catalog.infrastructure.config.CatalogJsonMapperFactory;

import com.prog2.catalog.synchronization.domain.enums.SnapshotFailureKind;
import com.prog2.catalog.synchronization.domain.exception.CatalogSnapshotException;
import com.prog2.catalog.synchronization.domain.model.*;
import com.prog2.catalog.synchronization.infrastructure.client.dto.*;

/** Traduce DTO externos a valores de dominio sin coerciones que oculten datos incompatibles. */
public class CatalogSnapshotResponseMapper {
    private final JsonMapper mapper;

    public CatalogSnapshotResponseMapper(JsonMapper mapper) {
        this.mapper = CatalogJsonMapperFactory.strict(mapper);
    }

    /**
     * Interpreta la respuesta completa. No incorpora cuerpos ni causas externas en el fallo público.
     *
     * @param body JSON central
     * @return copia completa validada
     * @throws CatalogSnapshotException si falta información o es incompatible
     */
    public CatalogSnapshot read(String body) {
        try {
            CatalogSnapshotResponse response = mapper.readValue(body, CatalogSnapshotResponse.class);
            CatalogSnapshot snapshot = new CatalogSnapshot(response.snapshotVersion(),
                    Instant.parse(response.generatedAt()),
                    response.professionalCategories().stream().map(this::category).toList(),
                    response.professionals().stream().map(this::professional).toList(),
                    response.weeklySchedules().stream().map(this::schedule).toList());
            snapshot.validate();
            return snapshot;
        } catch (CatalogSnapshotException failure) {
            throw failure;
        } catch (RuntimeException _) {
            throw new CatalogSnapshotException(SnapshotFailureKind.TERMINAL, "INVALID_CATALOG_SNAPSHOT");
        }
    }

    public ProfessionalCategory category(ProfessionalCategoryResponse value) {
        return new ProfessionalCategory(value.id(), Objects.requireNonNull(value.name()),
                value.description(), value.enabled(), Instant.parse(value.createdAt()), Instant.parse(value.updatedAt()));
    }

    public Professional professional(ProfessionalResponse value) {
        return new Professional(value.id(), value.categoryId(), Objects.requireNonNull(value.firstName()),
                Objects.requireNonNull(value.lastName()), value.enabled(),
                Instant.parse(value.createdAt()), Instant.parse(value.updatedAt()));
    }

    public WeeklySchedule schedule(WeeklyScheduleResponse value) {
        return new WeeklySchedule(value.id(), value.professionalId(), DayOfWeek.valueOf(value.dayOfWeek()),
                time(value.startTime()), time(value.endTime()), value.slotDurationMinutes(), value.enabled(),
                Instant.parse(value.createdAt()), Instant.parse(value.updatedAt()));
    }

    private static LocalTime time(String value) {
        if (value == null || !value.matches("\\d{2}:\\d{2}(:\\d{2})?")) {
            throw new IllegalArgumentException("Invalid weekly time");
        }
        return LocalTime.parse(value);
    }
}
