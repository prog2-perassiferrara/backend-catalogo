package com.prog2.catalog.synchronization;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;
import com.prog2.catalog.security.JwtTestTokens;
import com.prog2.catalog.synchronization.domain.model.*;
import com.prog2.catalog.synchronization.domain.ports.out.CatalogQueryRepository;
import static org.assertj.core.api.Assertions.assertThat;

/** Prueba filtros, totales y collation ejecutando Criteria contra MySQL, sin simular resultados. */
@Testcontainers
@SpringBootTest(classes = CatalogQueryTestConfiguration.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("catalog-query-test")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Timeout(30)
class CatalogSearchPersistenceTests {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.8")
            .withDatabaseName("search_persistence").withUsername("search_test").withPassword("fictional-search-password");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("DB_HOST", MYSQL::getHost); registry.add("DB_PORT", MYSQL::getFirstMappedPort);
        registry.add("DB_NAME", MYSQL::getDatabaseName); registry.add("DB_USER", MYSQL::getUsername);
        registry.add("DB_PASSWORD", MYSQL::getPassword);
        registry.add("JWT_PUBLIC_KEY_PATH", JwtTestTokens.PUBLIC_KEY_PATH::toString);
    }
    private final CatalogQueryRepository repository;
    private final CatalogQueryTestData data;
    private final EntityManagerFactory entityManagerFactory;

    CatalogSearchPersistenceTests(CatalogQueryRepository repository, CatalogQueryTestData data,
            EntityManagerFactory entityManagerFactory) {
        this.repository = repository; this.data = data; this.entityManagerFactory = entityManagerFactory;
    }

    @BeforeEach void setup() { CatalogSearchFixtures.install(data); }

    static Stream<Arguments> filters() {
        return Stream.of(
                row(null, null, null, null, 103, 101, 102, 106, 104, 105),
                row(1L, null, null, null, 103, 101, 106, 105),
                row(2L, null, true, null, 102),
                row(999L, null, null, null),
                row(null, "ana go", null, null, 101, 102),
                row(null, "ÁNA GÓ", null, null, 101, 102),
                row(null, null, false, null, 103, 104),
                row(null, null, true, null, 101, 102, 106, 105),
                row(null, null, null, "2026-11-02", 101, 104),
                row(null, null, null, "2026-11-03", 102),
                row(null, null, null, "2026-11-04"),
                row(1L, "Ana", true, "2026-11-02", 101),
                row(null, "%", null, null, 105), row(null, "_", null, null, 105),
                row(null, "!", null, null, 105), row(null, "%_!", null, null, 105),
                row(null, "' OR 1=1 --", null, null));
    }

    private static Arguments row(Long category, String name, Boolean enabled, String date, long... ids) {
        return Arguments.of(new ProfessionalSearchCriteria(category, name, enabled,
                date == null ? null : LocalDate.parse(date), 0, 20), ids);
    }

    @ParameterizedTest @MethodSource("filters")
    void filtersAreCombinedInSqlAndSchedulesNeverMultiplyResults(ProfessionalSearchCriteria criteria, long[] expected) {
        var before = data.persisted();
        var result = repository.search(criteria);
        assertThat(result.content()).extracting(entry -> entry.professional().id())
                .containsExactly(Arrays.stream(expected).boxed().toArray(Long[]::new));
        assertThat(result.totalElements()).isEqualTo(expected.length);
        assertThat(result.catalog().appliedVersion()).isEqualTo(7);
        assertThat(data.persisted()).isEqualTo(before);
    }

    @Test void paginationUsesAStableTieBreakerAndLoadsCategoriesInOneBatch() {
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var first = repository.search(CatalogSearchFixtures.all(0, 2));
        assertThat(first.totalElements()).isEqualTo(6);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(4);
        var ids = new ArrayList<Long>();
        for (int page = 0; page < 3; page++) {
            var result = repository.search(CatalogSearchFixtures.all(page, 2));
            assertThat(result.totalElements()).isEqualTo(6);
            ids.addAll(result.content().stream().map(entry -> entry.professional().id()).toList());
        }
        assertThat(ids).containsExactly(103L, 101L, 102L, 106L, 104L, 105L);
        var afterLast = repository.search(CatalogSearchFixtures.all(3, 2));
        assertThat(afterLast.content()).isEmpty();
        assertThat(afterLast.totalElements()).isEqualTo(6);
        assertThat(first.content()).extracting(entry -> entry.category().name()).containsExactly("Clínica", "Clínica");
    }

    @Test void nameComparisonDoesNotDependOnCaseInsensitiveColumnDefaults() {
        data.jdbc.execute("""
                ALTER TABLE professional
                MODIFY first_name LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs NOT NULL,
                MODIFY last_name LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs NOT NULL
                """);
        try {
            var result = repository.search(new ProfessionalSearchCriteria(null, "ANA GO", null, null, 0, 20));
            assertThat(result.content()).extracting(entry -> entry.professional().id()).containsExactlyInAnyOrder(101L, 102L);
        } finally {
            data.jdbc.execute("""
                    ALTER TABLE professional
                    MODIFY first_name LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
                    MODIFY last_name LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL
                    """);
        }
    }

    @Test void categoriesKeepDisabledOptionsAndEmptyConfirmedCatalogIsLegitimate() {
        var result = repository.readCategories();
        assertThat(result.content()).extracting(ProfessionalCategory::id).containsExactly(2L, 1L, 3L);
        assertThat(result.content().getFirst().enabled()).isFalse();
        data.snapshots.replace(UUID.randomUUID(), new CatalogSnapshot(8, data.clock.instant(), List.of(), List.of(), List.of()));
        assertThat(repository.search(CatalogSearchFixtures.all(0, 20)).content()).isEmpty();
        assertThat(repository.readCategories().catalog().hasCopy()).isTrue();
        assertThat(repository.readCategories().content()).isEmpty();
    }
}

