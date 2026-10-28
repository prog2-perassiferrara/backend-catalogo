package com.prog2.catalog.synchronization.domain.exception;

/** Fallo del puerto de lectura; no transporta mensajes SQL ni causas externas al dominio. */
public class CatalogReadException extends RuntimeException {
    public CatalogReadException() { super("CATALOG_UNAVAILABLE"); }
}
