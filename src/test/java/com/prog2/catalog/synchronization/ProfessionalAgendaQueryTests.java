package com.prog2.catalog.synchronization;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.prog2.catalog.synchronization.application.exception.CatalogQueryException;
import com.prog2.catalog.synchronization.application.service.*;
import com.prog2.catalog.synchronization.application.usecases.*;
import com.prog2.catalog.synchronization.domain.enums.*;
import com.prog2.catalog.synchronization.domain.exception.*;
import com.prog2.catalog.synchronization.domain.model.*;
import com.prog2.catalog.synchronization.domain.ports.out.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Casos de uso reales; los dobles controlan lectura y reloj, no reemplazan la política de vigencia. */
class ProfessionalAgendaQueryTests {
    private final CatalogQueryRepository repository = mock(CatalogQueryRepository.class);
    private final TechnicalAccessGateway access = mock(TechnicalAccessGateway.class);
    private final CatalogSynchronizationGuard guard = new CatalogSynchronizationGuard();
    private final Clock clock = mock(Clock.class);
    private final CatalogStatusPolicy policy = new CatalogStatusPolicy(clock);
    private final GetProfessionalAgendaUseCaseImpl agendas = new GetProfessionalAgendaUseCaseImpl(repository, access, guard, policy);
    private final GetCatalogStatusUseCaseImpl statuses = new GetCatalogStatusUseCaseImpl(repository, access, guard, policy);

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(SnapshotFixtures.TIME);
        when(access.getToken()).thenReturn("fictional-access");
        when(repository.readProfessional(101)).thenReturn(view(true));
        when(repository.readState()).thenReturn(current());
        guard.confirmVerification();
    }

    @Test void preservesDisabledFlagsAndReturnsTheSameVersionAsTheRead() {
        var result = agendas.get(101);
        assertThat(result.professional().enabled()).isFalse();
        assertThat(result.weeklySchedules()).hasSize(1);
        assertThat(result.catalog().state()).isEqualTo(CatalogAvailability.CURRENT);
        assertThat(result.catalog().appliedVersion()).isEqualTo(7);
        verify(access, never()).invalidate();
        assertThat(guard.trySynchronize()).isTrue();
        guard.endSynchronization();
    }

    @Test void emptySchedulesAreLegitimateButMissingProfessionalIsNot() {
        var professional = view(true).professional();
        when(repository.readProfessional(101)).thenReturn(new CatalogReadView(current(), professional, List.of()));
        assertThat(agendas.get(101).weeklySchedules()).isEmpty();
        when(repository.readProfessional(101)).thenReturn(view(false));
        assertReason(() -> agendas.get(101), CatalogQueryFailure.PROFESSIONAL_NOT_FOUND);
    }

    @ParameterizedTest @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void invalidIdsDoNotReachStorage(long id) {
        assertReason(() -> agendas.get(id), CatalogQueryFailure.INVALID_PROFESSIONAL_ID);
        verifyNoInteractions(repository, access);
    }

    @Test void noInitialVerificationRejectsBeforeAcquiringTheReadLock() {
        guard.requireVerification();
        assertReason(() -> agendas.get(101), CatalogQueryFailure.CATALOG_NOT_CURRENT);
        verifyNoInteractions(repository, access);
        assertThat(guard.trySynchronize()).isTrue();
        guard.endSynchronization();
    }

    @Test void freshnessIsEvaluatedAfterTheRepositoryReturns() {
        when(repository.readProfessional(101)).thenAnswer(_ -> {
            when(clock.instant()).thenReturn(SnapshotFixtures.TIME.plusSeconds(61));
            return view(false);
        });
        assertReason(() -> agendas.get(101), CatalogQueryFailure.CATALOG_NOT_CURRENT);
    }

    @Test void unusableTechnicalAccessCannotAuthorizeAnOtherwiseCurrentRead() {
        when(access.getToken()).thenThrow(new CatalogSnapshotException(SnapshotFailureKind.UNAUTHORIZED, "FICTIONAL_EXPIRED"));
        assertReason(() -> agendas.get(101), CatalogQueryFailure.CATALOG_NOT_CURRENT);
        assertThat(statuses.get().state()).isEqualTo(CatalogAvailability.STALE);
        verify(access, never()).invalidate();
    }

    @Test void storageFailuresAreUnavailableAndReleaseTheReadLock() {
        when(repository.readProfessional(anyLong())).thenThrow(new CatalogReadException());
        when(repository.readState()).thenThrow(new CatalogReadException());
        assertReason(() -> agendas.get(101), CatalogQueryFailure.CATALOG_UNAVAILABLE);
        assertReason(statuses::get, CatalogQueryFailure.CATALOG_UNAVAILABLE);
        assertThat(guard.trySynchronize()).isTrue();
        guard.endSynchronization();
    }

    @Test void aNewGuardDoesNotTrustPersistedCurrentAndStatusRemainsReadable() {
        var restarted = new GetCatalogStatusUseCaseImpl(repository, access, new CatalogSynchronizationGuard(), policy);
        assertThat(restarted.get().state()).isEqualTo(CatalogAvailability.STALE);
        when(repository.readState()).thenReturn(CatalogStatusPolicyTests.state(null, null, SynchronizationPhase.NOT_STARTED, null));
        assertThat(restarted.get().state()).isEqualTo(CatalogAvailability.NO_COPY);
        when(repository.readState()).thenReturn(CatalogStatusPolicyTests.state(7L, 7L, SynchronizationPhase.FAILED, SnapshotFixtures.TIME));
        assertThat(restarted.get().state()).isEqualTo(CatalogAvailability.FAILED);
    }

    private static CatalogState current() {
        return CatalogStatusPolicyTests.state(7L, 7L, SynchronizationPhase.CURRENT, SnapshotFixtures.TIME);
    }

    private static CatalogReadView view(boolean exists) {
        var snapshot = SnapshotFixtures.snapshot(7, 1);
        return new CatalogReadView(current(), exists ? Optional.of(snapshot.professionals().getFirst()) : Optional.empty(),
                exists ? snapshot.weeklySchedules() : List.of());
    }

    private static void assertReason(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation, CatalogQueryFailure reason) {
        assertThatThrownBy(operation).isInstanceOfSatisfying(CatalogQueryException.class,
                failure -> assertThat(failure.reason()).isEqualTo(reason));
    }
}
