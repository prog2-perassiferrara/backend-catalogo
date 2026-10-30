package com.prog2.catalog.synchronization;

import com.prog2.catalog.synchronization.application.usecases.UpdateCatalogUseCaseImpl.IncrementalRetrySettings;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;
import com.prog2.catalog.security.JwtTestTokens;
import com.prog2.catalog.synchronization.application.exception.CatalogQueryException;
import com.prog2.catalog.synchronization.application.service.*;
import com.prog2.catalog.synchronization.application.usecases.*;
import com.prog2.catalog.synchronization.domain.enums.*;
import com.prog2.catalog.synchronization.domain.model.*;
import com.prog2.catalog.synchronization.domain.ports.in.*;
import com.prog2.catalog.synchronization.domain.ports.out.*;
import com.prog2.catalog.synchronization.infrastructure.persistence.entity.CatalogSyncStateEntity;
import com.prog2.catalog.synchronization.infrastructure.persistence.mapper.CatalogSnapshotPersistenceMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Lectores y escritores SQL reales; los spies solo detienen fronteras para ordenar los intercalados. */
@Testcontainers
@SpringBootTest(classes = CatalogQueryTestConfiguration.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("catalog-query-test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@MockitoSpyBean(types = CatalogSnapshotPersistenceMapper.class)
@Timeout(30)
class CatalogQueryConcurrencyTests {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.8")
            .withDatabaseName("query_concurrency").withUsername("query_test").withPassword("fictional-query-password");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("DB_HOST", MYSQL::getHost); registry.add("DB_PORT", MYSQL::getFirstMappedPort);
        registry.add("DB_NAME", MYSQL::getDatabaseName); registry.add("DB_USER", MYSQL::getUsername);
        registry.add("DB_PASSWORD", MYSQL::getPassword);
        registry.add("JWT_PUBLIC_KEY_PATH", JwtTestTokens.PUBLIC_KEY_PATH::toString);
    }
    private final CatalogQueryTestData data;
    private final CatalogQueryRepository repository;
    private final GetProfessionalAgendaUseCase agendas;
    private final GetCatalogStatusUseCase statuses;
    private final TechnicalAccessGateway access;
    private final CatalogSnapshotPersistenceMapper mapper;
    CatalogQueryConcurrencyTests(CatalogQueryTestData data, CatalogQueryRepository repository,
            GetProfessionalAgendaUseCase agendas, GetCatalogStatusUseCase statuses,
            TechnicalAccessGateway access, CatalogSnapshotPersistenceMapper mapper) {
        this.data = data; this.repository = repository; this.agendas = agendas;
        this.statuses = statuses; this.access = access; this.mapper = mapper;
    }
    @BeforeEach void setup() { data.reset(); }
    @AfterEach void removeInterleaving() { reset(mapper); }

    @Test void mysqlReaderKeepsOneCopyAcrossARealSnapshotCommitBetweenSelects() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        pauseAfterState(entered, release);
        try (var executor = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("query-reader").factory())) {
            var reading = executor.submit(() -> repository.readProfessional(101));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                data.snapshots.replace(UUID.randomUUID(), CatalogQueryTestData.snapshot(8, "Updated", 45));
            } finally { release.countDown(); }
            CatalogReadView old = reading.get(5, TimeUnit.SECONDS);
            assertThat(old.catalog().appliedVersion()).isEqualTo(7);
            assertThat(old.professional().orElseThrow().firstName()).isEqualTo("Fictional");
            assertThat(old.weeklySchedules()).allSatisfy(v -> assertThat(v.slotDurationMinutes()).isEqualTo(30));
            CatalogReadView next = repository.readProfessional(101);
            assertThat(next.catalog().appliedVersion()).isEqualTo(8);
            assertThat(next.professional().orElseThrow().firstName()).isEqualTo("Updated");
            assertThat(next.weeklySchedules()).allSatisfy(v -> assertThat(v.slotDurationMinutes()).isEqualTo(45));
        }
    }

    @Test void twoRealQueriesCoexistWhileAnExclusiveCycleCannotEnter() throws Exception {
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        pauseAfterState(entered, release);
        try (var executor = Executors.newFixedThreadPool(2, Thread.ofPlatform().name("query-reader").factory())) {
            var first = executor.submit(() -> agendas.get(101));
            var second = executor.submit(() -> agendas.get(101));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(data.guard.trySynchronize()).isFalse();
            } finally { release.countDown(); }
            assertThat(first.get(5, TimeUnit.SECONDS).catalog().appliedVersion()).isEqualTo(7);
            assertThat(second.get(5, TimeUnit.SECONDS).catalog().appliedVersion()).isEqualTo(7);
            assertThat(data.guard.trySynchronize()).isTrue();
            data.guard.endSynchronization();
        }
    }

    @ParameterizedTest @ValueSource(strings = {"snapshot", "increment"})
    void queriesDuringARealWriteRemainNonBlockingAndNeverExposePartialData(String kind) throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var once = new AtomicBoolean();
        doAnswer(call -> {
            Object entity = call.callRealMethod();
            if (once.compareAndSet(false, true)) {
                entered.countDown();
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            }
            return entity;
        }).when(mapper).toEntity(any(Professional.class));
        try (var executor = Executors.newSingleThreadExecutor()) {
            var writing = executor.submit(() -> write(kind));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> agendas.get(101)).isInstanceOfSatisfying(CatalogQueryException.class,
                        failure -> assertThat(failure.reason()).isEqualTo(CatalogQueryFailure.CATALOG_NOT_CURRENT));
                CatalogStatus during = statuses.get();
                assertThat(during.state()).isEqualTo(CatalogAvailability.SYNCHRONIZING);
                assertThat(during.copyAvailable()).isTrue();
                assertThat(during.appliedVersion()).isEqualTo(7);
            } finally { release.countDown(); }
            assertThat(writing.get(5, TimeUnit.SECONDS)).isIn(SynchronizationPhase.SNAPSHOT_APPLIED, SynchronizationPhase.CURRENT);
            assertThat(repository.readProfessional(101).catalog().appliedVersion()).isEqualTo(8);
            if ("snapshot".equals(kind)) {
                assertThat(data.guard.verificationRequired()).isTrue();
            } else {
                assertThat(agendas.get(101).professional().firstName()).isEqualTo("Updated");
            }
        }
    }

    @Test void initialQueriesDoNotOccupyTheLockNeededForSnapshotOrTrustOldCurrent() throws Exception {
        data.guard.requireVerification();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        pauseAfterState(entered, release);
        try (var executor = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("query-reader").factory())) {
            var status = executor.submit(statuses::get);
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> agendas.get(101)).isInstanceOf(CatalogQueryException.class);
                assertThat(write("snapshot")).isEqualTo(SynchronizationPhase.SNAPSHOT_APPLIED);
            } finally { release.countDown(); }
            assertThat(status.get(5, TimeUnit.SECONDS).state()).isNotEqualTo(CatalogAvailability.CURRENT);
        }
        var freshGuard = new CatalogSynchronizationGuard();
        var policy = new CatalogStatusPolicy(data.clock);
        var restarted = new GetProfessionalAgendaUseCaseImpl(repository, access, freshGuard, policy);
        assertThatThrownBy(() -> restarted.get(101)).isInstanceOf(CatalogQueryException.class);
        var check = new UpdateCatalogUseCaseImpl(version -> new CatalogChangesBatch(version,
                new CatalogPublication(version, 1, data.clock.instant()), List.of(), List.of(), List.of()), data.updates, recovery(), access, data.clock, new IncrementalRetrySettings(3, Duration.ZERO, Duration.ofSeconds(30)), freshGuard);
        assertThat(check.check().phase()).isEqualTo(SynchronizationPhase.CURRENT);
        assertThat(restarted.get(101).catalog().appliedVersion()).isEqualTo(8);
    }

    private SynchronizationPhase write(String kind) {
        var snapshot = CatalogQueryTestData.snapshot(8, "Updated", 45);
        if ("snapshot".equals(kind)) {
            return new SynchronizeCatalogUseCaseImpl(_ -> snapshot, data.snapshots, access, data.clock, 3, Duration.ZERO, data.guard).synchronize().phase();
        }
        var batch = new CatalogChangesBatch(7, new CatalogPublication(8, 1, data.clock.instant()),
                snapshot.professionalCategories(), snapshot.professionals(), snapshot.weeklySchedules());
        return new UpdateCatalogUseCaseImpl(_ -> batch, data.updates, recovery(), access, data.clock, new IncrementalRetrySettings(3, Duration.ZERO, Duration.ofSeconds(30)), data.guard).check().phase();
    }

    private static RecoverCatalogUseCase recovery() {
        var recovery = mock(RecoverCatalogUseCase.class);
        when(recovery.resume()).thenReturn(Optional.empty());
        return recovery;
    }

    private void pauseAfterState(CountDownLatch entered, CountDownLatch release) {
        doAnswer(call -> {
            Object result = call.callRealMethod();
            if (Thread.currentThread().getName().equals("query-reader")) {
                entered.countDown();
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            }
            return result;
        }).when(mapper).toDomain(any(CatalogSyncStateEntity.class));
    }
}
