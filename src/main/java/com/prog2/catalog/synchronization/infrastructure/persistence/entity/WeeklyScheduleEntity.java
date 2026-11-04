package com.prog2.catalog.synchronization.infrastructure.persistence.entity;

import lombok.Getter;
import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import com.prog2.catalog.synchronization.domain.model.WeeklySchedule;

/** Fila de la réplica local. El ID y los tiempos proceden de cátedra, sin generación local. */
@Entity
@Table(name = "weekly_schedule")
public class WeeklyScheduleEntity {
    @Id
    @Column(name = "id", nullable = false)
    @Getter
    private Long id;

    @Column(name = "professional_id", nullable = false)
    @Getter
    private long professionalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    @Getter
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    @Getter
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    @Getter
    private LocalTime endTime;

    @Column(name = "slot_duration_minutes", nullable = false)
    @Getter
    private int slotDurationMinutes;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    @Getter
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @Getter
    private Instant updatedAt;

    protected WeeklyScheduleEntity() { }

    /** Copia un horario completo, evitando intercambiar los nueve valores al construir la fila. */
    public WeeklyScheduleEntity(WeeklySchedule schedule) {
        this.id = schedule.id();
        this.professionalId = schedule.professionalId();
        this.dayOfWeek = schedule.dayOfWeek();
        this.startTime = schedule.startTime();
        this.endTime = schedule.endTime();
        this.slotDurationMinutes = schedule.slotDurationMinutes();
        this.enabled = schedule.enabled();
        this.createdAt = schedule.createdAt();
        this.updatedAt = schedule.updatedAt();
    }

    public boolean getEnabled() {
        return enabled;
    }

}
