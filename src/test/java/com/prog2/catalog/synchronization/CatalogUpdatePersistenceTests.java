package com.prog2.catalog.synchronization;

import java.time.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;
import com.prog2.catalog.synchronization.application.usecases.*;
import com.prog2.catalog.synchronization.domain.enums.*;
import com.prog2.catalog.synchronization.domain.exception.*;
import com.prog2.catalog.synchronization.domain.model.*;
import com.prog2.catalog.synchronization.domain.ports.in.RecoverCatalogUseCase;
import com.prog2.catalog.synchronization.domain.ports.out.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest(classes=CatalogUpdateTestConfiguration.class, webEnvironment=SpringBootTest.WebEnvironment.NONE)
@TestConstructor(autowireMode=TestConstructor.AutowireMode.ALL)
@ActiveProfiles("update-persistence-test")
@Timeout(40)
class CatalogUpdatePersistenceTests {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4.8")
            .withDatabaseName("updates_test").withUsername("updates_test").withPassword("fictional-update-password")
            .withCommand("--log-bin-trust-function-creators=1");
    @DynamicPropertySource static void settings(DynamicPropertyRegistry registry) {
        registry.add("DB_HOST", MYSQL::getHost); registry.add("DB_PORT", MYSQL::getFirstMappedPort);
        registry.add("DB_NAME", MYSQL::getDatabaseName); registry.add("DB_USER", MYSQL::getUsername);
        registry.add("DB_PASSWORD", MYSQL::getPassword);
    }
    private final CatalogSnapshotRepository snapshots;
    private final CatalogUpdateRepository updates;
    private final JdbcTemplate jdbc;
    CatalogUpdatePersistenceTests(CatalogSnapshotRepository snapshots, CatalogUpdateRepository updates, JdbcTemplate jdbc) {
        this.snapshots=snapshots; this.updates=updates; this.jdbc=jdbc;
    }
    @BeforeEach void reset() {
        jdbc.execute("DROP TRIGGER IF EXISTS reject_fictional_professional");
        jdbc.update("DELETE FROM catalog_processed_event"); jdbc.update("DELETE FROM catalog_rejected_record");
        jdbc.update("UPDATE catalog_sync_state SET applied_version = NULL WHERE id = 1");
        snapshots.replace(UUID.randomUUID(), SnapshotFixtures.snapshot(7, 1));
    }
    @AfterEach void removeTrigger() { jdbc.execute("DROP TRIGGER IF EXISTS reject_fictional_professional"); }

    static CatalogChangesBatch batch(long base, long target, long id) {
        var snapshot=SnapshotFixtures.snapshot(target,id);
        return new CatalogChangesBatch(base,new CatalogPublication(target,1,SnapshotFixtures.TIME),
                snapshot.professionalCategories(),snapshot.professionals(),snapshot.weeklySchedules());
    }
    static CatalogNotification notice(String id, long version) {
        return new CatalogNotification(id,version,SnapshotFixtures.TIME,"MANUAL");
    }

    @Test void commitsOnlyTheBatchAndItsReceiptAndPreservesSnapshotTime() {
        UUID run=UUID.randomUUID();
        CatalogState state=updates.apply(run,batch(7,10,2),notice("batch-event",10));
        assertThat(state.appliedVersion()).isEqualTo(10);
        assertThat(state.appliedRunId()).isEqualTo(run);
        assertThat(state.generatedAt()).isEqualTo(SnapshotFixtures.TIME);
        assertThat(state.observedVersion()).isEqualTo(10);
        assertThat(jdbc.queryForList("SELECT id FROM professional ORDER BY id",Long.class)).containsExactly(101L,102L);
        assertThat(updates.isProcessed("batch-event")).isTrue();
        updates.apply(run,batch(7,11,3),notice("batch-event",10));
        assertThat(snapshots.getState().appliedVersion()).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_processed_event",Integer.class)).isEqualTo(1);
    }

    @Test void rollsBackEntitiesVersionAndReceiptAfterARealSqlFailure() {
        jdbc.execute("""
                CREATE TRIGGER reject_fictional_professional BEFORE INSERT ON professional
                FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'fictional failure'
                """);
        assertThatThrownBy(() -> updates.apply(UUID.randomUUID(),batch(7,8,2),notice("rolled-back",8)))
                .isInstanceOf(CatalogSnapshotException.class);
        assertThat(snapshots.getState().appliedVersion()).isEqualTo(7);
        assertThat(jdbc.queryForList("SELECT id FROM professional_category",Long.class)).containsExactly(1L);
        assertThat(updates.isProcessed("rolled-back")).isFalse();
    }

