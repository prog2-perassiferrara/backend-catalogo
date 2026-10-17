package com.prog2.catalog.synchronization.infrastructure.client.dto;

import java.util.List;

/** DTO externo con referencias anulables para distinguir campos ausentes de valores válidos. */
public record CatalogSnapshotResponse(Long snapshotVersion, String generatedAt,
        List<ProfessionalCategoryResponse> professionalCategories,
        List<ProfessionalResponse> professionals, List<WeeklyScheduleResponse> weeklySchedules) { }
