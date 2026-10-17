package com.prog2.catalog.synchronization.domain.model;

import com.prog2.catalog.synchronization.domain.enums.SynchronizationPhase;

/** Resultado del ciclo actual, sin confundirlo con un intento histórico almacenado. */
public record SynchronizationResult(SynchronizationPhase phase, int attempts, String safeDiagnostic) { }
