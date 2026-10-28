package com.prog2.catalog.synchronization;

import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import com.prog2.catalog.synchronization.application.service.CatalogSynchronizationGuard;
import static org.assertj.core.api.Assertions.assertThat;

/** Intercalados deterministas: no se usa una carrera probabilística para probar exclusión. */
@Timeout(10)
class CatalogSynchronizationGuardTests {
    @Test
    void admitsTwoReadersButExcludesTheWriterUntilTheyFinish() throws Exception {
        var guard = new CatalogSynchronizationGuard();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var reader = executor.submit(() -> {
                assertThat(guard.tryRead()).isTrue();
                try {
                    entered.countDown();
                    assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                } finally { guard.endRead(); }
                return true;
            });
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(guard.tryRead()).isTrue();
                guard.endRead();
                assertThat(guard.trySynchronize()).isFalse();
            } finally { release.countDown(); }
            assertThat(reader.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(guard.trySynchronize()).isTrue();
            guard.endSynchronization();
        }
    }

    @Test
    void keepsWriteReentrancyAndDeniesReadersOnOtherThreads() throws Exception {
        var guard = new CatalogSynchronizationGuard();
        assertThat(guard.trySynchronize()).isTrue();
        try {
            assertThat(guard.trySynchronize()).isTrue();
            guard.endSynchronization();
            assertThat(guard.isSynchronizing()).isTrue();
            try (var executor = Executors.newSingleThreadExecutor()) {
                assertThat(executor.submit(guard::tryRead).get(5, TimeUnit.SECONDS)).isFalse();
            }
        } finally { guard.endSynchronization(); }
        assertThat(guard.isSynchronizing()).isFalse();
        assertThat(guard.tryRead()).isTrue();
        guard.endRead();
    }

    @Test
    void verificationDoesNotSurviveANewProcessGuard() {
        var guard = new CatalogSynchronizationGuard();
        assertThat(guard.verificationRequired()).isTrue();
        guard.confirmVerification();
        assertThat(guard.verificationRequired()).isFalse();
        guard.requireVerification();
        assertThat(guard.verificationRequired()).isTrue();
        assertThat(new CatalogSynchronizationGuard().verificationRequired()).isTrue();
    }
}
