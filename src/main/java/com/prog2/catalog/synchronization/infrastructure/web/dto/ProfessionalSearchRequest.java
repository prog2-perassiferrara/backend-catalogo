package com.prog2.catalog.synchronization.infrastructure.web.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import com.prog2.catalog.synchronization.domain.model.ProfessionalSearchCriteria;

/** Parámetros opcionales del GET; normaliza antes de validar la longitud efectiva del nombre. */
public record ProfessionalSearchRequest(@Positive Long categoryId,
        @Size(max = ProfessionalSearchCriteria.MAX_NAME_LENGTH) String name, Boolean enabled,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate availableOn,
        @Min(0) Integer page, @Min(1) @Max(ProfessionalSearchCriteria.MAX_SIZE) Integer size) {
    public ProfessionalSearchRequest { name = ProfessionalSearchCriteria.normalizeName(name); }
}

