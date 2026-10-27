package com.prog2.catalog.synchronization.infrastructure.persistence.mapper;

import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLTransientConnectionException;
import java.util.StringJoiner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.TransactionException;

import com.prog2.catalog.synchronization.domain.enums.SnapshotFailureKind;
import com.prog2.catalog.synchronization.domain.exception.CatalogSnapshotException;

/** Traduce fallos de persistencia sin perder la incertidumbre de un commit ni exponer mensajes JDBC. */
public final class CatalogStorageFailureMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogStorageFailureMapper.class);
    private CatalogStorageFailureMapper() { }

    /**
     * Revisa la cadena completa: el envoltorio de JPA puede ocultar la clasificación de conexión.
     * SQLState 08 y los tipos JDBC de conexión prevalecen porque no demuestran rollback.
     * Una conexión cerrada puede perder el SQLState al fallar también el rollback;
     * el pool debe reemplazarla, sin detener por ello la recuperación del backend.
     *
     * @param failure error del cuerpo o del commit del proxy transaccional
     * @return fallo tipado y diagnóstico seguro, sin copiar mensajes de dependencias
     */
    public static CatalogSnapshotException translate(RuntimeException failure) {
        if (uncertainFailure(failure) || failure instanceof DataAccessResourceFailureException
                || failure instanceof TransactionException) {
            return new CatalogSnapshotException(SnapshotFailureKind.UNCERTAIN, "LOCAL_STORAGE_UNCERTAIN");
        }
        if (failure instanceof TransientDataAccessException) {
            return new CatalogSnapshotException(SnapshotFailureKind.TEMPORARY, "LOCAL_STORAGE_TEMPORARY");
        }
        LOGGER.warn("Unclassified catalog storage failure types: {}", failureTypes(failure));
        return new CatalogSnapshotException(SnapshotFailureKind.TERMINAL, "LOCAL_STORAGE_FAILURE");
    }

    /** Comprobación de tipos en una frontera JDBC, sin seleccionar recursos de negocio. */
    private static boolean uncertainFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.TransactionException transaction
                    && transaction.getCause() instanceof SQLException sql && sql.getSQLState() == null) {
                return true;
            }
            if (cause instanceof SQLTransientConnectionException || cause instanceof SQLNonTransientConnectionException) {
                return true;
            }
            if (cause instanceof SQLException sql && sql.getSQLState() != null
                    && sql.getSQLState().startsWith("08")) {
                return true;
            }
        }
        return false;
    }

    /** Diagnóstico operativo sin mensajes de proveedor, SQL, parámetros ni secretos. */
    private static String failureTypes(Throwable failure) {
        var types = new StringJoiner(" -> ");
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            types.add(cause.getClass().getName());
        }
        return types.toString();
    }
}
