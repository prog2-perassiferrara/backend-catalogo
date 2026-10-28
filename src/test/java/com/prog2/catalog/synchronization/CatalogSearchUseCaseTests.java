package com.prog2.catalog.synchronization;

import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import com.prog2.catalog.synchronization.application.exception.CatalogQueryException;
import com.prog2.catalog.synchronization.application.service.*;
import com.prog2.catalog.synchronization.application.usecases.*;
import com.prog2.catalog.synchronization.domain.enums.*;
import com.prog2.catalog.synchronization.domain.exception.*;
import com.prog2.catalog.synchronization.domain.model.*;
import com.prog2.catalog.synchronization.domain.ports.out.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Ejecución real de los casos y la política; dobles únicamente en persistencia, sesión y reloj. */
class CatalogSearchUseCaseTests {
    private final CatalogQueryRepository repository = mock(CatalogQueryRepository.class);
    private final TechnicalAccessGateway access = mock(TechnicalAccessGateway.class);
    private final CatalogSynchronizationGuard guard = new CatalogSynchronizationGuard();
    private final Clock clock = mock(Clock.class);
    private final CatalogStatusPolicy policy = new CatalogStatusPolicy(clock);
    private final SearchProfessionalsUseCaseImpl search = new SearchProfessionalsUseCaseImpl(repository, access, guard, policy);
    private final GetProfessionalCategoriesUseCaseImpl categories = new GetProfessionalCategoriesUseCaseImpl(repository, access, guard, policy);

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(SnapshotFixtures.TIME);
        when(access.getToken()).thenReturn("fictional-central-access");
        guard.confirmVerification();
        views(state(SynchronizationPhase.CURRENT));
    }

    private static CatalogState state(SynchronizationPhase phase) {
        return CatalogStatusPolicyTests.state(7L, 7L, phase, SnapshotFixtures.TIME);
    }

    private void views(CatalogState state) {
        var copy = CatalogSearchFixtures.snapshot(7);
        var entry = new ProfessionalSearchEntry(copy.professionals().getFirst(), copy.professionalCategories().getFirst());
        when(repository.search(any())).thenReturn(new CatalogSearchView(state, List.of(entry), 1));
        when(repository.readCategories()).thenReturn(new CatalogCategoriesView(state, copy.professionalCategories()));
    }

    @ParameterizedTest
    @CsvSource({"CURRENT,CURRENT", "SNAPSHOT_APPLIED,STALE", "IN_PROGRESS,SYNCHRONIZING",
            "RECOVERY_WAITING,RECOVERING", "FAILED,FAILED", "UNVERIFIABLE,UNVERIFIABLE"})
    void confirmedCopyRemainsQueryableInEachFunctionalState(SynchronizationPhase phase, CatalogAvailability expected) {
        views(state(phase));
        var result = search.search(CatalogSearchFixtures.all(0, 20));
        assertThat(result.content()).hasSize(1);
        assertThat(result.catalog().state()).isEqualTo(expected);
        assertThat(categories.get().catalog().state()).isEqualTo(expected);
        assertThat(guard.trySynchronize()).isTrue();
        guard.endSynchronization();
        verify(access, never()).invalidate();
    }

    static Stream<ProfessionalSearchCriteria> invalidCriteria() {
        return Stream.of(null,
                new ProfessionalSearchCriteria(0L, null, null, null, 0, 20),
                new ProfessionalSearchCriteria(-1L, null, null, null, 0, 20),
                new ProfessionalSearchCriteria(null, "x".repeat(101), null, null, 0, 20),
                CatalogSearchFixtures.all(-1, 20), CatalogSearchFixtures.all(0, 0),
                CatalogSearchFixtures.all(0, 101), CatalogSearchFixtures.all(Integer.MAX_VALUE, 100));
    }

    @ParameterizedTest @MethodSource("invalidCriteria")
    void invalidCriteriaNeverReachStorage(ProfessionalSearchCriteria criteria) {
        reason(() -> search.search(criteria), CatalogQueryFailure.INVALID_SEARCH_CRITERIA);
        verifyNoInteractions(repository, access);
    }

    @Test void noCopyIsNotAnEmptySuccessfulSearchOrCategoryList() {
        views(CatalogStatusPolicyTests.state(null, null, SynchronizationPhase.NOT_STARTED, null));
        reason(() -> search.search(CatalogSearchFixtures.all(0, 20)), CatalogQueryFailure.CATALOG_COPY_UNAVAILABLE);
        reason(categories::get, CatalogQueryFailure.CATALOG_COPY_UNAVAILABLE);
        verifyNoInteractions(access);
    }

    @Test void emptyConfirmedCopyIsSuccessfulAndHasZeroPages() {
        when(repository.search(any())).thenReturn(new CatalogSearchView(state(SynchronizationPhase.CURRENT), List.of(), 0));
        when(repository.readCategories()).thenReturn(new CatalogCategoriesView(state(SynchronizationPhase.CURRENT), List.of()));
        var result = search.search(CatalogSearchFixtures.all(0, 20));
        assertThat(result.content()).isEmpty();
        assertThat(result.totalPages()).isZero();
        assertThat(result.catalog().copyAvailable()).isTrue();
        assertThat(categories.get().content()).isEmpty();
    }

    @Test void freshnessIsEvaluatedAfterReadingAndDoesNotForbidBrowsing() {
        when(repository.search(any())).thenAnswer(_ -> {
            when(clock.instant()).thenReturn(SnapshotFixtures.TIME.plusSeconds(61));
            return new CatalogSearchView(state(SynchronizationPhase.CURRENT), List.of(), 0);
        });
        assertThat(search.search(CatalogSearchFixtures.all(0, 20)).catalog().state()).isEqualTo(CatalogAvailability.STALE);
        assertThat(categories.get().catalog().state()).isEqualTo(CatalogAvailability.STALE);
    }

    @Test void lostAccessAndRestartDoNotHideThePreviousCopyOrInitiateRenewal() {
        when(access.getToken()).thenThrow(new CatalogSnapshotException(SnapshotFailureKind.UNAUTHORIZED, "FICTIONAL_EXPIRED"));
        assertThat(search.search(CatalogSearchFixtures.all(0, 20)).catalog().state()).isEqualTo(CatalogAvailability.STALE);
        assertThat(categories.get().content()).hasSize(3);
        guard.requireVerification();
        assertThat(search.search(CatalogSearchFixtures.all(0, 20)).content()).hasSize(1);
        assertThat(categories.get().catalog().state()).isEqualTo(CatalogAvailability.STALE);
        verify(access, never()).invalidate();
    }

    @Test void readFailuresAreUnavailableAndAlwaysReleaseAccess() {
        when(repository.search(any())).thenThrow(new CatalogReadException());
        when(repository.readCategories()).thenThrow(new CatalogReadException());
        reason(() -> search.search(CatalogSearchFixtures.all(0, 20)), CatalogQueryFailure.CATALOG_UNAVAILABLE);
        reason(categories::get, CatalogQueryFailure.CATALOG_UNAVAILABLE);
        assertThat(guard.trySynchronize()).isTrue();
        guard.endSynchronization();
        verifyNoInteractions(access);
    }

    private static void reason(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation, CatalogQueryFailure expected) {
        assertThatThrownBy(operation).isInstanceOfSatisfying(CatalogQueryException.class,
                error -> assertThat(error.reason()).isEqualTo(expected));
    }
}

