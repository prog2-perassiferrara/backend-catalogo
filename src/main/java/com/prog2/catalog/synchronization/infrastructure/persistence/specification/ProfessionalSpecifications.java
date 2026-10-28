package com.prog2.catalog.synchronization.infrastructure.persistence.specification;

import java.time.LocalDate;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.jpa.domain.Specification;
import com.prog2.catalog.synchronization.domain.model.ProfessionalSearchCriteria;
import com.prog2.catalog.synchronization.infrastructure.persistence.entity.ProfessionalEntity;
import com.prog2.catalog.synchronization.infrastructure.persistence.entity.WeeklyScheduleEntity;

/** Specification: combina predicados Criteria sin construir SQL con texto del usuario. */
public final class ProfessionalSpecifications {
    private ProfessionalSpecifications() { }

    /** @return los filtros indicados combinados mediante AND, sin duplicar filas por horarios */
    public static Specification<ProfessionalEntity> matching(ProfessionalSearchCriteria criteria) {
        return Specification.allOf(category(criteria.categoryId()), name(criteria.name()),
                enabled(criteria.enabled()), availableOn(criteria.availableOn()));
    }

    private static Specification<ProfessionalEntity> category(Long id) {
        return (root, query, builder) -> id == null ? builder.conjunction() : builder.equal(root.get("categoryId"), id);
    }

    private static Specification<ProfessionalEntity> enabled(Boolean enabled) {
        return (root, query, builder) -> enabled == null ? builder.conjunction() : builder.equal(root.get("enabled"), enabled);
    }

    private static Specification<ProfessionalEntity> name(String text) {
        return (root, query, builder) -> {
            if (text == null) { return builder.conjunction(); }
            // Hibernate es el proveedor fijado: adaptación técnica, sin elegir comportamientos de negocio por tipo.
            var hibernate = (HibernateCriteriaBuilder) builder;
            var fullName = builder.concat(builder.concat(root.get("firstName"), " "), root.get("lastName"));
            String literal = text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
            return builder.like(hibernate.collate(fullName, "utf8mb4_0900_ai_ci"), "%" + literal + "%", '!');
        };
    }

    private static Specification<ProfessionalEntity> availableOn(LocalDate date) {
        return (root, query, builder) -> {
            if (date == null) { return builder.conjunction(); }
            var exists = query.subquery(Long.class);
            var schedule = exists.from(WeeklyScheduleEntity.class);
            exists.select(schedule.get("id")).where(
                    builder.equal(schedule.get("professionalId"), root.get("id")),
                    builder.equal(schedule.get("dayOfWeek"), date.getDayOfWeek()),
                    builder.isTrue(schedule.get("enabled")));
            return builder.exists(exists);
        };
    }
}

