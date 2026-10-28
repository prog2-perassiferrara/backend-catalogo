package com.prog2.catalog.synchronization.domain.ports.in;

import com.prog2.catalog.synchronization.domain.model.ProfessionalAgenda;

/** Entrada de CU-05: consulta local para operaciones que requieren catálogo vigente. */
public interface GetProfessionalAgendaUseCase {
    /**
     * @param professionalId identificador positivo conservado de cátedra
     * @return profesional, todos sus horarios y comprobación de vigencia de la misma copia
     * @throws RuntimeException rechazo tipado de aplicación si la entrada, vigencia o lectura no permite responder
     */
    ProfessionalAgenda get(long professionalId);
}
