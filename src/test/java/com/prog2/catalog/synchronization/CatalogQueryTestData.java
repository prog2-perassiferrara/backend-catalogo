package com.prog2.catalog.synchronization;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.prog2.catalog.integration.domain.enums.*;
import com.prog2.catalog.integration.domain.model.*;
import com.prog2.catalog.integration.infrastructure.persistence.adapter.InMemoryIntegrationSessionAdapter;
import com.prog2.catalog.security.JwtTestTokens;
import com.prog2.catalog.synchronization.application.service.CatalogSynchronizationGuard;
import com.prog2.catalog.synchronization.domain.model.*;
import com.prog2.catalog.synchronization.domain.ports.out.*;

/** Datos ficticios compartidos: prepara y observa SQL/sesión reales sin sustituir los casos de consulta. */
class CatalogQueryTestData {
    final CatalogSnapshotRepository snapshots;
    final CatalogUpdateRepository updates;
    final CatalogSynchronizationGuard guard;
    final CatalogQueryTestConfiguration.QueryClock clock;
    final InMemoryIntegrationSessionAdapter sessions;
    final JdbcTemplate jdbc;

    CatalogQueryTestData(CatalogSnapshotRepository snapshots, CatalogUpdateRepository updates,
            CatalogSynchronizationGuard guard, CatalogQueryTestConfiguration.QueryClock clock,
            InMemoryIntegrationSessionAdapter sessions, JdbcTemplate jdbc) {
        this.snapshots = snapshots;
        this.updates = updates;
        this.guard = guard;
        this.clock = clock;
        this.sessions = sessions;
        this.jdbc = jdbc;
    }

    void reset() {
        clock.set(JwtTestTokens.NOW);
        guard.requireVerification();
        jdbc.update("DELETE FROM catalog_processed_event");
        jdbc.update("DELETE FROM catalog_rejected_record");
        jdbc.update("UPDATE catalog_sync_state SET applied_version = NULL WHERE id = 1");
        snapshots.replace(UUID.randomUUID(), snapshot(7, "Fictional", 30));
        updates.checked(new CatalogPublication(7, 1, clock.instant()));
        guard.confirmVerification();
        sessions.replace(new IntegrationSession("fictional-central-access", JwtTestTokens.NOW.plusSeconds(3600),
                IntegrationState.READY, new IntegrationConfiguration("fictional-group", ProvisioningStatus.PROVISIONED,
                "redis.invalid", 6379, "fictional-user", "fictional-password", "fictional:namespace",
                "kafka.invalid:9092", "fictional-consumer", "fictional-topic")));
    }

    static CatalogSnapshot snapshot(long version, String name, int duration) {
        Instant time = SnapshotFixtures.TIME;
        return new CatalogSnapshot(version, time,
                List.of(new ProfessionalCategory(1, "Fictional category", null, true, time, time)),
                List.of(new Professional(101, 1, name, "Professional", false, time, time),
                        new Professional(102, 1, "Empty", "Agenda", true, time, time)),
                List.of(schedule(203, 101, DayOfWeek.SUNDAY, 9, false, duration),
                        schedule(202, 101, DayOfWeek.MONDAY, 10, false, duration),
                        schedule(201, 101, DayOfWeek.MONDAY, 9, true, duration)));
    }

    private static WeeklySchedule schedule(long id, long professional, DayOfWeek day, int hour, boolean enabled, int duration) {
        return new WeeklySchedule(id, professional, day, LocalTime.of(hour, 0), LocalTime.of(hour + 1, 0), duration,
                enabled, SnapshotFixtures.TIME, SnapshotFixtures.TIME);
    }

    /** Fotografía de efectos observables, también detecta cambios inadvertidos de metadata o recibos. */
    List<List<Map<String, Object>>> persisted() {
        return List.of(jdbc.queryForList("SELECT * FROM catalog_sync_state"),
                jdbc.queryForList("SELECT * FROM professional ORDER BY id"),
                jdbc.queryForList("SELECT * FROM weekly_schedule ORDER BY id"),
                jdbc.queryForList("SELECT * FROM catalog_processed_event"),
                jdbc.queryForList("SELECT * FROM catalog_rejected_record"));
    }
}
