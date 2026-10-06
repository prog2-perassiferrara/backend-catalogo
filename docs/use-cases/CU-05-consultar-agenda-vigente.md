# CU-05. Consultar profesional y agenda vigente

**Estado:** Revisado con el usuario.

## Identificación

| Campo | Descripción |
| --- | --- |
| Sistema | Backend Catálogo y sincronización. |
| Objetivo | Proporcionar a Turnos la información vigente de un profesional y sus horarios semanales. |
| Actor principal | Backend Turnos. |
| Actores de apoyo | Ninguno durante la consulta: los datos se obtienen de la copia local. |
| Disparador | Turnos necesita validar información del catálogo para una operación nueva. |

## Precondiciones

- Turnos está autenticado y autorizado mediante JWT para consumir el contrato entre servicios.
- Turnos identifica el profesional cuyos datos necesita.

## Flujo principal

1. Turnos solicita los datos del profesional y su agenda.
2. Catálogo comprueba que dispone de una copia consistente y puede informar su vigencia.
3. Catálogo busca el profesional y sus horarios semanales en la copia local.
4. Catálogo entrega la información vigente y los estados habilitados del profesional y sus agendas.
5. Turnos recibe la información necesaria para continuar su operación.

## Flujos alternativos y de excepción

- **2a. Sin copia consistente o sin vigencia verificable:** Catálogo informa la condición. Turnos espera para iniciar operaciones que requieren catálogo vigente; el caso termina sin una respuesta que habilite esas operaciones.
- **3a. Profesional inexistente:** Catálogo informa la ausencia; el caso termina sin datos del profesional.
- **4a. Profesional o agenda deshabilitados:** Catálogo informa esa condición. Turnos no inicia una operación que requiera ese profesional u horario habilitado.

## Postcondiciones

- **Éxito:** Turnos dispone de los datos vigentes y sus estados habilitados para validar la operación.
- **Alternativa o fallo:** Turnos puede distinguir ausencia, deshabilitación y falta de vigencia; no inicia una operación que requiera condiciones no satisfechas.
- La consulta no modifica el catálogo ni crea slots o reservas.

## Reglas y relaciones

- Catálogo es la única fuente local de profesionales y agendas vigentes. Turnos no mantiene otra réplica para nuevas operaciones.
- Turnos combina la agenda recibida con las ocupaciones centrales cuando calcula huecos libres; ese cálculo ocurre fuera de este caso.
- Las operaciones de Turnos que dependen solo de datos propios no requieren esta consulta.
- [CU-06](CU-06-estado-de-sincronizacion.md) describe la información de vigencia necesaria para Turnos; no se impone una llamada independiente.

## Fuentes y pendientes

**Fuente:** [enunciado, secciones 4.1, 4.2, 7 y 8](../../../PROJECT_STATEMENT-v1.md).

**Pendiente para el PLAN:** endpoints, campos, errores y mecanismo de JWT del contrato interno.
