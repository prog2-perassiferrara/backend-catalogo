# SPEC: Documentar APIs con OpenAPI y Swagger UI

**Estado:** Aprobada <!-- Borrador | En revisión | Aprobada -->

**Aprobación del usuario:** 2026-10-30.

<!-- PARA LA PERSONA
Copia esta plantilla como SPEC.md en una carpeta de la funcionalidad.
Pide al agente que la complete contigo según el enunciado y los contratos del backend.
SPEC.md define qué debe cumplirse; PLAN.md desarrolla cómo implementarlo;
TASKS.md organiza los pasos de ejecución.
-->

<!-- PARA EL AGENTE
- Lee las instrucciones del proyecto y los contratos pertinentes. Inspecciona el
  repositorio para comprobar el comportamiento actual.
- Completa esta spec con la persona: investiga lo comprobable y consulta las
  decisiones pendientes. Haz pocas preguntas por vez y actualiza las respuestas.
- No inventes requisitos ni exclusiones. Distingue propuestas de decisiones
  confirmadas y marca como PENDIENTE lo que aún no esté resuelto.
- Considera errores, concurrencia, integración y recuperación cuando apliquen,
  sin ampliar el alcance automáticamente.
- No incluyas diseño de clases, tablas, componentes, archivos o algoritmos:
  esos detalles pertenecen a PLAN.md. Sí registra restricciones explícitas del pedido.
- Mantén el documento breve y proporcional a la funcionalidad. Conserva los comentarios.
- Un documento completo no está aprobado automáticamente. Solicita aprobación
  antes de marcarlo como Aprobada. No implementes durante esta etapa.
- Para cambios de significado posteriores a la aprobación, registra una nota
  breve de cambio, motivo e impacto según AGENTS.md, sin historial separado.
-->

## Qué construimos y para quién

<!-- Qué necesidad resolvemos, quién tiene esa necesidad y qué podrá hacer.
Describe el objetivo en lenguaje de producto. -->

