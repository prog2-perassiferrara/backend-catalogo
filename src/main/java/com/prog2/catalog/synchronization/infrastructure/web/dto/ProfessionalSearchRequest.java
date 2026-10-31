package com.prog2.catalog.synchronization.infrastructure.web.dto;

import java.time.LocalDate;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import com.prog2.catalog.synchronization.domain.model.ProfessionalSearchCriteria;

/** Parámetros opcionales del GET; normaliza antes de validar la longitud efectiva del nombre. */
public record ProfessionalSearchRequest(@Positive @Schema(description = "ID positivo de categoría; si no existe, la búsqueda es vacía.") Long categoryId,
        @Size(max = ProfessionalSearchCriteria.MAX_NAME_LENGTH)
        @Schema(description = "Fragmento de nombre y apellido sin distinguir mayúsculas ni tildes. Se recortan extremos; blanco omite el filtro.") String name,
        @Schema(description = "Si se omite, incluye profesionales habilitados y deshabilitados.") Boolean enabled,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Schema(description = "Agenda habilitada para ese día de semana, sin prometer slots libres.", type = "string", format = "date") LocalDate availableOn,
        @Min(0) @Schema(defaultValue = "0") Integer page,
        @Min(1) @Max(ProfessionalSearchCriteria.MAX_SIZE) @Schema(defaultValue = "20") Integer size) {
    public ProfessionalSearchRequest { name = ProfessionalSearchCriteria.normalizeName(name); }
}

