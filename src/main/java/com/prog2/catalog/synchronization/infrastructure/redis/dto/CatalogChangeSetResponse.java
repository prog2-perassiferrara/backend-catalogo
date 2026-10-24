package com.prog2.catalog.synchronization.infrastructure.redis.dto;

import java.util.List;

/** El contrato publica IDs; no contiene versiones históricas de sus entidades. */
public record CatalogChangeSetResponse(Long version, Changes changes) {
    public record Changes(List<Long> professionalCategories, List<Long> professionals, List<Long> weeklySchedules) { }
}
