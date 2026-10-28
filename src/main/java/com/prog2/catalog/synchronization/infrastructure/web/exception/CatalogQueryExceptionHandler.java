package com.prog2.catalog.synchronization.infrastructure.web.exception;

import java.net.URI;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import com.prog2.catalog.synchronization.application.exception.CatalogQueryException;
import com.prog2.catalog.synchronization.domain.enums.CatalogQueryFailure;
import com.prog2.catalog.synchronization.infrastructure.web.controller.CatalogQueryController;

/** Traducción MVC limitada a estas consultas: códigos estables y ningún mensaje de proveedor o entrada reflejada. */
@RestControllerAdvice(assignableTypes = CatalogQueryController.class)
public class CatalogQueryExceptionHandler {
    /** @return rechazo de aplicación en problem+json, con ruta sin query como instance */
    @ExceptionHandler(CatalogQueryException.class)
    public ResponseEntity<ProblemDetail> queryFailure(CatalogQueryException failure, HttpServletRequest request) {
        return problem(failure.reason(), request);
    }

    /** @return 400 tanto para Bean Validation como para conversión numérica inválida o desbordada */
    @ExceptionHandler({HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ProblemDetail> invalidId(HttpServletRequest request) {
        return problem(CatalogQueryFailure.INVALID_PROFESSIONAL_ID, request);
    }

    private static ResponseEntity<ProblemDetail> problem(CatalogQueryFailure reason, HttpServletRequest request) {
        HttpStatus status = switch (reason) {
            case INVALID_PROFESSIONAL_ID -> HttpStatus.BAD_REQUEST;
            case PROFESSIONAL_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CATALOG_NOT_CURRENT, CATALOG_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        ProblemDetail detail = ProblemDetail.forStatus(status);
        detail.setType(URI.create("about:blank"));
        detail.setProperty("code", reason.name());
        detail.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header(HttpHeaders.CACHE_CONTROL, "no-store").body(detail);
    }
}
