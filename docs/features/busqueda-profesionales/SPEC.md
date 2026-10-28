# SPEC: Buscar profesionales con filtros locales

**Estado:** Aprobada <!-- Borrador | En revisión | Aprobada -->

**Aprobación del usuario:** 2026-10-28.

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

Permitir al usuario autenticado buscar profesionales desde KMP directamente en
Catálogo, filtrar con datos locales y conocer si podrían estar desactualizados.
Consultar también las categorías locales para presentar el filtro.

Corresponde al [issue #10](https://github.com/prog2-perassiferrara/backend-catalogo/issues/10)
y [CU-04](../../use-cases/CU-04-buscar-profesionales.md). Fuentes:
[enunciado, 4.1, 6, 7 y 11](../../../../PROJECT_STATEMENT-v1.md) y
[anexo, 4 y 7](../../../../INTEGRATION_REFERENCE-v2.md).

## Situación actual

<!-- Comportamiento actual relevante, limitación que queremos resolver y
comportamientos existentes que deben conservarse. No describas la arquitectura. -->

Existen réplica local, sincronización completa/incremental, recuperación,
protección JWT y consultas de agenda y estado de CU-05/CU-06. Faltan búsquedas y
consulta de categorías para KMP. Se conserva la política aprobada: una copia
anterior consistente permite búsquedas con aviso; sin copia no hay búsqueda.
La indisponibilidad de cátedra no impide consultar una copia local legible.

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. -->

- **RF-01:** Buscar profesionales mediante filtros opcionales y combinables de categoría, nombre, estado habilitado y disponibilidad de agenda para una fecha. Cada profesional aparece una sola vez aunque coincida con varios horarios.
- **RF-02:** Consultar categorías locales con identificación, nombre y habilitación para construir el filtro de KMP, conservando las deshabilitadas como datos distinguibles.
- **RF-03:** Entregar datos y estado coherentes con la copia consultada. Mantener búsquedas y categorías disponibles sobre la última copia consistente durante sincronización, recuperación, fallo o vigencia no acreditada, informando posible desactualización. Sin copia confirmada, informar indisponibilidad.
- **RF-04:** Distinguir entrada inválida, ausencia de coincidencias e imposibilidad de consultar. Un catálogo vacío confirmado o un filtro válido sin coincidencias produce éxito vacío; un fallo de lectura no se presenta como ausencia de resultados.
- **RF-05:** Restringir ambas consultas a usuarios autenticados y autorizados mediante la seguridad existente. No entregar credenciales, diagnósticos internos ni acceso a los contratos exclusivos de Turnos.
- **RF-06:** Resolver las consultas con datos y estado locales, sin modificar catálogo, versiones, recibos o sincronización ni iniciar login, reconstrucciones o solicitudes REST/Redis/Kafka de cátedra.
- **RF-07:** Entregar profesionales paginados con orden determinista por apellido, nombre e identificador. La información de paginación corresponde a los mismos filtros y copia que los resultados.

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

- Calcular huecos libres, consultar ocupaciones, crear holds o reservar: corresponden a Turnos.
- Implementar la interfaz KMP o modificar los otros repositorios; aquí se define su contrato consumidor.
- Cambiar las políticas aprobadas de sincronización, recuperación, vigencia o JWT. La búsqueda no sustituye la consulta vigente que Turnos necesita antes de operar.

## Flujo del caso de uso

<!-- Qué usuario, servicio, evento o arranque lo inicia, qué hace el backend
y qué resultado obtiene el actor. Incluye alternativas relevantes. -->

1. KMP consulta las categorías locales para presentar el filtro.
2. El usuario solicita una búsqueda con los filtros elegidos y su identidad autenticada.
3. Catálogo comprueba permisos, entrada válida y existencia de copia confirmada.
4. Aplica conjuntamente los filtros indicados y obtiene la página solicitada.
5. Devuelve profesionales, categoría y habilitación, paginación y estado de la consulta. Sin coincidencias, devuelve éxito vacío; con copia anterior, informa posible desactualización.

Las categorías se pueden consultar independientemente; consultarlas primero no es
una precondición para buscar.

## Datos y reglas de negocio

<!-- Información que recibe o devuelve el servicio, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de procesamiento. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

- Categoría: identificador positivo de cátedra. Un identificador válido inexistente no genera coincidencias. Los filtros se combinan por cumplimiento de todos los indicados.
- **D-01, confirmada el 2026-10-28:** nombre es un fragmento del nombre completo en orden nombre y apellido, sin distinguir mayúsculas ni tildes; «ana go» coincide con «Ana Gómez». No requiere coincidencia exacta ni aproximación por errores ortográficos.
- **D-02, confirmada el 2026-10-28:** sin filtro de estado se incluyen todos los profesionales. Permite pedir solo habilitados o solo deshabilitados; KMP puede solicitar habilitados explícitamente. Se filtra la habilitación del profesional, sin alterar la de su categoría o sus horarios.
- Disponibilidad: criterio ya acordado en CU-04, existe al menos un horario semanal habilitado para el día de semana de la fecha elegida. Otro día u horario deshabilitado no satisface el filtro. Sin fecha no se aplica este filtro; la fecha debe ser válida. No se consultan ocupaciones ni se garantizan slots libres.
- **D-03, confirmada el 2026-10-28:** incluir categorías locales para que KMP conozca sus nombres e identificadores. La búsqueda identifica al profesional, sus nombres y habilitación, y la categoría asociada; campos y representación exactos quedan para PLAN.
- **D-04, confirmada el 2026-10-28:** búsqueda paginada y orden por apellido, nombre e ID. Tamaño inicial, máximo y representación quedan para PLAN. La coherencia se garantiza por respuesta, sin congelar el catálogo entre solicitudes sucesivas. Una página posterior al último resultado es un éxito vacío.
- El estado acompaña a los datos; no exige confiar en una consulta de estado anterior. Conserva el significado de [CU-06](../consulta-agenda-estado/SPEC.md): tener copia no implica vigencia. Una copia vacía con versión confirmada sigue siendo válida.

## Comportamiento del backend y casos alternativos

<!-- Filas orientativas: conserva las pertinentes y añade las necesarias para
esta funcionalidad. No inventes soporte offline, persistencia ni recuperación.
Expresa resultados observables, no mecanismos técnicos. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Copia vigente y filtros válidos | Coincidencias locales paginadas y estado de esa consulta. |
| Sin filtros | Todos los profesionales, paginados. |
| Sin coincidencias, página posterior al final o catálogo vacío confirmado | Éxito vacío con paginación y estado, no indisponibilidad. |
| Copia anterior durante sincronización, recuperación, fallo o tras reinicio | Datos consultables con posible desactualización; sin acreditar vigencia para Turnos. |
| No existe copia confirmada | Indisponibilidad diferenciada de una colección vacía. |
| Categoría, fecha, filtro o paginación inválidos | Error de entrada interpretable, sin detalles internos. |
| MySQL no permite leer datos o estado | Indisponibilidad segura, sin éxito vacío ficticio. |
| REST, Redis o Kafka de cátedra no disponibles | Consultas permitidas con copia local legible y estado apropiado. |
| Lecturas concurrentes con un incremento o snapshot | Respuesta de una copia confirmada, sin datos parciales o mezclados. |
| JWT ausente, inválido o sin permiso de usuario | Rechazo de seguridad antes de entregar datos. |

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: conservar un contrato HTTP; el diseño de controladores va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

Conservar el significado aprobado de disponibilidad y las políticas de
[sincronización](../sincronizacion-incremental/SPEC.md),
[CU-05/06](../consulta-agenda-estado/SPEC.md) y
[seguridad](../seguridad-jwt/SPEC.md). Endpoints, formatos, límites de entrada y
lectura coherente se decidirán en PLAN. No implementar KMP en este issue.

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-01:** Los filtros individuales y combinados devuelven exactamente las coincidencias. Comprobar nombre completo parcial, mayúsculas/tildes y habilitación omitida/verdadera/falsa según D-01/D-02.
- **CA-02 · RF-01:** Para una fecha válida coinciden profesionales con horario habilitado en su día de semana; otro día, horario deshabilitado o ausencia de horarios no satisfacen el filtro. Varios horarios no duplican profesionales; no se prometen huecos libres.
- **CA-03 · RF-02, RF-03:** KMP recibe categorías locales y sus estados, incluidas las deshabilitadas, con el estado de consulta adecuado. Una copia vacía confirmada permite éxito vacío.
- **CA-04 · RF-03, RF-04:** Una búsqueda sin coincidencias, categoría válida inexistente o copia vacía devuelve éxito vacío. Sin copia o sin lectura posible se informa indisponibilidad, también al consultar categorías.
- **CA-05 · RF-03:** Con copia anterior legible y sincronización, recuperación, fallo, comprobación vencida o reinicio sin nueva verificación, ambas consultas siguen disponibles con posible desactualización. No exigen integración central disponible ni habilitan nuevas operaciones de Turnos.
- **CA-06 · RF-01, RF-03, RF-07:** Al intercalar consultas con commits de snapshot o incrementos, datos, estado y totales de cada respuesta corresponden a una sola copia confirmada.
- **CA-07 · RF-07:** Sobre un catálogo sin cambios, las páginas siguen orden por apellido, nombre e ID sin duplicar u omitir coincidencias; los totales corresponden a los filtros y una página posterior al final es un éxito vacío.
- **CA-08 · RF-04, RF-05:** JWT ausente/inválido o sin permiso recibe el rechazo existente. Un usuario autorizado con filtros o paginación inválidos obtiene error de entrada. Ninguna respuesta revela secretos o diagnósticos internos.
- **CA-09 · RF-04, RF-06:** Repetir consultas, incluso fallidas, no altera datos o sincronización, no invoca REST/Redis/Kafka centrales y no inicia login o recuperación. Un fallo de lectura se comunica sin resultados ficticios.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Variar categoría, texto y habilitación, por separado y combinados. | Coincidencias de los filtros acordados; omitir estado incluye ambos. |
| CA-02 | Variar fecha, días, habilitación y cantidad de horarios. | Coincidencia por agenda semanal habilitada, sin duplicados ni garantía de slots libres. |
| CA-03 | Consultar categorías habilitadas/deshabilitadas y copia vacía. | Identificadores, nombres, habilitación y estado del catálogo correctos. |
| CA-04 | Comparar ausencia de coincidencias, categoría inexistente, falta de copia y fallo local. | Éxito vacío diferenciado de indisponibilidad. |
| CA-05 | Consultar en cada condición no vigente y con cátedra no disponible. | Copia anterior consultable con estado apropiado, sin acreditar vigencia para Turnos. |
| CA-06 | Intercalar consultas con commits de actualización/reconstrucción. | Una copia confirmada por respuesta, incluidos estado y totales. |
| CA-07 | Recorrer páginas con apellidos/nombres repetidos y catálogo sin cambios. | Orden determinista, sin omisiones/duplicados, con totales correctos y final vacío. |
| CA-08 | Variar JWT/permisos y entradas inválidas, incluidos fecha y paginación. | Rechazos interpretables, sin filtraciones o acceso interno. |
| CA-09 | Repetir consultas observando datos, progreso y dependencias. | Sin efectos de negocio o solicitudes centrales; fallos locales explícitos. |

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

Ninguna funcional. D-01 a D-04 y SPEC completa aprobadas por el usuario el
2026-10-28. Parámetros y contratos concretos se desarrollarán en PLAN.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el alcance está acordado, los flujos son coherentes y todo RF tiene CA
comprobables, incluidos los casos alternativos pertinentes.
Resuelve las dudas y los marcadores pendientes. Mantén el diseño técnico en PLAN.md.
-->
