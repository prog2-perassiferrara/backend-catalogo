# CU-04. Buscar profesionales

**Estado:** Revisado con el usuario.

## Identificación

| Campo | Descripción |
| --- | --- |
| Sistema | Backend Catálogo y sincronización. |
| Objetivo | Encontrar profesionales que cumplen los filtros elegidos usando la copia local. |
| Actor principal | Usuario final, que accede mediante KMP directamente a Catálogo. |
| Actores de apoyo | Ninguno: la búsqueda se resuelve localmente. |
| Disparador | El usuario solicita una búsqueda desde KMP. |

## Precondiciones

- El usuario está autenticado y autorizado para consultar el catálogo.
- Existe una copia local consistente. No es necesario que la cátedra esté disponible para resolver la búsqueda.

## Flujo principal

1. El usuario solicita una búsqueda desde KMP con los filtros elegidos.
2. Catálogo recibe la solicitud y aplica los filtros de categoría, nombre, estado habilitado y disponibilidad de agenda que se hayan indicado.
3. Si se solicita disponibilidad, Catálogo comprueba si existe una agenda habilitada para el día de la semana de la fecha elegida.
4. Catálogo devuelve los profesionales que cumplen los filtros. KMP puede conocer si los datos consultados podrían estar desactualizados.
5. KMP presenta los resultados al usuario.

## Flujos alternativos y de excepción

- **4a. No hay coincidencias:** Catálogo devuelve un resultado vacío. KMP informa que no se encontraron profesionales; el caso termina correctamente.
- **4b. Sincronización en curso o fallida:** Catálogo resuelve la búsqueda sobre la última copia consistente y KMP muestra el aviso de posible desactualización. Continúa en el paso 5.
- **Antes del paso 2, sin copia consistente:** Catálogo informa que la búsqueda no puede resolverse; KMP informa la indisponibilidad y el caso termina sin resultados.

## Postcondiciones

- **Éxito:** el usuario recibe los profesionales que cumplen los filtros, o un resultado vacío, junto con el aviso de desactualización cuando corresponde.
- **Fallo:** se informa que la búsqueda no pudo resolverse; no se confunde la indisponibilidad con una búsqueda sin coincidencias.
- La búsqueda no modifica el catálogo ni consulta a la cátedra por cada solicitud.

## Reglas y relaciones

- Los filtros mínimos son categoría, nombre, estado habilitado y disponibilidad.
- La disponibilidad de agenda para una fecha no garantiza huecos libres. Turnos calcula los huecos reales con ocupaciones centrales.
- Las entidades deshabilitadas permanecen en la réplica, pero no se interpretan como una agenda habilitada.
- KMP accede directamente a Catálogo por decisión del usuario.
- Por decisión del usuario, el filtro de disponibilidad significa agenda habilitada para el día de la semana de la fecha elegida. La distinción entre agenda y huecos libres se fundamenta en las responsabilidades de Catálogo y Turnos definidas en el enunciado.
- [CU-06](CU-06-estado-de-sincronizacion.md) describe la información de estado que necesita KMP; la forma de entregarla queda para el PLAN.

## Fuentes y pendientes

**Fuente:** [enunciado, secciones 4.1 y 6](../../../PROJECT_STATEMENT-v1.md).

**Pendiente funcional:** ninguno registrado en esta revisión.
