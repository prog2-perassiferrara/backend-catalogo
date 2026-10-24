# SPEC: Incorporar incrementos y recuperar discontinuidades

**Estado:** Aprobada <!-- Borrador | En revisión | Aprobada -->

**Aprobación del usuario:** 2026-10-19.

<!-- PARA LA PERSONA
SPEC define comportamiento y aceptación; PLAN desarrolla la solución técnica;
TASKS organiza la ejecución. Esta funcionalidad corresponde al backend.
-->

<!-- PARA EL AGENTE
- Contrasta el enunciado, el contrato y los CU aprobados; inspecciona el estado real.
- Completa esta spec con la persona y consulta las decisiones pendientes.
- Distingue requisitos, propuestas y pendientes; no inventes decisiones.
- Conserva secciones y comentarios, adaptando los escenarios al backend.
- El diseño de clases, transacciones, almacenamiento y algoritmos pertenece al PLAN.
- Mantén el documento proporcional y no marques aprobación sin confirmación.
- Aprobar esta SPEC no autoriza por sí solo a implementar.
-->

## Qué construimos y para quién

<!-- Necesidad, destinatarios y resultado observable. -->

Mantener la copia local de Catálogo actualizada mediante Kafka y Redis, y recuperar
una copia verificable mediante snapshot cuando no sea seguro continuar. KMP y
Turnos utilizarán esa copia conforme a las políticas de vigencia ya acordadas.

