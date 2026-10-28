package com.prog2.catalog.synchronization.domain.model;

import java.time.LocalDate;

/** Filtros opcionales y límites de una consulta; no contiene tipos de transporte o persistencia. */
public record ProfessionalSearchCriteria(Long categoryId, String name, Boolean enabled,
        LocalDate availableOn, int page, int size) {
    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;
    public static final int MAX_NAME_LENGTH = 100;

    public ProfessionalSearchCriteria {
        name = normalizeName(name);
    }

    /** @return texto sin espacios exteriores, o null cuando no se indicó filtro */
    public static String normalizeName(String name) {
        if (name == null) { return null; }
        String normalized = name.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    /** @return si cumple los límites acordados, incluido el desplazamiento entero que admite JPA */
    public boolean isValid() {
        return (categoryId == null || categoryId > 0)
                && (name == null || name.length() <= MAX_NAME_LENGTH)
                && page >= 0 && size >= 1 && size <= MAX_SIZE
                && (long) page * size <= Integer.MAX_VALUE;
    }
}

