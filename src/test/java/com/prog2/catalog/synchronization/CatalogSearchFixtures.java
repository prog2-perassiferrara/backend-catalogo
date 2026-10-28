package com.prog2.catalog.synchronization;

import java.time.*;
import java.util.*;
import com.prog2.catalog.synchronization.domain.model.*;

/** Copias ficticias con nombres, flags y horarios que permiten distinguir cada filtro. */
final class CatalogSearchFixtures {
    private CatalogSearchFixtures() { }

    static void install(CatalogQueryTestData data) {
        data.reset();
        data.snapshots.replace(UUID.randomUUID(), snapshot(7));
        data.updates.checked(new CatalogPublication(7, 1, data.clock.instant()));
        data.guard.confirmVerification();
    }

    static CatalogSnapshot snapshot(long version) {
        Instant time = SnapshotFixtures.TIME;
        return new CatalogSnapshot(version, time,
                List.of(new ProfessionalCategory(1, "Clínica", null, true, time, time),
                        new ProfessionalCategory(2, "Archivo", null, false, time, time),
                        new ProfessionalCategory(3, "Sin profesionales", null, true, time, time)),
                List.of(professional(101, 1, "Ana", "Gómez", true),
                        professional(102, 2, "ANA", "Gomez", true),
                        professional(103, 1, "Bruno", "Álvarez", false),
                        professional(104, 2, "Ana", "López", false),
                        professional(105, 1, "Literal%_!", "Prueba", true),
                        professional(106, 1, "Zeta", "Gómez", true)),
                List.of(schedule(201, 101, DayOfWeek.MONDAY, 9, true),
                        schedule(202, 101, DayOfWeek.MONDAY, 10, true),
                        schedule(203, 101, DayOfWeek.TUESDAY, 9, false),
                        schedule(204, 102, DayOfWeek.TUESDAY, 9, true),
                        schedule(205, 103, DayOfWeek.MONDAY, 9, false),
                        schedule(206, 104, DayOfWeek.MONDAY, 9, true)));
    }

    private static Professional professional(long id, long category, String first, String last, boolean enabled) {
        return new Professional(id, category, first, last, enabled, SnapshotFixtures.TIME, SnapshotFixtures.TIME);
    }

    private static WeeklySchedule schedule(long id, long professional, DayOfWeek day, int hour, boolean enabled) {
        return new WeeklySchedule(id, professional, day, LocalTime.of(hour, 0), LocalTime.of(hour + 1, 0),
                30, enabled, SnapshotFixtures.TIME, SnapshotFixtures.TIME);
    }

    static ProfessionalSearchCriteria all(int page, int size) {
        return new ProfessionalSearchCriteria(null, null, null, null, page, size);
    }
}

