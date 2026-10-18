package com.prog2.catalog.synchronization.domain.model;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;

/** Horario semanal local de atención. No representa ocupaciones ni slots libres. */
public record WeeklySchedule(long id, long professionalId, DayOfWeek dayOfWeek,
        LocalTime startTime, LocalTime endTime, int slotDurationMinutes, boolean enabled,
        Instant createdAt, Instant updatedAt) { }
