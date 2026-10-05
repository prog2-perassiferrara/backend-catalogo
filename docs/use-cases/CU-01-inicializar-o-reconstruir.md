# CU-01. Inicializar o reconstruir el catálogo

**Estado:** Revisado con el usuario.

## Identificación

| Campo | Descripción |
| --- | --- |
| Sistema | Backend Catálogo y sincronización. |
| Objetivo | Obtener una copia completa de categorías, profesionales y horarios semanales con la versión del snapshot aplicado. |
| Actor iniciador | No hay un actor externo obligatorio: es un proceso automático o invocado desde CU-03. |
| Actor de apoyo | Servicio de cátedra, que entrega el snapshot. |
| Disparador | Inicio con copia local vacía, solicitud de reconstrucción o detección de un estado local no verificable. |

## Precondiciones

- La integración técnica con la cátedra está configurada y Catálogo dispone de las credenciales necesarias.
- Se ha identificado la necesidad de obtener un snapshot completo.
- No se exige una copia local previa: el caso también cubre la primera inicialización.

## Flujo principal

1. Catálogo solicita un snapshot completo al servicio de cátedra.
2. La cátedra entrega categorías, profesionales, horarios semanales y `snapshotVersion`, incluidas entidades habilitadas y deshabilitadas.
3. Catálogo aplica las tres colecciones como una copia consistente.
4. Una vez aplicadas todas las colecciones, Catálogo registra `snapshotVersion` como versión local.
5. Catálogo informa que la aplicación del snapshot terminó correctamente. Las consultas pueden usar la nueva copia de acuerdo con su estado de sincronización.

## Flujos alternativos y de excepción

- **2a. No se obtiene el snapshot:** Catálogo informa el fallo y termina sin modificar la versión aplicada. Si existe una copia anterior consistente, KMP puede buscar con aviso de posible desactualización; Turnos espera para operaciones que requieren catálogo vigente.
- **3a. Falla la aplicación del snapshot:** Catálogo no publica una copia parcial ni una versión correspondiente a datos incompletos. Informa el fallo y termina con la misma política de consultas que en 2a.
- **3b. Hay consultas durante la reconstrucción:** si existe una copia anterior consistente, KMP puede buscar sobre ella con aviso; Turnos espera para operaciones que requieren catálogo vigente. El flujo principal continúa.
- **2a/3a/3b, sin copia anterior consistente:** las búsquedas no pueden resolverse hasta disponer de una copia consistente.

## Postcondiciones

- **Éxito:** las tres colecciones almacenadas corresponden al snapshot aplicado y la versión local es exactamente su `snapshotVersion`.
- **Fallo:** no se declara aplicada una versión parcial; se conserva la última copia consistente, si existe, y se informa el fallo.

## Reglas y relaciones

- El snapshot no incluye historial incremental. Los cambios posteriores se atienden mediante [CU-02](CU-02-incorporar-cambios.md).
- [CU-03](CU-03-resolver-discontinuidad.md) incluye este caso para reconstruir la copia.
- Aplicar un snapshot establece una copia consistente para su versión; la vigencia frente a cambios posteriores se evalúa mediante CU-02 y [CU-06](CU-06-estado-de-sincronizacion.md).
- La política de búsquedas con aviso y espera de Turnos fue acordada con el usuario.

## Fuentes y pendientes

**Fuentes:** [enunciado, sección 6.1](../../../PROJECT_STATEMENT-v1.md); [contrato, sección 7](../../../INTEGRATION_REFERENCE-v2.md).

**Pendiente para el PLAN:** estrategia de reemplazo y concurrencia.