Agrupa los issues [#8](https://github.com/prog2-perassiferrara/backend-catalogo/issues/8)
y [#9](https://github.com/prog2-perassiferrara/backend-catalogo/issues/9), conservando
[CU-02](../../use-cases/CU-02-incorporar-cambios.md) y
[CU-03](../../use-cases/CU-03-resolver-discontinuidad.md) separados.
Fuentes: [enunciado, 6.2 y 8](../../../../PROJECT_STATEMENT-v1.md) y
[contrato, 14, 15.1 a 15.3, 16 y 18](../../../../INTEGRATION_REFERENCE-v2.md).

## Situación actual

<!-- Comportamiento existente y reglas que deben conservarse. -->

Catálogo obtiene acceso técnico y ejecuta [CU-01](../snapshot-catalogo/SPEC.md) al
arrancar. Conserva snapshot y versión de forma consistente, pero todavía no consume
Kafka, lee incrementos de Redis ni comprueba atraso posterior al snapshot.

Se conservan la [política de acceso técnico](../integracion-tecnica/SPEC.md), la
protección JWT y las garantías del snapshot. Si el snapshot inicial falla, el
backend permanece activo; sin copia confirmada no habilita búsquedas. CU-03 añade
la coordinación de nuevos ciclos de recuperación durante la ejecución. Tener una
copia aplicada no demuestra vigencia permanente. No se implementan todavía las
APIs de consulta del catálogo.

## Dentro del alcance

<!-- Requisitos con IDs estables, sin diseño de implementación. -->

- **RF-01:** Con integración técnica habilitada y copia confirmada, atender `CatalogUpdated` y comprobar versiones independientemente de recibir mensajes. Detectar cambios posteriores al snapshot, incluidos los producidos durante arranque, desconexión o reinicio, sin depender de una búsqueda de usuario para iniciar la comprobación.
- **RF-02:** Interpretar el esquema v1 de las notificaciones y reconocer duplicados por `eventId`, también después de reiniciar. Ignorar campos adicionales compatibles. Una notificación antigua o repetida no vuelve a aplicar cambios ni retrocede la versión; la comprobación independiente sigue activa. Los mensajes incompatibles no modifican el catálogo ni se presentan como aplicados.
- **RF-03:** Consultar versión actual y límite del historial; obtener los incrementos pendientes, sus IDs afectados y las entidades vigentes. Procesar la secuencia en orden, sin saltar versiones. Si la versión local es menor que `oldestAvailableVersion`, falta un incremento o no puede verificarse la continuidad, detener esa secuencia e iniciar RF-07.
- **RF-04:** Aplicar todos los cambios de cada unidad y confirmar su versión conjuntamente. Una unidad fallida no deja efectos parciales ni adelanta versión; las unidades anteriores confirmadas conservan sus efectos. Validar datos y referencias, conservar IDs y habilitación, e interpretar horas e instantes conforme al contrato. No publicar una combinación de datos cuya versión no pueda justificarse.
- **RF-05:** Confirmar offsets Kafka después de persistir los efectos y el reconocimiento del evento procesado. Ante caída, confirmación perdida o reentrega, comprobar lo confirmado antes de repetir; la misma notificación no duplica efectos. Un evento válido sin cambios pendientes puede reconocerse sin alterar la copia.
- **RF-06:** Conservar la última copia consistente y el backend activo ante fallos temporales de Kafka, Redis o del guardado incremental local; mientras el acceso técnico siga habilitado, retomar automáticamente al recuperarse la dependencia, desde el progreso confirmado, con reintentos limitados y observables. No tratar un timeout como prueba de que nada se guardó; verificar el resultado antes de repetir efectos inciertos.
- **RF-07:** Recuperar discontinuidades mediante CU-03, reutilizando la operación de snapshot de CU-01. Registrar causa y resultado; usar su `snapshotVersion` como nueva base sin ejecutar incrementos concurrentes sobre la copia en reemplazo. Un snapshot rechazado o fallido conserva la última copia y no declara recuperación. Si un ciclo agota sus intentos por fallos temporales, iniciar otro ciclo automáticamente después de una pausa, sin requerir reinicio y mientras el acceso técnico siga habilitado. La recuperación tendrá límites observables de intentos y ciclos; los mensajes posteriores no eluden las pausas ni reanudan la secuencia insegura. Ante fallo no recuperable, detener la repetición automática y conservar el diagnóstico.
- **RF-08:** Conservar datos, versión y progreso confirmado tras reiniciar. Reentregas o notificaciones pendientes después de recuperar no revierten el snapshot ni repiten efectos. Una ejecución interrumpida no se interpreta como éxito; se conserva la carga de snapshot en cada arranque ya acordada para CU-01.
- **RF-09:** Distinguir copia disponible, actualización/recuperación en curso, versión comprobada y fallo/no verificable. Mientras no pueda acreditarse vigencia, conservar la política de KMP con aviso sobre la copia consistente y espera de Turnos para operaciones que requieren catálogo vigente. Sin copia confirmada, las consultas permanecen indisponibles; un catálogo vacío con versión sí es una copia aplicada. Registrar diagnósticos seguros sin secretos, sin implementar todavía su API.

## Fuera de alcance

<!-- Exclusiones existentes o acordadas; no ampliar por iniciativa del agente. -->

- CU-04/05/06: APIs de búsqueda, agendas y exposición del estado; se conserva la información que necesitarán.
- Ocupaciones, holds, reservas y eventos de Turnos; otra réplica de catálogo en ese backend.
- Registro o renovación automática de acceso técnico, cambios JWT y administración de infraestructura central.
- Reimplementar la descarga o el reemplazo completo de CU-01.

## Flujo de usuario

<!-- Proceso automático del servicio y alternativas relevantes. -->

1. Después de preparar acceso y copia local, Catálogo atiende notificaciones y comprueba las versiones centrales aun sin recibirlas.
2. Reconoce duplicados y compara versión local, actual y límite de historial. Si está al día, conserva la copia.
3. Con continuidad verificable, obtiene y valida los cambios pendientes; los aplica en orden y confirma sus unidades con la versión correspondiente.
4. Si no puede demostrar continuidad, detiene los incrementos y ejecuta CU-03 mediante el snapshot de CU-01.
5. Registra el resultado confirmado y reconoce el mensaje Kafka cuando corresponda. Ante fallo conserva el último progreso consistente y aplica la política de recuperación acordada.

## Datos y reglas de negocio

<!-- Significado, validación y límites contractuales; sin diseñar almacenamiento. -->

- `CatalogUpdated` aporta `eventId`, `eventType`, `newVersion`, `occurredAt`, `source` y `schemaVersion`. Notifica un cambio; no contiene sus entidades. Su message key es la versión como texto; no sustituye la comparación con Redis.
- Redis publica `currentVersion`, `oldestAvailableVersion`, `publishedAt` y `changes:{version}`. Los arrays de cambios contienen IDs; los hashes contienen entidades actuales, no sus estados históricos. No asumir que leer sucesivamente esos datos constituye una lectura consistente.
- Las bajas contractuales son lógicas: las entidades deshabilitadas permanecen en los hashes. Un ID ausente o datos incompatibles no se omiten para aparentar una actualización completa.
- No depender del orden de llegada de las notificaciones para ordenar versiones. Una notificación perdida, antigua o duplicada no modifica las reglas de continuidad.
- Versión local `3`, actual `7` y límite `4` requieren snapshot, según el ejemplo del contrato; no comenzar por `changes:4`.
- Las referencias entre categorías, profesionales y agendas deben conservar coherencia. Aceptar horas `HH:mm` y `HH:mm:ss`, y diferenciar horarios locales de instantes UTC.
- Estado de acceso técnico y vigencia del catálogo son distintos. El vencimiento/401 y el agotamiento del login conservan su recuperación por reinicio, sin renovar credenciales en esta funcionalidad.

## Comportamiento del backend y casos alternativos

<!-- Escenarios del servicio, sin trasladar navegación o ciclo de vida mobile. -->

| Situación | Comportamiento esperado |
| --- | --- |
| No hay cambios pendientes | Conservar datos y versión; registrar la comprobación sin inventar otra versión. |
| Evento duplicado, antiguo o reentregado | Reconocer lo confirmado sin repetir efectos ni retroceder. |
| Notificación perdida | Detectar atraso mediante comprobación independiente y actualizar o reconstruir según continuidad. |
| Incremento ausente, historial insuficiente o estado no verificable | Detener la secuencia e iniciar CU-03; no saltar el cambio faltante. |
| Redis cambia durante la lectura | No publicar una mezcla como versión comprobada; recuperar si no se puede demostrar continuidad/coherencia. |
| Corte temporal de Kafka/Redis | Copia conservada, fallo observable y reanudación automática con reintentos acotados. |
| Guardado incremental temporalmente fallido | Sin unidad parcial ni versión adelantada; verificar efectos inciertos y retomar automáticamente al recuperarse MySQL, con reintentos acotados. |
| Snapshot de recuperación con fallos temporales | Conservar copia y diagnóstico; si agota un ciclo, iniciar otro automáticamente tras una pausa y dentro de los límites de recuperación. No aplicar incrementos mientras siga sin verificarse la continuidad. |
| Snapshot de recuperación con fallo no recuperable | Conservar copia y diagnóstico, sin declarar recuperación ni repetir automáticamente el fallo permanente. |
| Mensaje incompatible | Sin cambios en catálogo ni falsa confirmación de aplicación; diagnóstico seguro. |
| Reconstrucción y notificaciones concurrentes | Ningún incremento compite con el reemplazo ni utiliza una versión anterior después de confirmarlo. |
| Reinicio o confirmación perdida | Recuperar lo confirmado y comprobar el resultado antes de repetir efectos. |

**UI no aplicable:** pantallas, navegación y ciclo de vida de KMP no corresponden a este servicio.

## Restricciones del pedido

<!-- Condiciones impuestas y decisiones confirmadas; solución técnica en PLAN. -->

- Agrupación de #8/#9 confirmada el 2026-10-18: una funcionalidad SDD, rama actual y PR conjunto; conservar los CU y sus criterios diferenciados. El usuario maneja Git.
- Catálogo sigue siendo propietario exclusivo de su réplica y persistencia. Kafka notifica, Redis publica catálogo/cambios y REST entrega el snapshot.
- Decisiones confirmadas: recuperación automática con reintentos acotados ante fallos temporales de Kafka, Redis y guardado incremental MySQL (2026-10-18). Revisado el 2026-10-19: CU-03 permite otro ciclo automático de snapshot tras una pausa cuando el anterior agota sus intentos temporales. Se conserva CU-01 al arrancar: backend activo ante fallo de snapshot, sin búsquedas si no existe copia confirmada.
- Usar únicamente configuración de integración habilitada, proteger secretos y no exponer el JWT técnico a KMP.
- El PLAN definirá comprobación independiente, coherencia de lecturas Redis, límites de intentos/ciclos y pausas de recuperación, deduplicación, offsets y coordinación con CU-01. No fijar aquí librerías, clases ni transacciones.

## Criterios de aceptación

<!-- Resultados observables vinculados a requisitos, aún no verificados. -->

- **CA-01 · RF-01, RF-03:** Con copia confirmada, detectar y aplicar atraso tanto por notificación como sin recibirla; sin acceso/copia no aplicar incrementos a un estado inexistente.
- **CA-02 · RF-02, RF-05:** Repetir un `eventId` antes/después de reiniciar y perder la confirmación Kafka no duplica efectos; el offset no precede a la persistencia del resultado correspondiente.
- **CA-03 · RF-03, RF-04:** Ante varias versiones pendientes, aplicar su secuencia sin saltos y publicar datos/versiones coherentes; si falla una unidad, conservar las anteriores confirmadas y no avanzar la incompleta.
- **CA-04 · RF-02, RF-04:** Conservar IDs, referencias, habilitación y semántica temporal; aceptar campos nuevos compatibles y rechazar entradas incompatibles sin modificación parcial ni falsa aplicación.
- **CA-05 · RF-03, RF-07:** Historial insuficiente, incremento ausente, discontinuidad o estado no verificable conducen a snapshot; no continuar la secuencia insegura.
- **CA-06 · RF-07, RF-08:** Una recuperación correcta establece exactamente la copia/versión del snapshot; notificaciones concurrentes, antiguas o repetidas no la revierten. Si un ciclo agota sus intentos por fallos temporales, otro ciclo puede recuperar automáticamente después de la pausa y sin reiniciar, respetando los límites acordados. Durante la espera se conserva la copia sin continuar la secuencia insegura; mensajes/comprobaciones no eluden las pausas ni superponen reconstrucciones. Un fallo no recuperable detiene la repetición automática sin declarar recuperación.
- **CA-07 · RF-04, RF-07:** Si cambia Redis durante la lectura o coinciden snapshot e incrementos, no se publica una mezcla como copia correspondiente a una versión verificada.
- **CA-08 · RF-06:** Con acceso técnico habilitado, un fallo temporal seguido de recuperación de Kafka, Redis o MySQL durante los incrementos permite retomar sin reiniciar, desde el progreso confirmado y sin duplicados; los reintentos tienen límite observable. Un guardado incierto se verifica antes de repetir sus efectos.
- **CA-09 · RF-08, RF-09:** Reiniciar durante una unidad o perder su confirmación conserva la última aplicación completa; sin copia se distingue indisponibilidad de catálogo vacío aplicado.
- **CA-10 · RF-07, RF-09:** Actualización, recuperación y fallo dejan estado/diagnóstico seguros para aplicar la política de consultas; ningún mensaje o salida expone credenciales ni confunde acceso técnico con vigencia.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio; herramientas, pruebas y evidencias en PLAN/TASKS. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Publicar nuevas versiones con y sin notificación, incluso durante el arranque. | Atraso detectado y resuelto sin intervención de un usuario. |
| CA-02 | Reentregar el mismo evento y reiniciar después de persistir pero antes de confirmar offset. | Efectos únicos y reconocimiento posterior a persistencia. |
| CA-03 | Entregar varias versiones; fallar lectura y guardado de una unidad intermedia. | Orden y unidades confirmadas conservados; unidad incompleta sin avance. |
| CA-04 | Incluir deshabilitados, ambos formatos de hora, campos adicionales, datos y referencias inválidos. | Contrato conservado o rechazo completo, según corresponda. |
| CA-05 | Probar cada discontinuidad, incluido el ejemplo local 3/actual 7/límite 4. | Se solicita snapshot, sin saltar versiones. |
| CA-06 | Agotar un ciclo temporal, recibir mensajes durante la pausa y recuperar en el siguiente sin reiniciar; probar también fallo no recuperable. | Nueva base exacta o copia previa preservada; pausas y límites respetados, sin superposición, retroceso o falso éxito; fallo permanente sin repetición automática. |
| CA-07 | Cambiar la publicación Redis mientras se lee y recibir eventos durante reconstrucción. | Ninguna copia mezclada publicada como verificable. |
| CA-08 | Interrumpir/restablecer Kafka, Redis y guardado incremental MySQL; incluir confirmación perdida y observar intentos/progreso. | Reanudación automática y consistente, reintentos acotados y resultado incierto verificado antes de repetir. |
| CA-09 | Reiniciar antes/después de confirmar una unidad, con copia inexistente/vacía/aplicada. | Persistencia e idempotencia correctas, estados distinguibles. |
| CA-10 | Observar estado y salidas en éxito, atraso, reconstrucción y fallo. | Políticas de consulta representables y diagnósticos sin secretos. |

## Decisiones pendientes

<!-- Actualizar las secciones afectadas al resolverlas; no fingir aprobación. -->

Ninguna funcional. Las decisiones técnicas indicadas en las restricciones se
desarrollarán en el PLAN, después de aprobar esta SPEC.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba alcance, correspondencia RF/CA y alternativas; resuelve las decisiones.
Mantén diseño técnico en PLAN y no implementes por aprobar esta SPEC.
-->
