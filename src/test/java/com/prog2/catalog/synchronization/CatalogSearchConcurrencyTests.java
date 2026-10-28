package com.prog2.catalog.synchronization;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.*;
import org.springframework.http.HttpHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;
import com.prog2.catalog.security.JwtTestTokens;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Intercalados deterministas con SQL y escritores reales; los spies solo detienen fronteras. */
@Testcontainers
@SpringBootTest(classes = CatalogQueryTestConfiguration.class)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("catalog-query-test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@MockitoSpyBean(types = CatalogSnapshotPersistenceMapper.class)
@Timeout(30)
class CatalogSearchConcurrencyTests {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.8")
            .withDatabaseName("search_concurrency").withUsername("search_test").withPassword("fictional-search-password");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("DB_HOST", MYSQL::getHost); registry.add("DB_PORT", MYSQL::getFirstMappedPort);
        registry.add("DB_NAME", MYSQL::getDatabaseName); registry.add("DB_USER", MYSQL::getUsername);
        registry.add("DB_PASSWORD", MYSQL::getPassword);
        registry.add("JWT_PUBLIC_KEY_PATH", JwtTestTokens.PUBLIC_KEY_PATH::toString);
    }
    private final CatalogQueryTestData data;
    private final CatalogQueryRepository repository;
    private final SearchProfessionalsUseCase search;
    private final TechnicalAccessGateway access;
    private final CatalogSnapshotPersistenceMapper mapper;
    private final MockMvc mvc;

    CatalogSearchConcurrencyTests(CatalogQueryTestData data, CatalogQueryRepository repository,
            SearchProfessionalsUseCase search, TechnicalAccessGateway access,
            CatalogSnapshotPersistenceMapper mapper, MockMvc mvc) {
        this.data = data; this.repository = repository; this.search = search;
        this.access = access; this.mapper = mapper; this.mvc = mvc;
    }

    @BeforeEach void setup() { CatalogSearchFixtures.install(data); }
    @AfterEach void clearInterleaving() { reset(mapper); }

    @Test void pageCategoriesAndCountShareTheSnapshotOfTheFirstSelect() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        pauseReader(entered, release);
        try (var executor = readerExecutor()) {
            var reading = executor.submit(() -> repository.search(CatalogSearchFixtures.all(0, 2)));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                data.snapshots.replace(UUID.randomUUID(), replacement());
            } finally { release.countDown(); }
            var previous = reading.get(5, TimeUnit.SECONDS);
            assertThat(previous.catalog().appliedVersion()).isEqualTo(7);
            assertThat(previous.totalElements()).isEqualTo(6);
            assertThat(previous.content()).extracting(entry -> entry.professional().id()).containsExactly(103L, 101L);
            assertThat(previous.content()).extracting(entry -> entry.category().name()).containsOnly("Clínica");
            var next = repository.search(CatalogSearchFixtures.all(0, 2));
            assertThat(next.catalog().appliedVersion()).isEqualTo(8);
            assertThat(next.totalElements()).isEqualTo(1);
            assertThat(next.content().getFirst().category().name()).isEqualTo("Nueva");
        }
    }

    @Test void categoryOptionsAlsoKeepTheMetadataGenerationAcrossCommit() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        pauseReader(entered, release);
        try (var executor = readerExecutor()) {
            var reading = executor.submit(repository::readCategories);
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                data.snapshots.replace(UUID.randomUUID(), replacement());
            } finally { release.countDown(); }
            var previous = reading.get(5, TimeUnit.SECONDS);
            assertThat(previous.catalog().appliedVersion()).isEqualTo(7);
            assertThat(previous.content()).extracting(ProfessionalCategory::id).containsExactly(2L, 1L, 3L);
            assertThat(repository.readCategories().content()).extracting(ProfessionalCategory::name).containsExactly("Nueva");
        }
    }

    @ParameterizedTest @ValueSource(strings = {"snapshot", "increment"})
    void bothGetsReadThePreviousCopyBeforeTheWriterIsReleased(String kind) throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        pauseWriter(entered, release);
        var before = data.persisted();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var writing = executor.submit(() -> write(kind));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                user("/api/catalog/professionals").andExpect(status().isOk())
                        .andExpect(jsonPath("$.totalElements").value(6))
                        .andExpect(jsonPath("$.catalog.state").value("SYNCHRONIZING"))
                        .andExpect(jsonPath("$.content[0].category.name").value("Clínica"));
                user("/api/catalog/categories").andExpect(status().isOk())
                        .andExpect(jsonPath("$.content.length()").value(3))
                        .andExpect(jsonPath("$.catalog.state").value("SYNCHRONIZING"));
                // El escritor puede confirmar su fase antes de la transacción de datos; se observan las filas de negocio.
                assertThat(data.jdbc.queryForObject("SELECT COUNT(*) FROM professional", Long.class)).isEqualTo(6);
                assertThat(data.persisted().subList(1, 4)).isEqualTo(before.subList(1, 4));
            } finally { release.countDown(); }
            assertThat(writing.get(5, TimeUnit.SECONDS)).isIn(SynchronizationPhase.SNAPSHOT_APPLIED, SynchronizationPhase.CURRENT);
            assertThat(repository.readState().appliedVersion()).isEqualTo(8);
        }
    }

    @Test void anUnconfirmedFirstSnapshotDoesNotExposeItsUncommittedRows() throws Exception {
        data.guard.requireVerification();
        data.jdbc.update("UPDATE catalog_sync_state SET applied_version = NULL WHERE id = 1");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        pauseWriter(entered, release);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var writing = executor.submit(() -> write("snapshot"));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                for (String path : List.of("/api/catalog/professionals", "/api/catalog/categories")) {
                    user(path).andExpect(status().isServiceUnavailable())
                            .andExpect(jsonPath("$.code").value("CATALOG_COPY_UNAVAILABLE"));
                }
            } finally { release.countDown(); }
            assertThat(writing.get(5, TimeUnit.SECONDS)).isEqualTo(SynchronizationPhase.SNAPSHOT_APPLIED);
        }
    }

    @Test void initialBrowsingDoesNotBlockStartupAndARecreatedGuardDoesNotTrustOldCurrent() throws Exception {
        data.guard.requireVerification();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        pauseReader(entered, release);
        try (var executor = readerExecutor()) {
            var reading = executor.submit(() -> search.search(CatalogSearchFixtures.all(0, 2)));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(write("snapshot")).isEqualTo(SynchronizationPhase.SNAPSHOT_APPLIED);
            } finally { release.countDown(); }
            var result = reading.get(5, TimeUnit.SECONDS);
            assertThat(result.totalElements()).isEqualTo(6);
            assertThat(result.catalog().state()).isNotEqualTo(CatalogAvailability.CURRENT);
        }
        data.updates.checked(new CatalogPublication(8, 1, data.clock.instant()));
        var newGuard = new CatalogSynchronizationGuard();
        var restarted = new SearchProfessionalsUseCaseImpl(repository, access, newGuard, new CatalogStatusPolicy(data.clock));
        assertThat(restarted.search(CatalogSearchFixtures.all(0, 20)).catalog().state()).isEqualTo(CatalogAvailability.STALE);
        var check = new UpdateCatalogUseCaseImpl(version -> new CatalogChangesBatch(version,
                new CatalogPublication(version, 1, data.clock.instant()), List.of(), List.of(), List.of()),
                data.updates, recovery(), access, data.clock, Duration.ZERO, newGuard);
        assertThat(check.check().phase()).isEqualTo(SynchronizationPhase.CURRENT);
        assertThat(restarted.search(CatalogSearchFixtures.all(0, 20)).catalog().state()).isEqualTo(CatalogAvailability.CURRENT);
    }

    private org.springframework.test.web.servlet.ResultActions user(String path) throws Exception {
        return mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + JwtTestTokens.signed(JwtTestTokens.claims("user"))));
    }

    private CatalogSnapshot replacement() {
        var time = data.clock.instant();
        return new CatalogSnapshot(8, time,
                List.of(new ProfessionalCategory(1, "Nueva", null, true, time, time)),
                List.of(new Professional(101, 1, "Changed", "Zed", true, time, time)), List.of());
    }

    private SynchronizationPhase write(String kind) {
        var copy = replacement();
        if ("snapshot".equals(kind)) {
            return new SynchronizeCatalogUseCaseImpl(_ -> copy, data.snapshots, access, data.clock,
                    Duration.ZERO, data.guard).synchronize().phase();
        }
        var changes = new CatalogChangesBatch(7, new CatalogPublication(8, 1, data.clock.instant()),
                copy.professionalCategories(), copy.professionals(), copy.weeklySchedules());
        return new UpdateCatalogUseCaseImpl(_ -> changes, data.updates, recovery(), access, data.clock,
                Duration.ZERO, data.guard).check().phase();
    }

    private static RecoverCatalogUseCase recovery() {
        var recovery = mock(RecoverCatalogUseCase.class);
        when(recovery.resume()).thenReturn(Optional.empty());
        return recovery;
    }

    private static ExecutorService readerExecutor() {
        return Executors.newSingleThreadExecutor(Thread.ofPlatform().name("search-reader").factory());
    }

    private void pauseReader(CountDownLatch entered, CountDownLatch release) {
        doAnswer(call -> {
            Object state = call.callRealMethod();
            if (Thread.currentThread().getName().equals("search-reader")) {
                entered.countDown();
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            }
            return state;
        }).when(mapper).toDomain(any(CatalogSyncStateEntity.class));
    }

    private void pauseWriter(CountDownLatch entered, CountDownLatch release) {
        var first = new AtomicBoolean();
        doAnswer(call -> {
            Object entity = call.callRealMethod();
            if (first.compareAndSet(false, true)) {
                entered.countDown();
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            }
            return entity;
        }).when(mapper).toEntity(any(Professional.class));
    }
}