Permitir a desarrolladores y revisores consultar el contrato OpenAPI y probar las
APIs existentes mediante Swagger UI. [Issue #23](https://github.com/prog2-perassiferrara/backend-catalogo/issues/23).
Fuentes: [enunciado, sección 12](../../../../PROJECT_STATEMENT-v1.md) y
[contratos del README](../../../README.md). El enunciado exige documentar contratos;
la elección de OpenAPI/Swagger corresponde a esta mejora.

## Situación actual

<!-- Comportamiento actual relevante, limitación que queremos resolver y
comportamientos existentes que deben conservarse. No describas la arquitectura. -->

Existen seis operaciones propias documentadas en README y planes, con JWT y
errores definidos. No hay documento OpenAPI ni Swagger UI. Se conserva su
comportamiento, autenticación y autorización.

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. -->

- **RF-01:** Ofrecer un contrato OpenAPI consultable y Swagger UI para las seis operaciones indicadas abajo.
- **RF-02:** Describir fielmente parámetros, filtros, paginación, DTO, restricciones, estados HTTP y errores `application/problem+json` con sus códigos reales.
- **RF-03:** Permitir aportar bearer en Swagger UI, distinguiendo JWT de usuario y servicio; cada API mantiene sus validaciones y permisos.
- **RF-04:** Deshabilitar documento y UI por defecto; habilitarlos mediante entorno. Cuando estén habilitados, permitir consultarlos sin JWT.
- **RF-05:** Usar ejemplos ficticios sin secretos incrustados, no ejecutar operaciones de negocio al cargar documentación y documentar su habilitación y utilización.

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

- Crear o rediseñar APIs de negocio; cambiar JWT, permisos o sincronización.
- Presentar APIs centrales como propias o modificar Turnos/KMP.

## Flujo del caso de uso

<!-- Qué usuario, servicio, evento o arranque lo inicia, qué hace el backend
y qué resultado obtiene el actor. Incluye alternativas relevantes. -->

1. Quien ejecuta Catálogo habilita opcionalmente la documentación por entorno.
2. Abre Swagger UI o consulta el contrato sin JWT.
3. Revisa operaciones/esquemas y aporta el bearer correspondiente para probar una API.
4. La API realiza su operación habitual o devuelve el error existente de entrada, seguridad o disponibilidad.

## Datos y reglas de negocio

<!-- Información que recibe o devuelve el servicio, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de procesamiento. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

Todas son GET:

| Operación existente | Perfil requerido por la API |
| --- | --- |
| `/api/catalog/professionals` | Usuario |
| `/api/catalog/categories` | Usuario |
| `/api/catalog/synchronization` | Usuario |
| `/api/internal/catalog/professionals/{professionalId}` | Servicio Turnos |
| `/api/internal/catalog/synchronization` | Servicio Turnos |
| `/api/internal/catedra/token` | Servicio Turnos |

La descripción incluye resultados vacíos, estado del catálogo y diferencias de
disponibilidad entre búsquedas y consultas vigentes. El JWT central no sustituye
los JWT propios. Documentar su respuesta interna no publica un token real.

## Comportamiento del backend y casos alternativos

<!-- Filas orientativas: conserva las pertinentes y añade las necesarias para
esta funcionalidad. No inventes soporte offline, persistencia ni recuperación.
Expresa resultados observables, no mecanismos técnicos. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Documentación deshabilitada | No entregar contrato ni UI. |
| Habilitada, sin JWT | Contrato y UI accesibles; APIs siguen protegidas. |
| Probar API sin JWT válido o permiso adecuado | Rechazo de seguridad existente. |
| Entrada inválida o servicio no disponible | Respuesta real documentada; sin éxitos ficticios. |
| Cargar documentación con cátedra no disponible y proceso activo | Disponible sin solicitudes centrales adicionales. |

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: conservar un contrato HTTP; el diseño de controladores va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

**D-01 y D-02, confirmadas el 2026-10-30:** deshabilitada por defecto,
habilitable por entorno y lectura anónima al habilitarla. Exponer documentación
no amplía permisos de negocio. Dependencia, versión, rutas de documentación y
configuración concreta corresponden al PLAN.

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-01, RF-02:** El contrato incluye las seis operaciones y sus entradas/respuestas reales, incluidos filtros, paginación, estados y errores.
- **CA-02 · RF-01, RF-03:** Swagger UI muestra las operaciones y permite aportar bearer; usuarios y servicio conservan sus permisos y rechazos actuales.
- **CA-03 · RF-04:** Sin habilitación no se entrega documento/UI; habilitados son consultables sin JWT, sin ampliar el acceso anónimo fuera de documentación.
- **CA-04 · RF-03, RF-05:** Documento, ejemplos y configuración de UI no incrustan credenciales ni tokens reales; la entrega técnica sigue restringida al servicio autorizado.
- **CA-05 · RF-05:** Consultar documentación no altera datos, inicia sincronización ni añade solicitudes centrales; sigue disponible con cátedra caída si el proceso está activo.
- **CA-06 · RF-05:** README permite habilitar, localizar y utilizar documentación, distinguiendo credenciales de usuario, servicio y cátedra.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Comparar contrato y operaciones existentes. | Rutas, parámetros, esquemas y errores coincidentes. |
| CA-02 | Abrir UI y probar con los perfiles existentes. | Bearer enviado y permisos conservados. |
| CA-03 | Arrancar deshabilitado/habilitado; consultar anónimamente. | Acceso únicamente según la política acordada. |
| CA-04 | Inspeccionar contrato/ejemplos y acceso al token técnico. | Sin secretos incrustados; autorización preservada. |
| CA-05 | Repetir consultas de documentación observando datos y solicitudes. | Sin efectos de negocio o dependencia central adicional. |
| CA-06 | Seguir README con configuración ficticia. | Habilitación, acceso y autenticación reproducibles. |

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

Ninguna funcional. D-01/D-02 confirmadas; solución técnica pendiente del PLAN.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el alcance está acordado, los flujos son coherentes y todo RF tiene CA
comprobables, incluidos los casos alternativos pertinentes.
Resuelve las dudas y los marcadores pendientes. Mantén el diseño técnico en PLAN.md.
-->
