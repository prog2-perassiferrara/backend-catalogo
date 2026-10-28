package com.prog2.catalog.synchronization.domain.model;

/** Profesional y categoría de una misma copia, sin cargar sus horarios en el resultado. */
public record ProfessionalSearchEntry(Professional professional, ProfessionalCategory category) { }

