package com.prog2.catalog.synchronization;

import java.time.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.boot.test.system.*;
import org.springframework.kafka.support.Acknowledgment;
import tools.jackson.databind.json.JsonMapper;
import com.prog2.catalog.synchronization.domain.enums.*;
import com.prog2.catalog.synchronization.domain.model.*;
import com.prog2.catalog.synchronization.domain.ports.in.UpdateCatalogUseCase;
import com.prog2.catalog.synchronization.infrastructure.messaging.CatalogUpdatedListener;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class CatalogUpdatedListenerTests {
    private final UpdateCatalogUseCase updates = mock(UpdateCatalogUseCase.class);
    private final Acknowledgment acknowledgment = mock(Acknowledgment.class);
    private final Runnable stop = mock(Runnable.class);
    private final CatalogUpdatedListener listener = new CatalogUpdatedListener(updates, JsonMapper.builder().build(),
            Clock.fixed(SnapshotFixtures.TIME, ZoneOffset.UTC), stop);
    private static final String EVENT = """
            {"eventId":"fictional-event","eventType":"CatalogUpdated","newVersion":10,
            "occurredAt":"2026-09-10T13:44:59Z","source":"MANUAL","schemaVersion":1}
            """;

    private void receive(String body) {
        listener.onMessage(new ConsumerRecord<>("fictional-topic", 2, 14, "10", body), acknowledgment);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"{}", "{\"schemaVersion\":2}", "{\"schemaVersion\":1,\"eventType\":\"Other\"}",
            "{\"schemaVersion\":1,\"eventType\":\"CatalogUpdated\"}"})
    void incompatibleNotificationsRequireADurableRejectionBeforeAck(String body) {
        when(updates.reject(any())).thenReturn(new CatalogUpdateResult(SynchronizationPhase.CURRENT, true, null,
                "CATALOG_RECORD_REJECTED", null));
        receive(body);
        verify(updates).reject(new RejectedCatalogRecord("fictional-topic", 2, 14, "INCOMPATIBLE_CATALOG_NOTIFICATION"));
        verify(updates, never()).process(any());
        verify(acknowledgment).acknowledge();
    }

    @Test void malformedDatesAreRejectedWithoutApplyingTheNotification() {
        when(updates.reject(any())).thenReturn(new CatalogUpdateResult(SynchronizationPhase.CURRENT, true, null, "REJECTED", null));
        receive(EVENT.replace("2026-09-10T13:44:59Z", "not-a-date"));
        verify(updates).reject(any());
        verify(updates, never()).process(any());
        verify(acknowledgment).acknowledge();
    }

    @ParameterizedTest
    @EnumSource(value = SnapshotFailureKind.class, names = {"UNAUTHORIZED", "TERMINAL"})
    void permanentFailuresStopConsumptionWithoutAcknowledging(SnapshotFailureKind kind) {
        when(updates.process(any())).thenReturn(new CatalogUpdateResult(SynchronizationPhase.FAILED, false, null,
                "FICTIONAL_ACCESS_FAILURE", kind));
        receive(EVENT);
        verify(stop).run();
        verifyNoInteractions(acknowledgment);
    }

    @ParameterizedTest
    @CsvSource({"-5,1", "0,1", "30,30"})
    void temporaryFailuresPauseUntilTheDeadlineWithAMinimumPositiveDelay(long offset, long expected) {
        when(updates.process(any())).thenReturn(new CatalogUpdateResult(SynchronizationPhase.RECOVERY_WAITING, false,
                SnapshotFixtures.TIME.plusSeconds(offset), "WAITING", SnapshotFailureKind.TEMPORARY));
        receive(EVENT);
        verify(acknowledgment).nack(Duration.ofSeconds(expected));
        verify(acknowledgment, never()).acknowledge();
        verifyNoInteractions(stop);
    }

    @Test void missingDeadlineUsesTheDefaultWait() {
        when(updates.process(any())).thenReturn(new CatalogUpdateResult(SynchronizationPhase.BUSY, false, null,
                "CATALOG_BUSY", SnapshotFailureKind.TEMPORARY));
        receive(EVENT);
        verify(acknowledgment).nack(Duration.ofSeconds(5));
    }

    @Test void lostOffsetConfirmationRequestsRedeliveryWithoutLoggingTheUnderlyingError(CapturedOutput output) {
        when(updates.process(any())).thenReturn(new CatalogUpdateResult(SynchronizationPhase.CURRENT, true, null,
                "CATALOG_CHECKED", null));
        doThrow(new IllegalStateException("fictional-sensitive-error")).when(acknowledgment).acknowledge();
        receive(EVENT);
        verify(acknowledgment).nack(Duration.ofSeconds(30));
        verifyNoInteractions(stop);
        assertThat(output).contains("CATALOG_OFFSET_CONFIRMATION_UNAVAILABLE").doesNotContain("fictional-sensitive-error");
    }
}
