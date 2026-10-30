# SPEC: Parametrizar la integración y sincronización

**Estado:** Aprobada <!-- Borrador | En revisión | Aprobada -->

**Aprobación del usuario:** 2026-10-29.

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

Permitir ajustar tiempos y presupuestos operativos sin recompilar Catálogo.
[Issue #22](https://github.com/prog2-perassiferrara/backend-catalogo/issues/22);
fuentes: [enunciado](../../../../PROJECT_STATEMENT-v1.md) y
[anexo](../../../../INTEGRATION_REFERENCE-v2.md).

## Situación actual

<!-- Comportamiento actual relevante, limitación que queremos resolver y
comportamientos existentes que deben conservarse. No describas la arquitectura. -->

Integración y sincronización funcionan, pero varios límites y tiempos siguen
fijos en código. Se conserva su comportamiento; cambia cómo se configuran.

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. -->

- **RF-01:** Configurar intentos/ciclos, pausas, timeouts de clientes de cátedra y comprobación periódica para autenticación, snapshot, incrementos y recuperación, sin recompilar.
- **RF-02:** Conservar valores actuales por defecto y admitir sobrescritura opcional por entorno, con Compose o fuera de Docker.
- **RF-03:** Impedir completar el arranque con configuración inválida, con diagnóstico seguro y sin sustituirla silenciosamente por valores predeterminados.
- **RF-04:** Conservar contratos, seguridad, consistencia, idempotencia y tratamiento de fallos; los presupuestos siguen acotados.
- **RF-05:** Documentar parámetros, unidades, valores predeterminados, restricciones y utilización.

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

- Cambiar HTTP/JWT, permisos, criterio de vigencia o políticas de recuperación.
- Recarga dinámica, renovación de sesión técnica, OpenAPI o reorganización de slices.
- Parametrizar todas las constantes, alterar secretos obligatorios o sustituir datos dinámicos entregados por cátedra.

## Flujo del caso de uso

<!-- Qué usuario, servicio, evento o arranque lo inicia, qué hace el backend
y qué resultado obtiene el actor. Incluye alternativas relevantes. -->

1. Quien ejecuta Catálogo proporciona sobrescrituras opcionales.
2. El backend resuelve y valida la configuración al arrancar.
3. La aplica si es válida; de lo contrario, rechaza el arranque con diagnóstico seguro.
4. Cambiar valores requiere reiniciar, sin recompilar.

## Datos y reglas de negocio

<!-- Información que recibe o devuelve el servicio, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de procesamiento. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

- El entorno prevalece sobre los valores predeterminados; omitirlo los conserva.
- Rechazar valores malformados, fuera de rango, presupuestos ilimitados o timeouts deshabilitados.
- Inventario, nombres, unidades y rangos quedan para PLAN; los valores predeterminados no cambian.

## Comportamiento del backend y casos alternativos

<!-- Filas orientativas: conserva las pertinentes y añade las necesarias para
esta funcionalidad. No inventes soporte offline, persistencia ni recuperación.
Expresa resultados observables, no mecanismos técnicos. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Sin sobrescrituras | Mismos tiempos y límites actuales. |
| Sobrescritura válida | Aplicada desde el arranque. |
| Configuración inválida | Arranque rechazado, sin secretos. |
| Fallos, duplicados, concurrencia o reinicio | Mismas garantías actuales con límites configurados. |
| Cambio durante la ejecución | Requiere reinicio; no hay recarga dinámica. |

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: conservar un contrato HTTP; el diseño de controladores va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

Valores predeterminados en `application.yaml`, entorno y Compose; configuración
en infraestructura y valores por constructor, según el issue. Conservar
[integración técnica](../integracion-tecnica/SPEC.md),
[snapshot](../snapshot-catalogo/SPEC.md) y
[sincronización incremental](../sincronizacion-incremental/SPEC.md).

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-01, RF-02:** Sin sobrescrituras, los valores efectivos coinciden con los actuales.
- **CA-02 · RF-01, RF-02:** Valores válidos distintos de los predeterminados gobiernan clientes y ciclos con Compose y fuera de Docker, sin recompilar.
- **CA-03 · RF-03:** Un valor inválido impide completar el arranque; se identifica el parámetro sin secretos ni sustitución silenciosa.
- **CA-04 · RF-01, RF-02:** El mismo artefacto aplica otra sobrescritura al reiniciar, sin recarga dinámica.
- **CA-05 · RF-04:** Fallos temporales/terminales, duplicados, discontinuidades y persistencia incierta conservan su tratamiento, presupuestos acotados y coherencia de datos/versión.
- **CA-06 · RF-05:** Cada parámetro tiene documentados significado, variable, unidad, valor predeterminado, restricciones y uso en ambos modos de ejecución.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Comparar configuración predeterminada y límites existentes. | Valores actuales conservados. |
| CA-02 | Sobrescribir valores y ejercitar clientes/ciclos en ambos modos. | Límites configurados aplicados, sin recompilar. |
| CA-03 | Arrancar con valores malformados o fuera de rango. | Rechazo y diagnóstico seguro del parámetro. |
| CA-04 | Reiniciar el mismo artefacto con otra sobrescritura. | Nuevo valor aplicado solo en el nuevo arranque. |
| CA-05 | Repetir fallos y recuperación con presupuestos acotados. | Reglas y datos conservados. |
| CA-06 | Contrastar documentación y ejemplos con los parámetros efectivos. | Instrucciones completas y coherentes. |

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

Ninguna funcional. Propiedades y validaciones se concretan en PLAN.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el alcance está acordado, los flujos son coherentes y todo RF tiene CA
comprobables, incluidos los casos alternativos pertinentes.
Resuelve las dudas y los marcadores pendientes. Mantén el diseño técnico en PLAN.md.
-->
