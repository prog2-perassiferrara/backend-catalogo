package com.prog2.catalog.synchronization;

import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.TransactionSystemException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.prog2.catalog.synchronization.application.usecases.SynchronizeCatalogUseCaseImpl;
import com.prog2.catalog.synchronization.domain.enums.*;
import com.prog2.catalog.synchronization.domain.exception.CatalogSnapshotException;
import com.prog2.catalog.synchronization.domain.model.*;
import com.prog2.catalog.synchronization.domain.ports.out.*;
import com.prog2.catalog.synchronization.infrastructure.persistence.adapter.JpaCatalogSnapshotAdapter;
import com.prog2.catalog.synchronization.infrastructure.persistence.entity.CatalogSyncStateEntity;
import com.prog2.catalog.synchronization.infrastructure.persistence.mapper.CatalogSnapshotPersistenceMapper;
import com.prog2.catalog.synchronization.infrastructure.persistence.repository.*;
import com.prog2.catalog.synchronization.infrastructure.persistence.service.TransactionalCatalogSnapshotWriter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest(classes = CatalogSnapshotPersistenceTests.PersistenceConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@ActiveProfiles("snapshot-persistence-test")
@MockitoSpyBean(types = {JpaProfessionalCategoryRepository.class, JpaCatalogSyncStateRepository.class})
@Timeout(40)
class CatalogSnapshotPersistenceTests {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.8")
            .withDatabaseName("snapshot_test").withUsername("snapshot_test")
            .withPassword("fictional-snapshot-password")
            .withCommand("--log-bin-trust-function-creators=1");

    @DynamicPropertySource
    static void settings(DynamicPropertyRegistry registry) {
        // Simula una base del setup ya existente: Boot debe aplicar V2 sin perder V1.
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .target("1").load().migrate();
        registry.add("DB_HOST", MYSQL::getHost);
        registry.add("DB_PORT", MYSQL::getFirstMappedPort);
        registry.add("DB_NAME", MYSQL::getDatabaseName);
        registry.add("DB_USER", MYSQL::getUsername);
        registry.add("DB_PASSWORD", MYSQL::getPassword);
    }

    private final CatalogSnapshotRepository repository;
    private final JpaProfessionalCategoryRepository categories;
    private final JpaCatalogSyncStateRepository states;
    private final TransactionalCatalogSnapshotWriter writer;
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    CatalogSnapshotPersistenceTests(CatalogSnapshotRepository repository,
            JpaProfessionalCategoryRepository categories, JpaCatalogSyncStateRepository states,
            TransactionalCatalogSnapshotWriter writer, JdbcTemplate jdbc, DataSource dataSource) {
        this.repository = repository;
        this.categories = categories;
        this.states = states;
        this.writer = writer;
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    @BeforeEach
    void clearRows() {
        jdbc.update("DELETE FROM weekly_schedule");
        jdbc.update("DELETE FROM professional");
        jdbc.update("DELETE FROM professional_category");
        jdbc.update("""
                UPDATE catalog_sync_state SET applied_version = NULL, applied_run_id = NULL,
                generated_at = NULL, applied_at = NULL, last_run_id = NULL, phase = 'NOT_STARTED',
                attempted_at = NULL, safe_diagnostic = 'NOT_STARTED' WHERE id = 1
                """);
    }

    @Test
    void upgradesTheExistingSetupAndDistinguishesEmptyCopyFromNoCopy() {
        assertThat(jdbc.queryForObject("SELECT description FROM setup_probe WHERE id = 1", String.class))
                .isEqualTo("fictional setup data");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '2' AND success = 1", Integer.class))
                .isEqualTo(1);
        assertThat(repository.getState().hasCopy()).isFalse();
        var empty = new CatalogSnapshot(0, SnapshotFixtures.TIME, List.of(), List.of(), List.of());
        var runId = UUID.randomUUID();
        repository.beginAttempt(runId, SnapshotFixtures.TIME);
        CatalogState result = repository.replace(runId, empty);
        assertThat(result.hasCopy()).isTrue();
        assertThat(result.appliedVersion()).isZero();
        assertThat(result.appliedRunId()).isEqualTo(runId);
        assertThat(result.phase()).isEqualTo(SynchronizationPhase.SNAPSHOT_APPLIED);
    }

    @Test
    void replacesEveryCollectionPreservesDisabledRowsAndAcceptsEqualVersions() {
        repository.replace(UUID.randomUUID(), SnapshotFixtures.snapshot(7, 1));
        UUID runId = UUID.randomUUID();
        repository.replace(runId, SnapshotFixtures.snapshot(8, 2));
        assertCopy(8, 2);
        assertThat(jdbc.queryForObject("SELECT enabled FROM professional_category WHERE id = 2", Boolean.class))
                .isFalse();
        assertThat(jdbc.queryForObject("SELECT CAST(start_time AS CHAR) FROM weekly_schedule WHERE id = 202",
                String.class)).isEqualTo("09:00:00");
        assertThat(categories.findById(2L).orElseThrow().getCreatedAt()).isEqualTo(SnapshotFixtures.TIME);
        // El mismo runId no escribe otra copia, incluso si se le entrega otro contenido.
        repository.replace(runId, SnapshotFixtures.snapshot(9, 3));
        assertCopy(8, 2);
        repository.replace(UUID.randomUUID(), SnapshotFixtures.snapshot(8, 4));
        assertCopy(8, 4);
    }

    @Test
    void rejectsLowerVersionWithoutChangingConfirmedRowsOrMetadata() {
        UUID original = UUID.randomUUID();
        repository.replace(original, SnapshotFixtures.snapshot(8, 2));
        UUID runId = UUID.randomUUID();
        CatalogSnapshot older = SnapshotFixtures.snapshot(7, 1);
        assertThatThrownBy(() -> repository.replace(runId, older))
                .isInstanceOf(CatalogSnapshotException.class).hasMessage("SNAPSHOT_VERSION_REGRESSION");
        assertCopy(8, 2);
        assertThat(repository.getState().appliedRunId()).isEqualTo(original);
    }

    @Test
    void rollsBackDeletesAndEarlierInsertsWhenALaterCollectionFails() {
        UUID original = UUID.randomUUID();
        repository.replace(original, SnapshotFixtures.snapshot(7, 1));
        jdbc.execute("""
                CREATE TRIGGER reject_professional BEFORE INSERT ON professional FOR EACH ROW
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'fictional-storage-failure'
                """);
        try {
            UUID runId = UUID.randomUUID();
            CatalogSnapshot candidate = SnapshotFixtures.snapshot(8, 2);
            assertThatThrownBy(() -> repository.replace(runId, candidate))
                    .isInstanceOf(CatalogSnapshotException.class).hasNoCause();
            assertCopy(7, 1);
            assertThat(repository.getState().appliedRunId()).isEqualTo(original);
        } finally {
            jdbc.execute("DROP TRIGGER reject_professional");
        }
    }

    @Test
    void databaseRejectsBrokenReferencesEvenOutsideTheMapper() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO professional (id, category_id, first_name, last_name, enabled, created_at, updated_at)
                VALUES (101, 999, 'Fictional', 'Professional', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void readersSeeCompleteCopiesAndRepeatableReadKeepsTheVersionAcrossCommit() throws Exception {
        repository.replace(UUID.randomUUID(), SnapshotFixtures.snapshot(7, 1));
        CountDownLatch prepared = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            prepared.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Writer was not released");
            }
            return mockingDetails(states).getMockCreationSettings().getDefaultAnswer().answer(invocation);
        }).when(states).saveAndFlush(any(CatalogSyncStateEntity.class));
        try (var executor = Executors.newSingleThreadExecutor();
                Connection reader = dataSource.getConnection()) {
            reader.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            reader.setAutoCommit(false);
            assertThat(readVersion(reader)).isEqualTo(7);
            var writing = executor.submit(() -> repository.replace(UUID.randomUUID(), SnapshotFixtures.snapshot(8, 2)));
            try {
                assertThat(prepared.await(5, TimeUnit.SECONDS)).isTrue();
                assertCopy(7, 1);
            } finally {
                release.countDown();
            }
            writing.get(5, TimeUnit.SECONDS);
            assertThat(readVersion(reader)).isEqualTo(7);
            try (var statement = reader.createStatement();
                    var rows = statement.executeQuery("SELECT id FROM professional_category")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isEqualTo(1);
                assertThat(rows.next()).isFalse();
            }
            reader.commit();
            assertCopy(8, 2);
        } finally {
            release.countDown();
        }
    }

    @Test
    void connectionLossBeforeCommitLeavesThePreviousCopyIntact() {
        repository.replace(UUID.randomUUID(), SnapshotFixtures.snapshot(7, 1));
        var aborted = new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(invocation -> {
            Object result = mockingDetails(categories).getMockCreationSettings().getDefaultAnswer().answer(invocation);
            // Aborta la conexión real de la transacción después de insertar categorías nuevas.
            DataSourceUtils.getConnection(dataSource).abort(Runnable::run);
            aborted.set(true);
            return result;
        }).when(categories).saveAllAndFlush(anyList());
        UUID runId = UUID.randomUUID();
        CatalogSnapshot candidate = SnapshotFixtures.snapshot(8, 2);
        assertThatThrownBy(() -> repository.replace(runId, candidate)).isInstanceOf(CatalogSnapshotException.class);
        assertThat(aborted).isTrue();
        assertCopy(7, 1);
    }

    @Test
    void translatesFailureAfterRealCommitAndReconcilesWithoutWritingTwice() {
        // La frontera devuelve un error después de que el proxy real haya confirmado.
        var boundary = mock(TransactionalCatalogSnapshotWriter.class, delegatesTo(writer));
        doAnswer(invocation -> {
            writer.replace(invocation.getArgument(0), invocation.getArgument(1));
            throw new TransactionSystemException("fictional-lost-confirmation");
        }).when(boundary).replace(any(UUID.class), any(CatalogSnapshot.class));
        CatalogSnapshotRepository uncertain = new JpaCatalogSnapshotAdapter(boundary);
        var gateway = mock(CatalogSnapshotGateway.class);
        when(gateway.fetch("fictional-access")).thenReturn(SnapshotFixtures.snapshot(7, 1));
        var access = mock(TechnicalAccessGateway.class);
        when(access.getToken()).thenReturn("fictional-access");
        var cycle = new SynchronizeCatalogUseCaseImpl(gateway, uncertain, access, Clock.systemUTC(), Duration.ZERO, new java.util.concurrent.locks.ReentrantLock());

        assertThat(cycle.synchronize().phase()).isEqualTo(SynchronizationPhase.SNAPSHOT_APPLIED);
        assertCopy(7, 1);
        verify(boundary).replace(any(UUID.class), any(CatalogSnapshot.class));
        verify(gateway).fetch("fictional-access");
        assertThat(repository.getState().phase()).isEqualTo(SynchronizationPhase.SNAPSHOT_APPLIED);
    }

    @Test
    void terminalReportingCannotOverwriteACommitThatFinishedMeanwhile() {
        UUID runId = UUID.randomUUID();
        repository.beginAttempt(runId, SnapshotFixtures.TIME);
        repository.replace(runId, SnapshotFixtures.snapshot(7, 1));
        CatalogState state = repository.finishAttempt(runId, SynchronizationPhase.FAILED, "SIMULATED_TIMEOUT");
        assertThat(state.phase()).isEqualTo(SynchronizationPhase.SNAPSHOT_APPLIED);
        assertCopy(7, 1);
    }

    @Test
    void confirmedCopySurvivesClosingAndReopeningApplicationContexts() {
        String[] args = {
            "--DB_HOST=" + MYSQL.getHost(), "--DB_PORT=" + MYSQL.getFirstMappedPort(),
            "--DB_NAME=" + MYSQL.getDatabaseName(), "--DB_USER=" + MYSQL.getUsername(),
            "--DB_PASSWORD=" + MYSQL.getPassword()
        };
        try (var first = new SpringApplicationBuilder(PersistenceConfiguration.class)
                .profiles("snapshot-persistence-test").web(WebApplicationType.NONE).run(args)) {
            first.getBean(CatalogSnapshotRepository.class).replace(UUID.randomUUID(), SnapshotFixtures.snapshot(7, 1));
        }
        try (var second = new SpringApplicationBuilder(PersistenceConfiguration.class)
                .profiles("snapshot-persistence-test").web(WebApplicationType.NONE).run(args)) {
            CatalogState restored = second.getBean(CatalogSnapshotRepository.class).getState();
            assertThat(restored.hasCopy()).isTrue();
            assertThat(restored.appliedVersion()).isEqualTo(7);
            assertThat(second.getBean(JpaProfessionalCategoryRepository.class).findById(1L))
                    .isPresent();
        }
    }

    private void assertCopy(long version, long categoryId) {
        assertThat(repository.getState().appliedVersion()).isEqualTo(version);
        assertThat(jdbc.queryForList("SELECT id FROM professional_category", Long.class)).containsExactly(categoryId);
        assertThat(jdbc.queryForList("SELECT id FROM professional", Long.class)).containsExactly(categoryId + 100);
        assertThat(jdbc.queryForList("SELECT id FROM weekly_schedule", Long.class)).containsExactly(categoryId + 200);
    }

    private static long readVersion(Connection connection) throws Exception {
        try (var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT applied_version FROM catalog_sync_state WHERE id = 1")) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Profile("snapshot-persistence-test")
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = CatalogSyncStateEntity.class)
    @EnableJpaRepositories(basePackageClasses = JpaCatalogSyncStateRepository.class)
    @Import({TransactionalCatalogSnapshotWriter.class, JpaCatalogSnapshotAdapter.class,
            CatalogSnapshotPersistenceMapper.class})
    static class PersistenceConfiguration {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
