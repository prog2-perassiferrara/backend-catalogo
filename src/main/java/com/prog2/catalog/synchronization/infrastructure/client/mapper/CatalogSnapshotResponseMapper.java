package com.prog2.catalog.synchronization.infrastructure.client.mapper;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Objects;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

import com.prog2.catalog.synchronization.domain.enums.SnapshotFailureKind;
import com.prog2.catalog.synchronization.domain.exception.CatalogSnapshotException;
import com.prog2.catalog.synchronization.domain.model.*;
import com.prog2.catalog.synchronization.infrastructure.client.dto.*;

/** Traduce DTO externos a valores de dominio sin coerciones que oculten datos incompatibles. */
public class CatalogSnapshotResponseMapper {
    private final JsonMapper mapper;

    public CatalogSnapshotResponseMapper(JsonMapper mapper) {
        this.mapper = mapper.rebuild()
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT,
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .withCoercionConfig(LogicalType.Textual, config -> config
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                .build();
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

    private ProfessionalCategory category(ProfessionalCategoryResponse value) {
        return new ProfessionalCategory(value.id(), Objects.requireNonNull(value.name()),
                value.description(), value.enabled(), Instant.parse(value.createdAt()), Instant.parse(value.updatedAt()));
    }

    private Professional professional(ProfessionalResponse value) {
        return new Professional(value.id(), value.categoryId(), Objects.requireNonNull(value.firstName()),
                Objects.requireNonNull(value.lastName()), value.enabled(),
                Instant.parse(value.createdAt()), Instant.parse(value.updatedAt()));
    }

    private WeeklySchedule schedule(WeeklyScheduleResponse value) {
        return new WeeklySchedule(value.id(), value.professionalId(), DayOfWeek.valueOf(value.dayOfWeek()),
                time(value.startTime()), time(value.endTime()), value.slotDurationMinutes(), value.enabled(),
                Instant.parse(value.createdAt()), Instant.parse(value.updatedAt()));
    }

    private static LocalTime time(String value) {
        if (value == null || !value.matches("[0-9]{2}:[0-9]{2}(:[0-9]{2})?")) {
            throw new IllegalArgumentException("Invalid weekly time");
        }
        return LocalTime.parse(value);
    }
}
