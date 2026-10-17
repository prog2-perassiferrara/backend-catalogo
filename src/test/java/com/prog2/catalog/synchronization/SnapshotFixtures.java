package com.prog2.catalog.synchronization;

import java.time.*;
import java.util.List;
import com.prog2.catalog.synchronization.domain.model.*;

/** Datos exclusivamente ficticios, compartidos por pruebas de contrato y persistencia. */
public final class SnapshotFixtures {
    public static final Instant TIME = Instant.parse("2026-09-10T13:44:59.123456Z");
    private SnapshotFixtures() { }

    public static CatalogSnapshot snapshot(long version, long id) {
        return new CatalogSnapshot(version, TIME,
                List.of(new ProfessionalCategory(id, "Fictional category", null, false, TIME, TIME)),
                List.of(new Professional(id + 100, id, "Fictional", "Professional", false, TIME, TIME)),
                List.of(new WeeklySchedule(id + 200, id + 100, DayOfWeek.MONDAY,
                        LocalTime.of(9, 0), LocalTime.of(12, 0), 30, true, TIME, TIME)));
    }

    public static String json(long version) {
        return """
                {"snapshotVersion":%d,"generatedAt":"2026-09-10T13:44:59.123456Z","additionalField":true,
                 "professionalCategories":[{"id":1,"name":"Fictional category","description":null,
                    "enabled":false,"createdAt":"2026-09-10T13:44:59.123456Z","updatedAt":"2026-09-10T13:44:59.123456Z"}],
                 "professionals":[{"id":101,"categoryId":1,"firstName":"Fictional","lastName":"Professional",
                    "enabled":false,"createdAt":"2026-09-10T13:44:59.123456Z","updatedAt":"2026-09-10T13:44:59.123456Z"}],
                 "weeklySchedules":[{"id":201,"professionalId":101,"dayOfWeek":"MONDAY","startTime":"09:00",
                    "endTime":"12:00:00","slotDurationMinutes":30,"enabled":true,
                    "createdAt":"2026-09-10T13:44:59.123456Z","updatedAt":"2026-09-10T13:44:59.123456Z"}]}
                """.formatted(version);
    }

    public static String emptyJson(long version) {
        return """
                {"snapshotVersion":%d,"generatedAt":"2026-09-10T13:44:59Z",
                 "professionalCategories":[],"professionals":[],"weeklySchedules":[]}
                """.formatted(version);
    }
}
