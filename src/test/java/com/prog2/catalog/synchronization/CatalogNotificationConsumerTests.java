package com.prog2.catalog.synchronization;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import com.prog2.catalog.synchronization.application.service.CatalogSynchronizationGuard;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.json.JsonMapper;
import com.prog2.catalog.infrastructure.config.CatalogRemoteClients;
import com.prog2.catalog.infrastructure.startup.CatalogSynchronizationMonitor;
import com.prog2.catalog.synchronization.application.usecases.UpdateCatalogUseCaseImpl;
import com.prog2.catalog.synchronization.domain.model.*;
import com.prog2.catalog.synchronization.domain.ports.in.*;
import com.prog2.catalog.synchronization.domain.ports.out.*;
import com.prog2.catalog.synchronization.infrastructure.client.mapper.CatalogSnapshotResponseMapper;
import com.prog2.catalog.synchronization.infrastructure.redis.adapter.RedisCatalogChangesAdapter;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest(classes=CatalogUpdateTestConfiguration.class, webEnvironment=SpringBootTest.WebEnvironment.NONE)
@TestConstructor(autowireMode=TestConstructor.AutowireMode.ALL)
@ActiveProfiles("update-persistence-test")
@Timeout(60)
class CatalogNotificationConsumerTests {
    @Container static final MySQLContainer MYSQL=new MySQLContainer("mysql:8.4.8")
            .withDatabaseName("consumer_test").withUsername("consumer_test").withPassword("fictional-consumer-password");
    @Container static final GenericContainer<?> REDIS=new GenericContainer<>("redis:8.4.0-alpine")
            .withExposedPorts(6379).withCommand("redis-server","--requirepass",IncrementalFixtures.ACCESS);
    @Container static final KafkaContainer KAFKA=new KafkaContainer("apache/kafka-native:4.2.1");
    @DynamicPropertySource static void settings(DynamicPropertyRegistry registry) {
        registry.add("DB_HOST",MYSQL::getHost); registry.add("DB_PORT",MYSQL::getFirstMappedPort);
        registry.add("DB_NAME",MYSQL::getDatabaseName); registry.add("DB_USER",MYSQL::getUsername);
        registry.add("DB_PASSWORD",MYSQL::getPassword);
    }
    private final CatalogSnapshotRepository snapshots;
    private final CatalogUpdateRepository updates;
    private final JdbcTemplate jdbc;
    private final JsonMapper json=JsonMapper.builder().build();
    private LettuceConnectionFactory redisFactory;
    private RedisCatalogChangesAdapter gateway;
    private CatalogRemoteClients remote;
    private AdminClient admin;
    private KafkaProducer<String,String> producer;
    private CatalogConnectionSettings settings;
    private final Clock clock=Clock.systemUTC();

    CatalogNotificationConsumerTests(CatalogSnapshotRepository snapshots,CatalogUpdateRepository updates,JdbcTemplate jdbc) {
        this.snapshots=snapshots; this.updates=updates; this.jdbc=jdbc;
    }

    @BeforeEach void setup() throws Exception {
        jdbc.update("DELETE FROM catalog_processed_event"); jdbc.update("DELETE FROM catalog_rejected_record");
        jdbc.update("UPDATE catalog_sync_state SET applied_version = NULL WHERE id = 1");
        snapshots.replace(UUID.randomUUID(),SnapshotFixtures.snapshot(7,1));
        redisFactory=IncrementalFixtures.factory(REDIS);
        var redis=new StringRedisTemplate(redisFactory);
        IncrementalFixtures.publish(redis,10,1);
        gateway=new RedisCatalogChangesAdapter(() -> redis,() -> "catedra:sync:*",json,
                new CatalogSnapshotResponseMapper(json),Executors.newSingleThreadExecutor(),Duration.ofSeconds(3),8192);
        String topic="fictional.catalog."+UUID.randomUUID();
        settings=new CatalogConnectionSettings(REDIS.getHost(),REDIS.getMappedPort(6379),"default",IncrementalFixtures.ACCESS,
                "catedra:sync:*",KAFKA.getBootstrapServers(),"fictional-group-"+UUID.randomUUID(),topic);
        admin=AdminClient.create(Map.of("bootstrap.servers",KAFKA.getBootstrapServers()));
        admin.createTopics(List.of(new NewTopic(topic,1,(short)1))).all().get(10,TimeUnit.SECONDS);
        producer=new KafkaProducer<>(Map.of("bootstrap.servers",KAFKA.getBootstrapServers(),"acks","all"),
                new StringSerializer(),new StringSerializer());
    }

    @AfterEach void close() {
        if(remote!=null) { remote.close(); }
        if(gateway!=null) { gateway.close(); }
        if(redisFactory!=null) { redisFactory.destroy(); }
        if(producer!=null) { producer.close(Duration.ofSeconds(5)); }
        if(admin!=null) { admin.close(Duration.ofSeconds(5)); }
    }

    private UpdateCatalogUseCase useCase(CatalogUpdateRepository repository) {
        var recovery=mock(RecoverCatalogUseCase.class);
        when(recovery.resume()).thenReturn(Optional.empty());
        return new UpdateCatalogUseCaseImpl(gateway,repository,recovery,mock(TechnicalAccessGateway.class),
                clock,Duration.ZERO,new CatalogSynchronizationGuard());
    }
    private void start(UpdateCatalogUseCase useCase) {
        remote=new CatalogRemoteClients(json,clock);
        remote.start(settings,useCase);
    }
    private void send(String body) throws Exception {
        producer.send(new ProducerRecord<>(settings.kafkaCatalogTopic(),"10",body)).get(10,TimeUnit.SECONDS);
    }
    private static String event(String id) {
        return """
                {"eventId":"%s","eventType":"CatalogUpdated","newVersion":10,
                "occurredAt":"2026-09-10T13:44:59Z","source":"MANUAL","schemaVersion":1,"extra":true}
                """.formatted(id);
    }
    private long offset() throws Exception {
        var offsets=admin.listConsumerGroupOffsets(settings.kafkaConsumerGroupId())
                .partitionsToOffsetAndMetadata().get(10,TimeUnit.SECONDS);
        var current=offsets.get(new TopicPartition(settings.kafkaCatalogTopic(),0));
        return current==null ? 0 : current.offset();
    }

