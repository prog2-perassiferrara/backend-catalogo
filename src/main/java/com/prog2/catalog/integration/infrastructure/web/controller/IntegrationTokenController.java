package com.prog2.catalog.integration.infrastructure.web.controller;

import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.prog2.catalog.integration.application.exception.IntegrationUnavailableException;
import com.prog2.catalog.integration.domain.ports.in.GetTechnicalTokenUseCase;
import com.prog2.catalog.integration.infrastructure.web.dto.TechnicalTokenResponse;

/**
 * Adaptador HTTP de entrada del puerto de consulta de token técnico.
 * Aplica Adapter: traduce el contrato HTTP al caso de uso sin duplicar sus reglas de sesión.
 * La cadena de seguridad autoriza antes de ejecutar este componente.
 */
@RestController
@RequestMapping("/api/internal/catedra/token")
public class IntegrationTokenController {

    private final GetTechnicalTokenUseCase getTechnicalToken;

    public IntegrationTokenController(GetTechnicalTokenUseCase getTechnicalToken) {
        this.getTechnicalToken = getTechnicalToken;
    }

    /**
     * Entrega solo el token existente; no autentica ni renueva la cuenta técnica.
     *
     * @return 200 con el JWT central y prohibición de caché
     * @throws IntegrationUnavailableException si la sesión no es utilizable, traducida a 503
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TechnicalTokenResponse> getToken() {
        return noCache(HttpStatus.OK).body(new TechnicalTokenResponse(getTechnicalToken.getToken()));
    }

    /** @return 503 estable, sin copiar la excepción ni revelar credenciales o causas internas */
    @ExceptionHandler(IntegrationUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleUnavailable() {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problem.setType(URI.create("about:blank"));
        problem.setProperty("code", "TECHNICAL_INTEGRATION_UNAVAILABLE");
        return noCache(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    private static ResponseEntity.BodyBuilder noCache(HttpStatus status) {
        return ResponseEntity.status(status).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.PRAGMA, "no-cache");
    }
}
