package com.prog2.catalog.infrastructure.security.web;

import java.io.IOException;
import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * Traduce rechazos de los filtros a ProblemDetail: 401 sin autenticación y 403 sin permiso.
 * Estos errores ocurren antes del controlador; no se delegan a su ExceptionHandler.
 * No serializa la excepción original ni revela el estado de la sesión central.
 */
public final class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper mapper;

    /** @param mapper configuración JSON del proceso, con soporte de propiedades ProblemDetail */
    public SecurityProblemHandler(JsonMapper mapper) {
        // En filtros no interviene el converter MVC que normalmente añade este mixin.
        this.mapper = mapper.rebuild().addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class).build();
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException failure) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(response, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException failure) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
    }

    private void write(HttpServletResponse response, HttpStatus status, String code) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create("about:blank"));
        problem.setProperty("code", code);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        mapper.writeValue(response.getOutputStream(), problem);
    }
}
