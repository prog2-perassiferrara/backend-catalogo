package com.prog2.catalog.synchronization.application.service;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Coordina lecturas y sincronización de una única instancia del servicio.
 * La escritura es reentrante para CU-02 → CU-03 → CU-01; ninguna espera mantiene
 * una transacción SQL abierta. La verificación vive solo en esta ejecución.
 */
public class CatalogSynchronizationGuard {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile boolean verificationRequired = true;

    /** @return si se obtuvo acceso compartido sin esperar al sincronizador */
    public boolean tryRead() { return lock.readLock().tryLock(); }

    /** Libera exactamente una adquisición de lectura del hilo actual. */
    public void endRead() { lock.readLock().unlock(); }

    /** @return si se obtuvo acceso exclusivo sin esperar; admite reentrada */
    public boolean trySynchronize() { return lock.writeLock().tryLock(); }

    /** Libera exactamente una adquisición exclusiva del hilo actual. */
    public void endSynchronization() { lock.writeLock().unlock(); }

    /** @return si hay un ciclo exclusivo activo, incluido el hilo consultante */
    public boolean isSynchronizing() { return lock.isWriteLocked(); }

    /** @return si falta una comprobación correcta de la ejecución actual */
    public boolean verificationRequired() { return verificationRequired; }

    /** La sincronización invalida la comprobación aun cuando MySQL no permita registrar el fallo. */
    public void requireVerification() { verificationRequired = true; }

    /** Solo la sincronización, después de comprobar y confirmar la publicación, habilita lecturas vigentes. */
    public void confirmVerification() { verificationRequired = false; }
}