    @Test void rejectsMissingReferencesAndChangedBaseWithoutPartialEffects() {
        var valid=batch(7,8,2);
        var broken=new CatalogChangesBatch(7,valid.publication(),valid.categories(),
                List.of(new Professional(102,999,"Fictional","Missing",true,SnapshotFixtures.TIME,SnapshotFixtures.TIME)),List.of());
        assertThatThrownBy(() -> updates.apply(UUID.randomUUID(),broken,notice("bad-reference",8)))
                .isInstanceOf(CatalogDiscontinuityException.class);
        assertThat(jdbc.queryForList("SELECT id FROM professional_category",Long.class)).containsExactly(1L);
        assertThatThrownBy(() -> updates.apply(UUID.randomUUID(),batch(6,8,2),notice("bad-base",8)))
                .isInstanceOf(CatalogSnapshotException.class);
        assertThat(snapshots.getState().appliedVersion()).isEqualTo(7);
    }

    @Test void recordsCoveredAndRejectedEventsWithoutAdvancingData() {
        String longId="fictitious-"+"á".repeat(40000);
        updates.recordCovered(notice(longId,7));
        updates.recordCovered(notice("Case",7)); updates.recordCovered(notice("case",7));
        assertThat(updates.isProcessed(longId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_processed_event",Integer.class)).isEqualTo(3);
        var rejected=new RejectedCatalogRecord("fictional-topic",0,20,"INCOMPATIBLE_CATALOG_NOTIFICATION");
        updates.recordRejection(rejected); updates.recordRejection(rejected);
        assertThat(updates.isRejected(rejected)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_rejected_record",Integer.class)).isEqualTo(1);
        assertThat(snapshots.getState().appliedVersion()).isEqualTo(7);
        assertThatThrownBy(() -> updates.recordCovered(notice("future",8))).isInstanceOf(CatalogSnapshotException.class);
        assertThat(updates.isProcessed("future")).isFalse();
    }

    @Test void reconcilesARealCommitBeforeRetryingAndThenDeduplicates() {
        CatalogUpdateRepository uncertain=mock(CatalogUpdateRepository.class,delegatesTo(updates));
        doAnswer(call -> {
            updates.apply(call.getArgument(0),call.getArgument(1),call.getArgument(2));
            throw new CatalogSnapshotException(SnapshotFailureKind.UNCERTAIN,"FICTIONAL_LOST_CONFIRMATION");
        }).when(uncertain).apply(any(),any(),any());
        var gateway=mock(CatalogChangesGateway.class);
        when(gateway.readSince(7)).thenReturn(batch(7,10,2));
        var recovery=mock(RecoverCatalogUseCase.class);
        when(recovery.resume()).thenReturn(Optional.empty());
        var useCase=new UpdateCatalogUseCaseImpl(gateway,uncertain,snapshots,recovery,mock(TechnicalAccessGateway.class),
                Clock.fixed(SnapshotFixtures.TIME,ZoneOffset.UTC),Duration.ZERO,new ReentrantLock());
        var event=notice("lost-confirmation",10);
        assertThat(useCase.process(event).recorded()).isTrue();
        assertThat(useCase.process(event).recorded()).isTrue();
        verify(uncertain).apply(any(),any(),any());
        verify(gateway).readSince(7);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_processed_event",Integer.class)).isEqualTo(1);
        assertThat(snapshots.getState().appliedVersion()).isEqualTo(10);
    }

    @Test void discontinuityUsesTheRealSnapshotAndItsVersionAsTheNewBase() {
        var clock = Clock.fixed(SnapshotFixtures.TIME, ZoneOffset.UTC);
        var lock = new ReentrantLock();
        var access = mock(TechnicalAccessGateway.class);
        when(access.getToken()).thenReturn("fictional-access");
        var download = mock(CatalogSnapshotGateway.class);
        when(download.fetch(any())).thenReturn(SnapshotFixtures.snapshot(12, 3));
        var load = new SynchronizeCatalogUseCaseImpl(download, snapshots, access, clock, Duration.ZERO, lock);
        var recovery = new RecoverCatalogUseCaseImpl(load, updates, access, clock, lock);
        var changes = mock(CatalogChangesGateway.class);
        when(changes.readSince(7)).thenAnswer(_ -> {
            assertThat(snapshots.getState().phase()).isEqualTo(SynchronizationPhase.IN_PROGRESS);
            throw new CatalogDiscontinuityException(CatalogRecoveryReason.CHANGE_MISSING);
        });
        var useCase = new UpdateCatalogUseCaseImpl(changes, updates, snapshots, recovery, access,
                clock, Duration.ZERO, lock);

        assertThat(useCase.process(notice("recovered-event", 10)).recorded()).isTrue();
        assertThat(snapshots.getState().appliedVersion()).isEqualTo(12);
        assertThat(jdbc.queryForList("SELECT id FROM professional_category", Long.class)).containsExactly(3L);
        assertThat(updates.isProcessed("recovered-event")).isTrue();
        when(changes.readSince(12)).thenReturn(batch(12, 12, 3));
        assertThat(useCase.check().phase()).isEqualTo(SynchronizationPhase.CURRENT);
        verify(download).fetch("fictional-access");
    }
}
