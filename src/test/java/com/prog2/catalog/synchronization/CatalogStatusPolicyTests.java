package com.prog2.catalog.synchronization;

import java.time.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import com.prog2.catalog.synchronization.application.service.CatalogStatusPolicy;
import com.prog2.catalog.synchronization.domain.enums.*;
import com.prog2.catalog.synchronization.domain.model.CatalogState;
import static org.assertj.core.api.Assertions.assertThat;

class CatalogStatusPolicyTests {
    private static final Instant NOW = SnapshotFixtures.TIME;
    private final CatalogStatusPolicy policy = new CatalogStatusPolicy(Clock.fixed(NOW, ZoneOffset.UTC));

    @ParameterizedTest
    @CsvSource({"59999999999,CURRENT", "60000000000,CURRENT", "60000000001,STALE", "-1,STALE"})
    void enforcesTheInclusiveSixtySecondBoundaryAndRejectsFutureChecks(long nanos, CatalogAvailability expected) {
        assertThat(policy.evaluate(state(7L, 7L, SynchronizationPhase.CURRENT, NOW.minusNanos(nanos)), true, true).state())
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"IN_PROGRESS,SYNCHRONIZING", "BUSY,SYNCHRONIZING", "RECOVERY_WAITING,RECOVERING",
            "FAILED,FAILED", "UNVERIFIABLE,UNVERIFIABLE", "SNAPSHOT_APPLIED,STALE", "NOT_STARTED,STALE"})
    void aRecentTimestampCannotOverrideAnIneligiblePhase(SynchronizationPhase phase, CatalogAvailability expected) {
        var status = policy.evaluate(state(7L, 7L, phase, NOW), true, true);
        assertThat(status.state()).isEqualTo(expected);
        assertThat(status.copyAvailable()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("ineligibleChecks")
    void refusesUnknownOrHistoricalVerification(CatalogState state, boolean verified, boolean access) {
        assertThat(policy.evaluate(state, verified, access).state()).isEqualTo(CatalogAvailability.STALE);
    }

    static Stream<Arguments> ineligibleChecks() {
        return Stream.of(
                Arguments.of(state(7L, 7L, SynchronizationPhase.CURRENT, NOW), false, true),
                Arguments.of(state(7L, 7L, SynchronizationPhase.CURRENT, NOW), true, false),
                Arguments.of(state(7L, 8L, SynchronizationPhase.CURRENT, NOW), true, true),
                Arguments.of(state(7L, null, SynchronizationPhase.CURRENT, NOW), true, true),
                Arguments.of(state(7L, 7L, SynchronizationPhase.CURRENT, null), true, true));
    }

    @Test void copyAvailabilityDependsOnConfirmedVersionIncludingZeroAndEmptyCatalogs() {
        assertThat(policy.evaluate(state(null, null, SynchronizationPhase.NOT_STARTED, null), false, false).state())
                .isEqualTo(CatalogAvailability.NO_COPY);
        assertThat(policy.evaluate(state(null, null, SynchronizationPhase.FAILED, null), false, false).copyAvailable()).isFalse();
        var empty = policy.evaluate(state(0L, 0L, SynchronizationPhase.CURRENT, NOW), true, true);
        assertThat(empty.state()).isEqualTo(CatalogAvailability.CURRENT);
        assertThat(empty.copyAvailable()).isTrue();
        assertThat(empty.evaluatedAt()).isEqualTo(NOW);
    }

    static CatalogState state(Long applied, Long observed, SynchronizationPhase phase, Instant checked) {
        return new CatalogState(applied, null, NOW, NOW, null, phase, NOW, "FICTIONAL_DIAGNOSTIC", observed, checked);
    }
}
