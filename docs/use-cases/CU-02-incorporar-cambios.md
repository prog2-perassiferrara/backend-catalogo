# CU-02. Incorporar cambios del catálogo

**Estado:** Revisado con el usuario.

## Identificación

| Campo | Descripción |
| --- | --- |
| Sistema | Backend Catálogo y sincronización. |
| Objetivo | Actualizar la copia local mediante cambios incrementales ordenados, incluso si se perdieron notificaciones. |
| Actor iniciador | Servicio de cátedra cuando envía una notificación; la comprobación de versiones también puede iniciarse internamente. |
| Actor de apoyo | Servicio de cátedra, que publica versiones y datos del catálogo. |
| Disparador | Recepción de `CatalogUpdated` o comprobación interna de versiones. |

## Precondiciones

- Existe una copia local con una versión aplicada registrada. Si la copia está vacía, corresponde CU-01.
- La integración técnica está configurada para acceder a las notificaciones y los datos de sincronización.

## Flujo principal

1. Catálogo recibe la notificación o inicia la comprobación de versiones.
2. Catálogo consulta a la cátedra la versión actual y el límite del historial disponible.
3. Catálogo comprueba que puede continuar desde su versión local e identifica las versiones pendientes.
4. Para cada versión pendiente, en orden, Catálogo obtiene los IDs afectados y las entidades correspondientes.
5. Catálogo persiste los cambios de esa unidad y, únicamente cuando todos quedaron aplicados, avanza la versión local. Repite desde el paso 4 mientras quedan versiones pendientes de la secuencia comprobada.
6. Catálogo informa el resultado de la actualización. Si el disparador fue Kafka, confirma el offset después de persistir los efectos.

## Flujos alternativos y de excepción

- **1a. Notificación con `eventId` ya procesado:** Catálogo no repite efectos y puede confirmar el offset. El caso termina; la comprobación independiente de versiones sigue permitiendo detectar atraso.
- **3a. No hay versiones pendientes:** Catálogo conserva la copia y la versión sin cambios y continúa en el paso 6.
- **3b/4a. No puede verificarse la continuidad:** Catálogo ejecuta [CU-03](CU-03-resolver-discontinuidad.md). No salta el incremento ausente; el resultado depende de la recuperación.
- **2a/4b. Falla la lectura de datos:** Catálogo informa el fallo y termina sin avanzar versiones cuyos cambios no se aplicaron.
- **5a. Falla la persistencia:** Catálogo no declara aplicada esa unidad. Las unidades previas completas conservan su versión y sus efectos; se informa el fallo.
- **2a/4b/5a, con copia consistente:** KMP puede buscar con aviso de posible desactualización. Turnos espera para operaciones que requieren catálogo vigente hasta recuperar la sincronización.

## Postcondiciones

- **Éxito:** los incrementos pendientes de la secuencia comprobada quedaron aplicados en orden, con datos y versión coherentes.
- **Sin cambios:** la copia y la versión permanecen iguales.
- **Fallo:** no se avanza sobre una unidad incompleta; queda disponible la última copia consistente y el fallo es observable.

## Reglas y relaciones

- Kafka notifica; Redis publica metadata, IDs afectados y entidades vigentes. Cada `changes:{version}` contiene IDs, no entidades completas.
- `eventId` identifica notificaciones duplicadas. Se ignoran campos adicionales compatibles con el esquema v1.
- La comparación de versiones debe detectar atraso aunque se pierda una notificación.
- CU-03 extiende este caso cuando se detecta una discontinuidad o un estado no verificable.
- La política ante interrupciones fue acordada con el usuario.

## Fuentes y pendientes

**Fuentes:** [enunciado, sección 6.2](../../../PROJECT_STATEMENT-v1.md); [contrato, secciones 14.2 a 14.5, 15.3 y 16](../../../INTEGRATION_REFERENCE-v2.md).

**Pendiente para el PLAN:** comprobación ante notificaciones perdidas, deduplicación y coherencia de versiones cuando las entidades Redis cambian durante la lectura.
