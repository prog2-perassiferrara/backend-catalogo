package com.prog2.catalog.synchronization;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import com.prog2.catalog.synchronization.domain.exception.CatalogSnapshotException;
import com.prog2.catalog.synchronization.domain.model.*;
import com.prog2.catalog.synchronization.infrastructure.client.mapper.CatalogSnapshotResponseMapper;

import static org.assertj.core.api.Assertions.*;

class CatalogSnapshotValidationTests {
    private final CatalogSnapshotResponseMapper mapper =
            new CatalogSnapshotResponseMapper(JsonMapper.builder().build());

    @Test
    void preservesDisabledEntitiesIdsTimesAndCompatibleAdditionalFields() {
        CatalogSnapshot result = mapper.read(SnapshotFixtures.json(7));
        assertThat(result).isEqualTo(SnapshotFixtures.snapshot(7, 1));
        assertThat(result.professionalCategories().getFirst().enabled()).isFalse();
        assertThat(result.weeklySchedules().getFirst().startTime()).hasHour(9).hasMinute(0);
    }

    @Test
    void acceptsEmptyCollectionsWithoutInventingAnInitialVersion() {
        CatalogSnapshot result = mapper.read(SnapshotFixtures.emptyJson(0));
        assertThat(result.snapshotVersion()).isZero();
        assertThat(result.professionalCategories()).isEmpty();
        assertThat(result.professionals()).isEmpty();
        assertThat(result.weeklySchedules()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"snapshotVersion", "generatedAt", "professionalCategories", "professionals", "weeklySchedules"})
    void rejectsMissingRequiredSnapshotFields(String field) {
        var json = (tools.jackson.databind.node.ObjectNode) JsonMapper.builder().build()
                .readTree(SnapshotFixtures.json(7));
        json.remove(field);
        String body = json.toString();
        assertThatThrownBy(() -> mapper.read(body)).isInstanceOf(CatalogSnapshotException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "\"snapshotVersion\":7|\"snapshotVersion\":\"7\"",
        "\"snapshotVersion\":7|\"snapshotVersion\":7.1",
        "\"enabled\":false|\"enabled\":\"false\"",
        "\"enabled\":false|\"enabled\":null",
        "\"id\":1|\"id\":true",
        "\"name\":\"Fictional category\"|\"name\":22",
        "\"categoryId\":1|\"categoryId\":999",
        "\"professionalId\":101|\"professionalId\":999",
        "\"dayOfWeek\":\"MONDAY\"|\"dayOfWeek\":\"FUNDAY\"",
        "\"startTime\":\"09:00\"|\"startTime\":\"25:00\"",
        "\"startTime\":\"09:00\"|\"startTime\":\"09:00:00.5\"",
        "\"slotDurationMinutes\":30|\"slotDurationMinutes\":30.5",
        "\"createdAt\":\"2026-09-10T13:44:59.123456Z\"|\"createdAt\":\"yesterday\""
    })
    void rejectsIncompatibleTypesReferencesAndTimesWithoutExposingTheBody(String replacement) {
        String[] parts = replacement.split("\\|", 2);
        String body = SnapshotFixtures.json(7).replace(parts[0], parts[1]);
        assertThatThrownBy(() -> mapper.read(body)).isInstanceOf(CatalogSnapshotException.class)
                .hasMessage("INVALID_CATALOG_SNAPSHOT").hasNoCause();
    }

    @Test
    void rejectsDuplicatesAndNullElementsInEveryCollection() {
        CatalogSnapshot valid = SnapshotFixtures.snapshot(7, 1);
        var category = valid.professionalCategories().getFirst();
        var professional = valid.professionals().getFirst();
        var schedule = valid.weeklySchedules().getFirst();
        List<CatalogSnapshot> invalid = List.of(
                new CatalogSnapshot(7, SnapshotFixtures.TIME, List.of(category, category),
                        valid.professionals(), valid.weeklySchedules()),
                new CatalogSnapshot(7, SnapshotFixtures.TIME, valid.professionalCategories(),
                        List.of(professional, professional), valid.weeklySchedules()),
                new CatalogSnapshot(7, SnapshotFixtures.TIME, valid.professionalCategories(),
                        valid.professionals(), List.of(schedule, schedule)));
        for (CatalogSnapshot snapshot : invalid) {
            assertThatThrownBy(snapshot::validate).isInstanceOf(CatalogSnapshotException.class);
        }
        String body = SnapshotFixtures.emptyJson(7).replace("\"professionals\":[]", "\"professionals\":[null]");
        assertThatThrownBy(() -> mapper.read(body)).isInstanceOf(CatalogSnapshotException.class);
    }

    @Test
    void snapshotCollectionsCannotBeChangedAfterValidation() {
        var categories = new java.util.ArrayList<>(SnapshotFixtures.snapshot(7, 1).professionalCategories());
        var snapshot = new CatalogSnapshot(7, SnapshotFixtures.TIME, categories, List.of(), List.of());
        categories.clear();
        assertThat(snapshot.professionalCategories()).hasSize(1);
        assertThatThrownBy(() -> snapshot.professionalCategories().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
