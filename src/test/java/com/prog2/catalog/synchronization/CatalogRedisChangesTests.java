package com.prog2.catalog.synchronization;

import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import tools.jackson.databind.json.JsonMapper;
import com.prog2.catalog.synchronization.domain.enums.*;
import com.prog2.catalog.synchronization.domain.exception.*;
import com.prog2.catalog.synchronization.infrastructure.client.mapper.CatalogSnapshotResponseMapper;
import com.prog2.catalog.synchronization.infrastructure.redis.adapter.RedisCatalogChangesAdapter;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
@Timeout(20)
class CatalogRedisChangesTests {
    @Container static final GenericContainer<?> REDIS=new GenericContainer<>("redis:8.4.0-alpine")
            .withExposedPorts(6379).withCommand("redis-server","--requirepass",IncrementalFixtures.ACCESS);
    private LettuceConnectionFactory factory;
    private StringRedisTemplate redisTemplate;
    private final JsonMapper json=JsonMapper.builder().build();
    @BeforeEach void setup() {
        factory=IncrementalFixtures.factory(REDIS); redisTemplate=new StringRedisTemplate(factory);
        IncrementalFixtures.publish(redisTemplate,10,1);
    }
    @AfterEach void close() { factory.destroy(); }
    private RedisCatalogChangesAdapter adapter(StringRedisTemplate source,int bytes) {
        return new RedisCatalogChangesAdapter(() -> source,() -> "catedra:sync:*",json,
                new CatalogSnapshotResponseMapper(json),Executors.newSingleThreadExecutor(),Duration.ofSeconds(3),bytes);
    }

    @Test void readsTheWholeRangeAndDeduplicatesIdsWithoutDiscardingDisabledRows() {
        try(var adapter=adapter(redisTemplate,8192)) {
            var batch=adapter.readSince(7);
            assertThat(batch.baseVersion()).isEqualTo(7);
            assertThat(batch.publication().currentVersion()).isEqualTo(10);
            assertThat(batch.categories()).hasSize(1);
            assertThat(batch.categories().getFirst().enabled()).isFalse();
            assertThat(batch.professionals().getFirst().firstName()).isEqualTo("Updated 10");
            assertThat(batch.schedules().getFirst().startTime()).hasHour(9).hasMinute(0);
            assertThat(batch.schedules().getFirst().endTime()).hasHour(12);
        }
    }

    @Test void acceptsAnUnchangedEmptyRangeAndTheOldestAvailableBoundary() {
        try(var adapter=adapter(redisTemplate,8192)) {
            assertThat(adapter.readSince(10).professionals()).isEmpty();
            IncrementalFixtures.publish(redisTemplate,10,7);
            assertThat(adapter.readSince(7).publication().currentVersion()).isEqualTo(10);
        }
    }

    @ParameterizedTest
    @CsvSource({"3,4,HISTORY_UNAVAILABLE","7,1,CHANGE_MISSING"})
    void refusesUnavailableHistory(long base,long oldest,CatalogRecoveryReason reason) {
        IncrementalFixtures.publish(redisTemplate,10,oldest);
        if(reason==CatalogRecoveryReason.CHANGE_MISSING) { redisTemplate.delete("catedra:sync:changes:8"); }
        try(var adapter=adapter(redisTemplate,8192)) {
            assertThatThrownBy(() -> adapter.readSince(base)).isInstanceOfSatisfying(CatalogDiscontinuityException.class,
                    failure -> assertThat(failure.reason()).isEqualTo(reason));
        }
    }

    @Test void rejectsMissingEntitiesAndWrongJsonTypesInsteadOfOmittingThem() {
        redisTemplate.opsForHash().delete("catedra:sync:professionals","101");
        try(var adapter=adapter(redisTemplate,8192)) {
            assertThatThrownBy(() -> adapter.readSince(7)).isInstanceOf(CatalogDiscontinuityException.class);
            redisTemplate.opsForHash().put("catedra:sync:professionals","101","{\"id\":\"101\"}");
            assertThatThrownBy(() -> adapter.readSince(7)).isInstanceOf(CatalogDiscontinuityException.class);
            redisTemplate.opsForValue().set("catedra:sync:changes:8","{}");
            assertThatThrownBy(() -> adapter.readSince(7)).isInstanceOf(CatalogDiscontinuityException.class);
        }
    }

    @Test void detectsPublicationChangingAfterTheFirstMetadataRead() {
        StringRedisTemplate observed=spy(redisTemplate);
        ValueOperations<String,String> values=spy(redisTemplate.opsForValue());
        doReturn(values).when(observed).opsForValue();
        doAnswer(call -> {
            String previous=redisTemplate.opsForValue().get("catedra:sync:changes:8");
            IncrementalFixtures.publish(redisTemplate,11,1);
            return previous;
        }).when(values).get("catedra:sync:changes:8");
        try(var adapter=adapter(observed,8192)) {
            assertThatThrownBy(() -> adapter.readSince(7)).isInstanceOfSatisfying(CatalogDiscontinuityException.class,
                    failure -> assertThat(failure.reason()).isEqualTo(CatalogRecoveryReason.PUBLICATION_CHANGED));
        }
    }

    @Test void boundsTheCandidateAndTheWholeRequestAndCancelsItsReader() throws Exception {
        try(var adapter=adapter(redisTemplate,16)) {
            assertThatThrownBy(() -> adapter.readSince(7)).isInstanceOfSatisfying(CatalogDiscontinuityException.class,
                    failure -> assertThat(failure.reason()).isEqualTo(CatalogRecoveryReason.READ_LIMIT_EXCEEDED));
        }
        CountDownLatch blocked=new CountDownLatch(1);
        CountDownLatch interrupted=new CountDownLatch(1);
        try(var adapter=new RedisCatalogChangesAdapter(() -> {
            try { blocked.await(); }
            catch(InterruptedException _) { interrupted.countDown(); Thread.currentThread().interrupt(); }
            return redisTemplate;
        },() -> "catedra:sync",json,new CatalogSnapshotResponseMapper(json),
                Executors.newSingleThreadExecutor(),Duration.ofMillis(100),8192)) {
            assertThatThrownBy(() -> adapter.readSince(7)).isInstanceOfSatisfying(CatalogSnapshotException.class,
                    failure -> assertThat(failure.kind()).isEqualTo(SnapshotFailureKind.TEMPORARY));
            assertThat(interrupted.await(2,TimeUnit.SECONDS)).isTrue();
        } finally { blocked.countDown(); }
    }
}
