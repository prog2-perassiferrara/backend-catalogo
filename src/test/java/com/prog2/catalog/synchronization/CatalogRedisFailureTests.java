package com.prog2.catalog.synchronization;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.dao.*;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;
import com.prog2.catalog.synchronization.domain.enums.SnapshotFailureKind;
import com.prog2.catalog.synchronization.domain.exception.CatalogSnapshotException;
import com.prog2.catalog.synchronization.infrastructure.client.mapper.CatalogSnapshotResponseMapper;
import com.prog2.catalog.synchronization.infrastructure.redis.adapter.RedisCatalogChangesAdapter;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Ejecuta el adaptador real y controla solo la frontera que entrega errores del driver. */
@Timeout(5)
class CatalogRedisFailureTests {
    private final StringRedisTemplate template = mock(StringRedisTemplate.class);
    private final JsonMapper json = JsonMapper.builder().build();

    private RedisCatalogChangesAdapter reader() {
        return new RedisCatalogChangesAdapter(() -> template, () -> "catedra:sync:*", json,
                new CatalogSnapshotResponseMapper(json), Executors.newSingleThreadExecutor(), Duration.ofSeconds(2), 8192);
    }

    static Stream<Arguments> failures() {
        return Stream.of(
                Arguments.of(new RedisConnectionFailureException("fictional-network"), SnapshotFailureKind.TEMPORARY),
                Arguments.of(new QueryTimeoutException("fictional-timeout"), SnapshotFailureKind.TEMPORARY),
                Arguments.of(new InvalidDataAccessApiUsageException("fictional-command"), SnapshotFailureKind.TERMINAL),
                Arguments.of(new IllegalStateException("fictional-unexpected"), SnapshotFailureKind.TERMINAL),
                accessFailure("WRONGPASS"), accessFailure("NOAUTH"), accessFailure("NOPERM"));
    }

    private static Arguments accessFailure(String code) {
        return Arguments.of(new RedisConnectionFailureException("fictional-wrapper",
                new IllegalStateException(code + " fictional-redis-sensitive")), SnapshotFailureKind.TERMINAL);
    }

    @ParameterizedTest
    @MethodSource("failures")
    void classifiesDriverFailuresWithoutLeakingTheirMessages(RuntimeException cause, SnapshotFailureKind kind) {
        when(template.opsForHash()).thenThrow(cause);
        try (var reader = reader()) {
            assertThatThrownBy(() -> reader.readSince(7)).isInstanceOfSatisfying(CatalogSnapshotException.class, failure -> {
                assertThat(failure.kind()).isEqualTo(kind);
                assertThat(failure.getMessage()).startsWith("CATALOG_REDIS_").doesNotContain("fictional-");
                assertThat(failure.getCause()).isNull();
            });
        }
    }

    @Test void callerInterruptionCancelsTheRequestAndPreservesItsInterruptFlag() {
        CountDownLatch blocked = new CountDownLatch(1);
        try (var reader = new RedisCatalogChangesAdapter(() -> {
            try { blocked.await(); }
            catch (InterruptedException _) { Thread.currentThread().interrupt(); }
            return template;
        }, () -> "catedra:sync:*", json, new CatalogSnapshotResponseMapper(json),
                Executors.newSingleThreadExecutor(), Duration.ofSeconds(2), 8192)) {
            Thread.currentThread().interrupt();
            assertThatThrownBy(() -> reader.readSince(7)).isInstanceOfSatisfying(CatalogSnapshotException.class,
                    failure -> assertThat(failure.getMessage()).isEqualTo("CATALOG_READ_INTERRUPTED"));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
            blocked.countDown();
        }
    }
}
