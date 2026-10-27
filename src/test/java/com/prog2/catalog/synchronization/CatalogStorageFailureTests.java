package com.prog2.catalog.synchronization;

import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLTransientConnectionException;
import java.util.stream.Stream;

import org.hibernate.TransactionException;
import org.hibernate.HibernateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.transaction.TransactionSystemException;

import com.prog2.catalog.synchronization.domain.enums.SnapshotFailureKind;
import com.prog2.catalog.synchronization.domain.exception.CatalogSnapshotException;
import com.prog2.catalog.synchronization.infrastructure.persistence.adapter.*;
import com.prog2.catalog.synchronization.infrastructure.persistence.service.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Comprueba traducción de la frontera JPA; no sustituye el clasificador bajo prueba. */
class CatalogStorageFailureTests {
    @Test
    void theStandardHibernateTranslationPreservesConnectionUncertainty() {
        var sql = new SQLException("fictional connection loss", "08S01",
                new SocketTimeoutException("fictional read timeout"));
        var translated = new HibernateJpaDialect().translateExceptionIfPossible(
                new TransactionException("fictional commit failure", sql));
        assertThat(translated).isInstanceOf(DataAccessResourceFailureException.class);
        assertBothAdapters(translated, SnapshotFailureKind.UNCERTAIN, "LOCAL_STORAGE_UNCERTAIN");
    }

    @ParameterizedTest
    @MethodSource("storageFailures")
    void preservesConnectionUncertaintyAndPermanentErrors(RuntimeException failure,
            SnapshotFailureKind kind, String diagnostic) {
        assertBothAdapters(failure, kind, diagnostic);
    }

    static Stream<Arguments> storageFailures() {
        return Stream.of(
                Arguments.of(new JpaSystemException(new TransactionException("fictional rollback",
                                new SQLNonTransientConnectionException("fictional closed connection"))),
                        SnapshotFailureKind.UNCERTAIN, "LOCAL_STORAGE_UNCERTAIN"),
                Arguments.of(new JpaSystemException(new TransactionException("fictional connection",
                                new SQLTransientConnectionException("fictional unavailable connection"))),
                        SnapshotFailureKind.UNCERTAIN, "LOCAL_STORAGE_UNCERTAIN"),
                Arguments.of(new JpaSystemException(new TransactionException("fictional commit",
                                new SQLException("fictional network", "08006"))),
                        SnapshotFailureKind.UNCERTAIN, "LOCAL_STORAGE_UNCERTAIN"),
                Arguments.of(new TransientDataAccessResourceException("fictional transient wrapper",
                                new SQLException("fictional connection", "08S01")),
                        SnapshotFailureKind.UNCERTAIN, "LOCAL_STORAGE_UNCERTAIN"),
                Arguments.of(new TransientDataAccessResourceException("fictional temporary"),
                        SnapshotFailureKind.TEMPORARY, "LOCAL_STORAGE_TEMPORARY"),
                Arguments.of(new DataAccessResourceFailureException("fictional unavailable"),
                        SnapshotFailureKind.UNCERTAIN, "LOCAL_STORAGE_UNCERTAIN"),
                Arguments.of(new TransactionSystemException("fictional transaction"),
                        SnapshotFailureKind.UNCERTAIN, "LOCAL_STORAGE_UNCERTAIN"),
                Arguments.of(new JpaSystemException(new TransactionException("fictional SQL error",
                                new SQLException("fictional syntax", "42000"))),
                        SnapshotFailureKind.TERMINAL, "LOCAL_STORAGE_FAILURE"),
                Arguments.of(new DataIntegrityViolationException("fictional constraint",
                                new SQLException("fictional constraint", "23000")),
                        SnapshotFailureKind.TERMINAL, "LOCAL_STORAGE_FAILURE"),
                Arguments.of(new JpaSystemException(new TransactionException("fictional unknown SQL",
                                new SQLException("fictional missing state", (String) null))),
                        SnapshotFailureKind.UNCERTAIN, "LOCAL_STORAGE_UNCERTAIN"),
                Arguments.of(new JpaSystemException(new HibernateException("fictional non-transactional failure",
                                new SQLException("fictional missing state", (String) null))),
                        SnapshotFailureKind.TERMINAL, "LOCAL_STORAGE_FAILURE"));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void unclassifiedFailuresLogTypesWithoutProviderMessages(CapturedOutput output) {
        var failure = new JpaSystemException(new HibernateException("fictional-private-value",
                new SQLException("fictional-sql-body", "42000")));
        assertBothAdapters(failure, SnapshotFailureKind.TERMINAL, "LOCAL_STORAGE_FAILURE");
        assertThat(output).contains("JpaSystemException", "java.sql.SQLException")
                .doesNotContain("fictional-private-value", "fictional-sql-body");
    }

    @Test
    void domainFailuresRetainTheirClassificationAndIdentity() {
        var failure = new CatalogSnapshotException(SnapshotFailureKind.UNAUTHORIZED, "FICTIONAL_ACCESS");
        var snapshotWriter = mock(TransactionalCatalogSnapshotWriter.class);
        var updateWriter = mock(TransactionalCatalogUpdateWriter.class);
        when(snapshotWriter.getState()).thenThrow(failure);
        when(updateWriter.getState()).thenThrow(failure);
        var snapshot = new JpaCatalogSnapshotAdapter(snapshotWriter);
        var updates = new JpaCatalogUpdateAdapter(updateWriter);
        assertThatThrownBy(snapshot::getState).isSameAs(failure);
        assertThatThrownBy(updates::getState).isSameAs(failure);
    }

    private static void assertBothAdapters(RuntimeException failure, SnapshotFailureKind kind,
            String diagnostic) {
        var snapshotWriter = mock(TransactionalCatalogSnapshotWriter.class);
        var updateWriter = mock(TransactionalCatalogUpdateWriter.class);
        when(snapshotWriter.getState()).thenThrow(failure);
        when(updateWriter.getState()).thenThrow(failure);
        var snapshot = new JpaCatalogSnapshotAdapter(snapshotWriter);
        var updates = new JpaCatalogUpdateAdapter(updateWriter);
        assertThatThrownBy(snapshot::getState).isInstanceOfSatisfying(CatalogSnapshotException.class,
                error -> assertFailure(error, kind, diagnostic));
        assertThatThrownBy(updates::getState).isInstanceOfSatisfying(CatalogSnapshotException.class,
                error -> assertFailure(error, kind, diagnostic));
    }

    private static void assertFailure(CatalogSnapshotException error, SnapshotFailureKind kind,
            String diagnostic) {
        assertThat(error.kind()).isEqualTo(kind);
        assertThat(error.getMessage()).isEqualTo(diagnostic);
    }
}
