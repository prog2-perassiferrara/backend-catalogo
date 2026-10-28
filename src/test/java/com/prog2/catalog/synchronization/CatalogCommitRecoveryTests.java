package com.prog2.catalog.synchronization;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import com.prog2.catalog.synchronization.application.service.CatalogSynchronizationGuard;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

import com.prog2.catalog.synchronization.application.usecases.*;
import com.prog2.catalog.infrastructure.config.CatalogRemoteClients;
import com.prog2.catalog.infrastructure.startup.CatalogSynchronizationMonitor;
import com.prog2.catalog.synchronization.domain.enums.*;
import com.prog2.catalog.synchronization.domain.model.*;
import com.prog2.catalog.synchronization.domain.ports.in.RecoverCatalogUseCase;
import com.prog2.catalog.synchronization.domain.ports.out.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Confirma recuperación y reconciliación con transacciones y timeouts JDBC reales. */
@Testcontainers
@SpringBootTest(classes = {CatalogUpdateTestConfiguration.class, CatalogCommitRecoveryTests.FaultConfiguration.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("update-persistence-test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Timeout(40)
class CatalogCommitRecoveryTests {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.8")
            .withDatabaseName("commit_recovery_test").withUsername("commit_recovery_test")
            .withPassword("fictional-commit-password").withCommand("--log-bin-trust-function-creators=1")
            .withTmpFs(Map.of("/var/lib/mysql", "rw"));

    @DynamicPropertySource static void settings(DynamicPropertyRegistry registry) {
        registry.add("DB_HOST", MYSQL::getHost); registry.add("DB_PORT", MYSQL::getFirstMappedPort);
        registry.add("DB_NAME", MYSQL::getDatabaseName); registry.add("DB_USER", MYSQL::getUsername);
        registry.add("DB_PASSWORD", MYSQL::getPassword);
    }

    private final CatalogSnapshotRepository snapshots;
    private final CatalogUpdateRepository updates;
    private final JdbcTemplate jdbc;
    private final CommitFaultDataSource faults;

    CatalogCommitRecoveryTests(CatalogSnapshotRepository snapshots, CatalogUpdateRepository updates,
            JdbcTemplate jdbc, CommitFaultDataSource faults) {
        this.snapshots = snapshots; this.updates = updates; this.jdbc = jdbc; this.faults = faults;
    }

    @BeforeEach void reset() {
        faults.resetFault();
        jdbc.execute("DROP TRIGGER IF EXISTS slow_fictional_receipt");
        jdbc.update("DELETE FROM catalog_processed_event");
        jdbc.update("DELETE FROM catalog_rejected_record");
        jdbc.update("UPDATE catalog_sync_state SET applied_version = NULL WHERE id = 1");
        snapshots.replace(UUID.randomUUID(), SnapshotFixtures.snapshot(7, 1));
    }

    @ParameterizedTest
    @EnumSource(value = CommitFaultDataSource.Moment.class, names = {"BEFORE_COMMIT", "AFTER_COMMIT"})
    void metadataCommitTimeoutCanRecoverOnTheNextCheckWithoutRestart(CommitFaultDataSource.Moment moment) {
        var changes = mock(CatalogChangesGateway.class);
        when(changes.readSince(7)).thenReturn(CatalogUpdatePersistenceTests.batch(7, 7, 1));
        var clock = mock(Clock.class);
        when(clock.instant()).thenReturn(SnapshotFixtures.TIME);
        var useCase = updates(changes, clock);
        var clients = mock(CatalogRemoteClients.class);
        var settings = mock(CatalogConnectionSettingsGateway.class);
        when(settings.getSettings()).thenReturn(new CatalogConnectionSettings("127.0.0.1", 6379,
                "fictional-user", "fictional-redis-secret", "fictional:catalog",
                "127.0.0.1:9092", "fictional-group", "fictional-topic"));
        var scheduler = mock(ScheduledExecutorService.class);
        try (var monitor = new CatalogSynchronizationMonitor(clients, settings, snapshots, useCase, scheduler)) {
            monitor.start();
            var scheduled = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).scheduleWithFixedDelay(scheduled.capture(), eq(0L), eq(30L), eq(TimeUnit.SECONDS));
            Runnable check = scheduled.getValue();
            faults.arm("update catalog_sync_state", moment);

            check.run();
            assertThat(faults.triggeredFaults()).isEqualTo(1);
            assertThat(updates.getState().phase()).isEqualTo(SynchronizationPhase.UNVERIFIABLE);
            assertThat(updates.getState().safeDiagnostic()).isEqualTo("LOCAL_STORAGE_UNCERTAIN");
            assertThat(updates.getState().appliedVersion()).isEqualTo(7);
            verifyNoInteractions(changes);
            verify(scheduler, never()).shutdown();
            verify(clients, never()).stopConsumer();

            when(clock.instant()).thenReturn(SnapshotFixtures.TIME.plusSeconds(29));
            check.run();
            verifyNoInteractions(changes);
            when(clock.instant()).thenReturn(SnapshotFixtures.TIME.plusSeconds(30));
            check.run();
            assertThat(updates.getState().phase()).isEqualTo(SynchronizationPhase.CURRENT);
            verify(changes).readSince(7);
        }
    }

    @ParameterizedTest
    @EnumSource(value = CommitFaultDataSource.Moment.class, names = {"BEFORE_COMMIT", "AFTER_COMMIT"})
    void anUncertainBatchReconcilesARealCommitOrRetriesOnlyItsRollback(CommitFaultDataSource.Moment moment) {
        var changes = mock(CatalogChangesGateway.class);
        when(changes.readSince(7)).thenReturn(CatalogUpdatePersistenceTests.batch(7, 10, 2));
        var useCase = updates(changes, Clock.fixed(SnapshotFixtures.TIME, ZoneOffset.UTC));
        var event = CatalogUpdatePersistenceTests.notice("fictional-commit-event", 10);
        faults.arm("insert into professional ", moment);

        assertThat(useCase.process(event).recorded()).isTrue();
        assertThat(faults.triggeredFaults()).isEqualTo(1);
        assertThat(faults.matchingWriteCommits()).isEqualTo(moment == CommitFaultDataSource.Moment.AFTER_COMMIT ? 1 : 2);
        assertThat(updates.getState().appliedVersion()).isEqualTo(10);
        assertThat(updates.isProcessed(event.eventId())).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_processed_event", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT id FROM professional ORDER BY id", Long.class)).containsExactly(101L, 102L);
        verify(changes).readSince(7);
        assertThat(useCase.process(event).recorded()).isTrue();
        verifyNoMoreInteractions(changes);
    }

    @ParameterizedTest
    @EnumSource(value = CommitFaultDataSource.Moment.class, names = {"BEFORE_COMMIT", "AFTER_COMMIT"})
    void snapshotCommitTimeoutPreservesItsReconciliationContract(CommitFaultDataSource.Moment moment) {
        var gateway = mock(CatalogSnapshotGateway.class);
        when(gateway.fetch(any())).thenReturn(SnapshotFixtures.snapshot(10, 2));
        var useCase = new SynchronizeCatalogUseCaseImpl(gateway, snapshots, mock(TechnicalAccessGateway.class),
                Clock.fixed(SnapshotFixtures.TIME, ZoneOffset.UTC), Duration.ZERO, new CatalogSynchronizationGuard());
        faults.arm("insert into professional ", moment);

        assertThat(useCase.synchronize().phase()).isEqualTo(SynchronizationPhase.SNAPSHOT_APPLIED);
        assertThat(faults.triggeredFaults()).isEqualTo(1);
        assertThat(faults.matchingWriteCommits()).isEqualTo(moment == CommitFaultDataSource.Moment.AFTER_COMMIT ? 1 : 2);
        assertThat(snapshots.getState().appliedVersion()).isEqualTo(10);
        assertThat(jdbc.queryForList("SELECT id FROM professional ORDER BY id", Long.class)).containsExactly(102L);
        verify(gateway).fetch(any());
    }

    private UpdateCatalogUseCaseImpl updates(CatalogChangesGateway changes, Clock clock) {
        var recovery = mock(RecoverCatalogUseCase.class);
        when(recovery.resume()).thenReturn(Optional.empty());
        return new UpdateCatalogUseCaseImpl(changes, updates, recovery, mock(TechnicalAccessGateway.class),
                clock, Duration.ZERO, new CatalogSynchronizationGuard());
    }

    @Test
    void aFailedRollbackOnAClosedConnectionCanBeRetriedWithoutAcknowledgingEarly() {
        jdbc.execute("""
                CREATE TRIGGER slow_fictional_receipt BEFORE INSERT ON catalog_processed_event
                FOR EACH ROW DO SLEEP(1)
                """);
        var changes = mock(CatalogChangesGateway.class);
        var clock = mock(Clock.class);
        when(clock.instant()).thenReturn(SnapshotFixtures.TIME);
        var useCase = updates(changes, clock);
        var event = CatalogUpdatePersistenceTests.notice("fictional-closed-connection", 7);
        faults.arm("insert into catalog_processed_event", CommitFaultDataSource.Moment.DURING_STATEMENT);
        try {
            var result = useCase.process(event);
            assertThat(faults.triggeredFaults()).isEqualTo(1);
            assertThat(result.failureKind()).isEqualTo(SnapshotFailureKind.UNCERTAIN);
            assertThat(result.terminal()).isFalse();
            assertThat(result.recorded()).isFalse();
            assertThat(updates.isProcessed(event.eventId())).isFalse();
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS slow_fictional_receipt");
        }
        when(clock.instant()).thenReturn(SnapshotFixtures.TIME.plusSeconds(30));
        assertThat(useCase.process(event).recorded()).isTrue();
        assertThat(updates.getState().appliedVersion()).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_processed_event", Integer.class)).isEqualTo(1);
        verifyNoInteractions(changes);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FaultConfiguration {
        @Bean(destroyMethod = "close") HikariDataSource commitTestPool() {
            var pool = new HikariDataSource();
            pool.setJdbcUrl(MYSQL.getJdbcUrl());
            pool.setUsername(MYSQL.getUsername()); pool.setPassword(MYSQL.getPassword());
            pool.setMaximumPoolSize(3);
            return pool;
        }

        @Bean @Primary CommitFaultDataSource dataSource(HikariDataSource commitTestPool) {
            return new CommitFaultDataSource(commitTestPool);
        }
    }
}
