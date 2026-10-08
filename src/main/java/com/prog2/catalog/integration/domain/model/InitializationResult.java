package com.prog2.catalog.integration.domain.model;

import com.prog2.catalog.integration.domain.enums.AttemptOutcome;

/**
 * Resultado inmutable que conecta la evaluación del caso de uso con el ciclo de arranque.
 *
 * @param outcome indica si el ciclo debe terminar o puede continuar
 * @param safeDiagnostic mensaje apto para logs, sin JWT, contraseñas ni cuerpos HTTP
 */
public record InitializationResult(AttemptOutcome outcome, String safeDiagnostic) { }