    @Test void doesNotCommitKafkaWhileTheDatabaseUnitIsPending() throws Exception {
        var entered=new CountDownLatch(1);
        var release=new CountDownLatch(1);
        var repository=mock(CatalogUpdateRepository.class,delegatesTo(updates));
        doAnswer(call -> {
            entered.countDown();
            assertThat(release.await(10,TimeUnit.SECONDS)).isTrue();
            return updates.apply(call.getArgument(0),call.getArgument(1),call.getArgument(2));
        }).when(repository).apply(any(),any(),any());
        start(useCase(repository));
        try {
            send(event("after-commit"));
            assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            assertThat(offset()).isZero();
            assertThat(snapshots.getState().appliedVersion()).isEqualTo(7);
            assertThat(updates.isProcessed("after-commit")).isFalse();
        } finally { release.countDown(); }
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(offset()).isEqualTo(1);
            assertThat(updates.isProcessed("after-commit")).isTrue();
            assertThat(snapshots.getState().appliedVersion()).isEqualTo(10);
        });
        assertThat(jdbc.queryForObject("SELECT first_name FROM professional WHERE id = 101",String.class)).isEqualTo("Updated 10");
    }

    @Test void durableRejectionAllowsTheFollowingValidRecordToProgress() throws Exception {
        start(useCase(updates));
        send("not JSON"); send(event("after-invalid"));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(offset()).isEqualTo(2));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_rejected_record",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT record_offset FROM catalog_rejected_record",Long.class)).isZero();
        assertThat(updates.isProcessed("after-invalid")).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_processed_event",Integer.class)).isEqualTo(1);
    }

    @Test void failedRejectionDoesNotLetTheFollowingMessageSkipItsOffset() throws Exception {
        var repository = mock(CatalogUpdateRepository.class, delegatesTo(updates));
        var failed = new CountDownLatch(1);
        doAnswer(call -> {
            failed.countDown();
            throw new com.prog2.catalog.synchronization.domain.exception.CatalogSnapshotException(
                    com.prog2.catalog.synchronization.domain.enums.SnapshotFailureKind.TEMPORARY,
                    "FICTIONAL_REJECTION_STORAGE_UNAVAILABLE");
        }).doAnswer(call -> {
            updates.recordRejection(call.getArgument(0));
            return null;
        }).when(repository).recordRejection(any());
        start(useCase(repository));
        send("not JSON");
        send(event("after-rejection-retry"));
        assertThat(failed.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(offset()).isZero();
        assertThat(updates.isProcessed("after-rejection-retry")).isFalse();
        await().atMost(Duration.ofSeconds(40)).untilAsserted(() -> assertThat(offset()).isEqualTo(2));
        assertThat(updates.isProcessed("after-rejection-retry")).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_rejected_record", Integer.class)).isEqualTo(1);
    }

    @Test void replaysAfterCommitButBeforeAckWithoutApplyingTheDeltaTwice() throws Exception {
        UpdateCatalogUseCase actual=useCase(updates);
        UpdateCatalogUseCase crashed=mock(UpdateCatalogUseCase.class,delegatesTo(actual));
        var committed=new CountDownLatch(1);
        doAnswer(call -> {
            actual.process(call.getArgument(0));
            committed.countDown();
            throw new IllegalStateException("fictional-crash-before-ack");
        }).when(crashed).process(any());
        start(crashed);
        send(event("replayed"));
        assertThat(committed.await(10,TimeUnit.SECONDS)).isTrue();
        assertThat(updates.isProcessed("replayed")).isTrue();
        assertThat(offset()).isZero();
        UUID original=snapshots.getState().appliedRunId();
        remote.close();
        start(useCase(updates));
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(offset()).isEqualTo(1));
        assertThat(snapshots.getState().appliedRunId()).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_processed_event",Integer.class)).isEqualTo(1);
    }

    @Test void scheduledCheckUpdatesWithoutKafkaAndSurvivesAClientStartupFailure() {
        var clients=mock(CatalogRemoteClients.class);
        doThrow(new IllegalStateException("fictional-kafka-unavailable")).when(clients).start(any(),any());
        var configuration=mock(CatalogConnectionSettingsGateway.class);
        when(configuration.getSettings()).thenReturn(settings);
        var scheduler=mock(ScheduledExecutorService.class);
        AtomicReference<Runnable> tick=new AtomicReference<>();
        doAnswer(call -> { tick.set(call.getArgument(0)); return mock(ScheduledFuture.class); })
                .when(scheduler).scheduleWithFixedDelay(any(Runnable.class),eq(0L),eq(30L),eq(TimeUnit.SECONDS));
        try(var monitor=new CatalogSynchronizationMonitor(clients,configuration,snapshots,useCase(updates),scheduler)) {
            monitor.start(); monitor.start();
            tick.get().run();
            assertThat(snapshots.getState().appliedVersion()).isEqualTo(10);
            tick.get().run();
            verify(scheduler).scheduleWithFixedDelay(any(Runnable.class),eq(0L),eq(30L),eq(TimeUnit.SECONDS));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_processed_event",Integer.class)).isZero();
        }
        verify(scheduler).shutdownNow();
    }
}
