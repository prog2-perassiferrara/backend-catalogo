package com.prog2.catalog.synchronization.infrastructure.client.dto;

/** Representación JSON del horario; las horas todavía no tienen una zona horaria. */
public record WeeklyScheduleResponse(Long id, Long professionalId, String dayOfWeek, String startTime,
        String endTime, Integer slotDurationMinutes, Boolean enabled, String createdAt, String updatedAt) { }
