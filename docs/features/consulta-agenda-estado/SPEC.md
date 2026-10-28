# SPEC: Consultar profesional, agenda y estado del catálogo

**Estado:** Aprobada <!-- Borrador | En revisión | Aprobada -->

**Aprobación del usuario:** 2026-10-26.

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

Permitir que Turnos obtenga un profesional y sus horarios semanales desde la copia
local, con información suficiente para decidir si puede iniciar una operación que
requiere catálogo vigente. Informar a KMP y Turnos el estado apropiado para cada
consumidor, incluso cuando todavía no existe una copia consistente.

Agrupa [#11](https://github.com/prog2-perassiferrara/backend-catalogo/issues/11) y
[#12](https://github.com/prog2-perassiferrara/backend-catalogo/issues/12), manteniendo
[CU-05](../../use-cases/CU-05-consultar-agenda-vigente.md) y
[CU-06](../../use-cases/CU-06-estado-de-sincronizacion.md) diferenciados.
Fuentes: [enunciado, 4.1, 4.2, 7, 8 y 9](../../../../PROJECT_STATEMENT-v1.md) y
[anexo, 4, 7 y 13](../../../../INTEGRATION_REFERENCE-v2.md).
Consumidor relacionado: [Turnos, CU-03](../../../../backend-turnos/docs/use-cases/CU-03-consultar-disponibilidad.md).

## Situación actual

<!-- Comportamiento actual relevante, limitación que queremos resolver y
comportamientos existentes que deben conservarse. No describas la arquitectura. -->

Catálogo ya obtiene acceso técnico, aplica snapshots, incorpora incrementos y
recupera discontinuidades. Conserva datos, versión aplicada, última versión
comprobada y fecha de comprobación. La comprobación periódica está acordada cada
30 segundos después de finalizar la anterior. Una comprobación histórica no
demuestra vigencia indefinida.

Existe protección JWT y entrega interna del token central a Turnos, pero no las
consultas HTTP de profesional, agenda o estado funcional. Se conservan las
políticas de [sincronización](../sincronizacion-incremental/SPEC.md) y
[seguridad](../seguridad-jwt/SPEC.md): búsquedas de KMP sobre copia anterior con
aviso; espera de Turnos cuando no puede acreditarse vigencia; sin copia, no hay
datos de catálogo para consultar. Health informa salud técnica, no vigencia.

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. -->

- **RF-01:** Permitir a Turnos autenticado y autorizado consultar un profesional por su identificador de cátedra, junto con sus horarios semanales. Obtener esos datos de la réplica local y conservar sus identificadores y estados habilitados.
- **RF-02:** Entregar los datos de CU-05 como información vigente solo si existe copia consistente y su vigencia puede acreditarse conforme a D-01. Durante actualización, reconstrucción, espera de recuperación, fallo o estado no verificable, informar indisponibilidad para operaciones nuevas de Turnos que requieren vigencia, sin presentar la copia anterior como habilitante.
- **RF-03:** Distinguir profesional inexistente, profesional deshabilitado, agendas deshabilitadas y profesional sin agendas. Informar los estados habilitados sin confundirlos con ausencia de copia o fallo del servicio; corresponde a Turnos decidir la aplicabilidad del horario y combinarlo con ocupaciones.
- **RF-04:** Comunicar el estado del catálogo a consumidores autenticados y autorizados, aunque no exista copia o la sincronización haya fallado. KMP recibe un resumen que permite distinguir datos consultables, posible desactualización y ausencia de copia; Turnos recibe además la versión aplicada y la información de comprobación necesaria para interpretar la vigencia.
- **RF-05:** Mantener coherencia entre profesional, agendas y versión informada durante consultas concurrentes con la sincronización. No mezclar datos de copias distintas ni presentar como aplicada una versión todavía en curso. Para CU-05, entregar los datos y su condición de vigencia conjuntamente, sin exigir confiar en una consulta de estado anterior independiente.
- **RF-06:** Las consultas se resuelven con datos y estado locales, sin llamadas a REST, Redis o Kafka de cátedra por solicitud. No modifican el catálogo, su versión, recibos de eventos ni el progreso de sincronización. Repetirlas no produce efectos de negocio, no inicia login técnico ni reconstrucciones y no sustituye la política de comprobación y recuperación existente.
- **RF-07:** Conservar la autenticación y separación de permisos existentes: la consulta de profesional y agenda de esta funcionalidad corresponde al servicio Turnos; el estado resumido corresponde a usuarios autorizados vía KMP. Rechazar identidades sin permiso y producir errores interpretables sin secretos ni detalles internos. No confundir un fallo de lectura con un resultado vacío o con un estado vigente.

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

- Búsquedas y filtros de CU-04, que se trabajarán después de CU-05/CU-06 según el orden aceptado por el usuario.
- Cálculo de huecos libres, consulta de ocupaciones, holds y reservas: pertenecen a Turnos.
- Otra réplica de profesionales/agendas en Turnos o acceso directo a datos internos entre backends.
- Cambiar los contratos de JWT, renovar automáticamente el acceso técnico o administrar el Kafka central. Se conservan las políticas aprobadas.

## Flujo del caso de uso

<!-- Qué usuario, servicio, evento o arranque lo inicia, qué hace el backend
y qué resultado obtiene el actor. Incluye alternativas relevantes. -->

**CU-05:**

1. Turnos presenta su identidad de servicio y solicita un profesional.
2. Catálogo comprueba autorización y validez del identificador.
3. Determina si hay copia consistente y vigencia verificable. Si no puede acreditarlas, informa la condición sin habilitar la operación dependiente de Turnos.
4. Obtiene el profesional y sus horarios de una copia coherente. Si el profesional no existe, informa su ausencia.
5. Entrega datos, estados habilitados, versión e información de vigencia correspondientes a esa misma consulta. Turnos valida las condiciones y continúa su caso de uso.

**CU-06:**

1. KMP o Turnos solicita información de estado con la identidad autorizada correspondiente.
2. Catálogo determina existencia de copia, situación de sincronización y comprobación de vigencia disponible.
3. Entrega el detalle permitido para ese consumidor. Sin copia o con sincronización fallida, comunica esa condición; si no puede determinar el estado, informa indisponibilidad en lugar de inventarlo.

## Datos y reglas de negocio

<!-- Información que recibe o devuelve el servicio, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de procesamiento. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

- El identificador solicitado es el ID positivo del profesional en cátedra. Un valor incompatible no se trata como profesional inexistente.
- El profesional conserva identificador, referencia a categoría, nombre, apellido y habilitación. Los horarios conservan identificador, referencia al profesional, día de semana, hora inicial/final, duración del turno y habilitación. Campos y representación HTTP se concretan con el consumidor en PLAN.
- Se incluyen los estados habilitados de las agendas; no se oculta una deshabilitada para aparentar que nunca existió. Una colección de agendas vacía es válida si el profesional existe.
- Los horarios semanales son horas locales de atención, no instantes UTC. Los instantes de comprobación/versionado se distinguen de esas horas. No se calculan slots ni se promete disponibilidad real.
- La existencia de copia depende de que haya una versión aplicada confirmada. Un catálogo vacío con versión es una copia válida; tablas vacías sin copia confirmada no permiten afirmar que el profesional no existe.
- La versión aplicada, la generación del snapshot y la última comprobación de vigencia tienen significados distintos. Un snapshot aplicado o una marca persistida de `CURRENT` no demuestran por sí solos vigencia actual.
- Tras reiniciar, una comprobación del proceso anterior no habilita nuevas operaciones de Turnos. Se requiere completar la comprobación correspondiente a la ejecución actual.
- La condición informada describe lo comprobado al resolver la consulta. No bloquea cambios futuros del catálogo ni equivale a un hold o una autorización permanente para reservar.
- Durante sincronización o fallo, CU-06 permite indicar a KMP que existe una copia anterior consultable con aviso. La búsqueda efectiva queda en CU-04. Los diagnósticos técnicos detallados permanecen en operación, sin divulgar credenciales, JWT, configuración privada ni errores de dependencias.
- **D-01, confirmada por el usuario el 2026-10-26:** aceptar la última comprobación exitosa de la ejecución actual durante un máximo de **60 segundos, incluido el límite**, con copia consistente, versión aplicada igual a la comprobada, integración utilizable y sin trabajo de sincronización/recuperación o fallo que invalide esa condición. Superado el plazo, no acreditar vigencia. Un fallo o trabajo en curso invalida la condición aunque la comprobación todavía tenga menos de 60 segundos. Las consultas usan el estado local, sin comprobar Redis por solicitud; se conserva el seguimiento periódico existente. Es una decisión del proyecto, no un intervalo impuesto por cátedra, y acepta que pueda haber un cambio central aún no detectado entre comprobaciones.

## Comportamiento del backend y casos alternativos

<!-- Filas orientativas: conserva las pertinentes y añade las necesarias para
esta funcionalidad. No inventes soporte offline, persistencia ni recuperación.
Expresa resultados observables, no mecanismos técnicos. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Copia consistente con vigencia acreditada y profesional existente | Turnos recibe datos, agendas, habilitación y versión coherentes. |
| Profesional inexistente en copia vigente | Informar ausencia, diferenciada de indisponibilidad. |
| Profesional deshabilitado o agendas deshabilitadas | Informar datos y condición; no presentar esas agendas como habilitadas. |
| Profesional existente sin agendas | Colección vacía válida, sin prometer huecos libres. |
| No hay copia confirmada | Estado informa ausencia; CU-05 no entrega datos habilitantes. |
| Sincronización, reconstrucción, espera de recuperación o fallo | KMP conoce la posible desactualización; Turnos espera para operaciones que requieren vigencia. |
| Comprobación de más de 60 segundos, ausente o no verificable; acceso técnico inutilizable | No afirmar vigencia, aunque el último estado persistido indique `CURRENT`. |
| Identificador inválido | Error de entrada; no confundirlo con profesional inexistente. |
| Identidad no autenticada o sin permiso | Rechazo de seguridad antes de exponer datos o estado funcional. |
| MySQL no permite determinar datos/estado | Informar indisponibilidad, sin listas vacías o estado vigente ficticios ni errores internos. |
| Consulta concurrente con un commit de sincronización | Datos y versión de una misma copia; no responder con una mezcla ni habilitar Turnos con una actualización detectada en curso. |
| Reinicio con copia persistida | Conservar la copia y distinguirla de una comprobación de vigencia de la ejecución actual. |

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: conservar un contrato HTTP; el diseño de controladores va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

El usuario pidió avanzar conjuntamente con CU-05/CU-06 antes de CU-04, sin esperar
la resolución del topic Kafka central. La comprobación de ese consumo sigue
pendiente; esta funcionalidad no lo declara resuelto.

Conservar las políticas aprobadas de copia anterior, recuperación y seguridad.
Turnos es consumidor del contrato, no propietario de otra réplica. Endpoints,
permisos concretos, formato de respuestas/errores y transporte del estado quedan
para PLAN; CU-06 no obliga a crear un endpoint independiente. Sus contratos se
coordinarán con Turnos y KMP sin modificar esos repositorios en esta etapa.

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-01, RF-02, RF-05:** Con copia vigente y servicio autorizado, consultar un profesional devuelve sus datos, horarios, habilitación, versión y comprobación correspondientes a la misma copia.
- **CA-02 · RF-01, RF-03, RF-07:** Con copia vigente, un ID válido inexistente informa ausencia; un ID inválido informa error de entrada. Sin copia o sin lectura verificable, no se responde como si el profesional simplemente no existiera.
- **CA-03 · RF-03:** Un profesional deshabilitado, agendas deshabilitadas y un profesional sin agendas conservan sus condiciones distinguibles; no se inventan horarios habilitados ni slots libres.
- **CA-04 · RF-02, RF-04:** Con actualización, recuperación, fallo o ausencia de copia, CU-05 no habilita nuevas operaciones dependientes de vigencia y CU-06 informa la situación. KMP puede distinguir copia anterior consultable con aviso de ausencia de copia.
- **CA-05 · RF-02, RF-04:** Con todas las demás condiciones de D-01 satisfechas, una comprobación de hasta 60 segundos permite acreditar vigencia y una de mayor antigüedad no. Comprobar justo antes, en el límite y justo después, además de ausencia de comprobación, reinicio y acceso técnico inutilizable. Un fallo o actualización/recuperación en curso impide acreditar vigencia incluso antes del límite; ninguna condición desconocida se convierte en vigente.
- **CA-06 · RF-04, RF-07:** El estado es consultable con el permiso correspondiente aunque no exista copia o haya fallado la sincronización; si la lectura del propio estado falla, se informa indisponibilidad. KMP recibe resumen y Turnos información de vigencia, sin secretos ni diagnósticos internos.
- **CA-07 · RF-01, RF-07:** JWT ausente/inválido y JWT admitido sin permiso reciben el rechazo de seguridad existente. Un usuario final no obtiene por esa identidad la consulta interna de CU-05 ni el token técnico central.
- **CA-08 · RF-02, RF-05:** Hacer consultas mientras se aplica un incremento o reemplazo nunca combina profesionales/agendas de una copia con la versión de otra; una actualización detectada en curso no se presenta como catálogo habilitante para Turnos.
- **CA-09 · RF-06, RF-07:** Repetir consultas y provocar fallos de lectura no altera datos, versión, recibos ni progreso de sincronización, no inicia login o reconstrucción, no invoca REST/Redis/Kafka de cátedra por solicitud y no filtra errores de dependencias.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Consultar como Turnos autorizado sobre una copia vigente conocida. | Datos y agendas con IDs, habilitación, versión y comprobación coherentes. |
| CA-02 | Variar ID inválido/inexistente y repetir sin copia o ante fallo de lectura. | Diferenciar entrada inválida, ausencia e indisponibilidad. |
| CA-03 | Consultar profesional deshabilitado, agendas deshabilitadas y profesional sin agendas. | Conservar estados y colección vacía legítima; ningún cálculo de slots. |
| CA-04 | Consultar profesional/estado durante actualización, recuperación, fallo y ausencia de copia. | Turnos no habilitado; resumen apropiado para KMP. |
| CA-05 | Consultar justo antes, a los 60 segundos y justo después de una comprobación; repetir sin comprobación, tras reinicio, con acceso inutilizable y con fallo/trabajo en curso antes del límite. | Solo comprobación de la ejecución actual de hasta 60 segundos, junto con las demás condiciones de D-01, permite CU-05 como información habilitante. |
| CA-06 | Consultar estado con/sin copia y con fallo funcional; simular imposibilidad de leer el estado. | Situación resumida o indisponibilidad verificable, sin secretos ni falso éxito. |
| CA-07 | Variar JWT y permisos de usuario/servicio para ambas consultas. | Rechazos existentes y separación de accesos sin entrega de credenciales técnicas. |
| CA-08 | Intercalar consultas con la aplicación de snapshot e incremento. | Una copia coherente por respuesta y ninguna actualización en curso presentada como vigente. |
| CA-09 | Repetir consultas exitosas y fallidas, observando efectos y dependencias invocadas. | Sin cambios de negocio, login, recuperación ni llamadas a cátedra por consultar. |

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

Ninguna funcional. D-01 y la SPEC completa están aprobadas por el usuario.
Endpoints, campos exactos, valores de estado, errores, permisos y mecanismo de lectura
coherente se resolverán en PLAN, sin fijarlos anticipadamente en esta SPEC.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el alcance está acordado, los flujos son coherentes y todo RF tiene CA
comprobables, incluidos los casos alternativos pertinentes.
Resuelve las dudas y los marcadores pendientes. Mantén el diseño técnico en PLAN.md.
-->
