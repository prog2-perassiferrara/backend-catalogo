# SPEC: Inicializar o reconstruir el catálogo mediante snapshot

**Estado:** Aprobada <!-- Borrador | En revisión | Aprobada -->

**Aprobación del usuario:** 2026-10-17.

**Aclaración confirmada durante el PLAN:** 2026-10-17, rechazar snapshots con versión menor que la ya aplicada, conservando la copia local.

**Aclaración confirmada el 2026-10-19:** se conserva el comportamiento de arranque:
si falla el snapshot, el backend queda activo; sin copia confirmada no habilita
búsquedas. La espera hasta reinicio al agotar el ciclo corresponde al arranque de
CU-01. [CU-03](../sincronizacion-incremental/SPEC.md) coordina nuevos ciclos de esta
misma operación durante la ejecución, con pausas y límites propios ante fallos temporales.

<!-- PARA LA PERSONA
SPEC.md define qué debe cumplirse; PLAN.md desarrolla cómo implementarlo;
TASKS.md organiza los pasos de ejecución. Esta funcionalidad corresponde al backend.
-->

<!-- PARA EL AGENTE
- Contrasta el enunciado, el contrato de integración y el CU aprobado.
- Completa esta spec con la persona: investiga lo comprobable y consulta las
  decisiones pendientes. Haz pocas preguntas por vez y actualiza las respuestas.
- No inventes requisitos ni exclusiones. Distingue propuestas de decisiones
  confirmadas y marca como PENDIENTE lo que aún no esté resuelto.
- No incluyas diseño de clases, tablas, componentes, archivos o algoritmos:
  esos detalles pertenecen a PLAN.md.
- Mantén el documento breve y proporcional a la funcionalidad. Conserva los comentarios.
- Un documento completo no está aprobado automáticamente. Solicita aprobación
  antes de marcarlo como Aprobada. No implementes durante esta etapa.
-->

## Qué construimos y para quién

<!-- Qué necesidad resolvemos, quién tiene esa necesidad y qué podrá hacer.
Describe el objetivo en lenguaje de producto. -->

Catálogo obtiene y conserva una copia completa de categorías, profesionales y
horarios semanales de cátedra, junto con la versión exacta de esos datos. Esa copia
permitirá resolver las consultas locales de KMP y Turnos sin consultar cátedra por
cada búsqueda.

Corresponde al issue [#7](https://github.com/prog2-perassiferrara/backend-catalogo/issues/7)
y a [CU-01](../../use-cases/CU-01-inicializar-o-reconstruir.md).
Fuentes: [enunciado, secciones 4.1, 6.1 y 8](../../../../PROJECT_STATEMENT-v1.md),
[contrato, secciones 4, 7 y 13](../../../../INTEGRATION_REFERENCE-v2.md).

## Situación actual

<!-- Comportamiento actual relevante, limitación que queremos resolver y
comportamientos existentes que deben conservarse. No describas la arquitectura. -->

Catálogo ya obtiene acceso técnico al arrancar y protege sus APIs mediante JWT.
Todavía no descarga ni persiste el catálogo o su versión aplicada. Se conservan
las políticas de [integración técnica](../integracion-tecnica/SPEC.md) y
[seguridad](../seguridad-jwt/SPEC.md): acceso habilitado solo con sesión técnica
utilizable, backend activo ante indisponibilidad remota y ningún JWT técnico a KMP.

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. -->

- **RF-01:** Al arrancar, iniciar una carga completa cuando se obtenga acceso técnico utilizable, incluso si existe una copia anterior consistente. Si no se obtiene acceso, conservar la copia y mantener el servicio activo conforme a la política de integración existente. Permitir que CU-03 invoque posteriormente esta misma operación para reconstruir el catálogo.
- **RF-02:** Solicitar `GET /api/synchronization/snapshot` con el JWT técnico existente, sin registrar cuentas ni iniciar un login adicional por la descarga. Obtener las tres colecciones completas y `snapshotVersion`, incluidas entidades habilitadas y deshabilitadas.
- **RF-03:** Rechazar un snapshot que no pueda interpretarse o aplicarse como copia completa y consistente, o cuya versión sea menor que la ya aplicada. No completar colecciones ausentes como si fueran vacías, omitir entidades inválidas ni corregir referencias por suposición.
- **RF-04:** Sustituir la copia completa y confirmar su versión como una sola unidad: después del éxito, las tres colecciones corresponden al snapshot y la versión local es exactamente `snapshotVersion`. Los elementos de la copia anterior ausentes del nuevo snapshot dejan de pertenecer a la copia actual. No publicar mezclas, datos parciales ni una versión anticipada.
- **RF-05:** Conservar datos y versión confirmados tras reiniciar. Una ejecución interrumpida no se considera completada; solo una copia consistente puede utilizarse, y el siguiente arranque inicia un nuevo ciclo de carga.
- **RF-06:** Mientras se reconstruye o después de un fallo, conservar la última copia consistente, si existe. Esa copia puede servir búsquedas de KMP con aviso de posible desactualización; las operaciones de Turnos que requieren vigencia deben esperar. Sin copia consistente no se habilitan búsquedas. Registrar la situación local necesaria sin implementar todavía esas APIs.
- **RF-07:** Reintentar de forma limitada los fallos temporales de descarga y de guardado local. Cada intento fallido conserva la copia y versión anteriores; no se vuelve a aplicar sobre un resultado parcial. Si el resultado del guardado es incierto, verificar la copia y versión confirmadas antes de repetir o declarar fallo: un timeout no demuestra que no se guardó. Detener el ciclo ante fallo no recuperable o al agotar el límite, manteniendo el backend activo si sus condiciones locales de arranque eran válidas. En el arranque, tras agotarlo, la recuperación automática comienza al reiniciar, sin ciclos ilimitados en segundo plano; la coordinación posterior de nuevos ciclos de recuperación corresponde a CU-03.
- **RF-08:** Distinguir errores por HTTP y `code` cuando exista, sin depender de `detail`. Un JWT técnico vencido o rechazado con 401 invalida la sesión conforme a la política existente, sin renovación automática. Registrar resultado y diagnóstico seguro del ciclo, sin tokens, contraseñas ni cuerpos completos de respuesta.

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

Separación ya establecida en el backlog y en los CU revisados:

- CU-02: consumo Kafka, lectura Redis, aplicación incremental y detección de atraso por versiones.
- CU-03: detección de discontinuidades y coordinación de la recuperación; este issue aporta la carga completa que utilizará.
- CU-04, CU-05 y CU-06: APIs de búsqueda, agendas y exposición del estado a consumidores. Aquí se conserva la información necesaria para respetar sus políticas.
- Ocupaciones, holds y reservas, responsabilidad de Turnos. No forman parte del snapshot.
- Cambiar registro, adquisición o renovación de la sesión técnica y los contratos JWT ya acordados.

## Flujo de usuario

<!-- Cómo se inicia, qué hace el usuario y qué resultado obtiene.
En este backend, describe el proceso automático y sus consumidores. -->

1. Catálogo arranca y obtiene acceso técnico utilizable mediante el proceso existente; una recuperación futura también podrá solicitar la reconstrucción.
2. Se marca la carga en curso. Si hay una copia anterior consistente, se conserva con la política de RF-06.
3. Catálogo solicita el snapshot con su JWT técnico y comprueba que la respuesta permite obtener una copia completa y consistente.
4. Aplica las tres colecciones y su versión conjuntamente. Solo después del éxito pasa a utilizarse la nueva copia.
5. Registra el resultado. Si ocurre un fallo temporal, reintenta dentro del límite; si el fallo es terminal o se agota el límite, conserva la copia anterior e informa la situación.

## Datos y reglas de negocio

<!-- Información necesaria, campos, validaciones y reglas de negocio.
Describe significado y comportamiento, sin diseñar tablas, DTO ni almacenamiento. -->

| Información del contrato | Significado |
| --- | --- |
| `snapshotVersion`, `generatedAt` | Versión de la copia y momento UTC de generación. La versión aplicada se toma de `snapshotVersion`, no del reloj local. |
| `professionalCategories` | ID, nombre, descripción, habilitación e instantes de creación/actualización. |
| `professionals` | ID, categoría referenciada, nombre, apellido, habilitación e instantes de creación/actualización. |
| `weeklySchedules` | ID, profesional referenciado, día semanal, inicio/fin, duración del slot, habilitación e instantes de creación/actualización. |

- Un snapshot completo puede contener colecciones vacías. Tener una copia aplicada vacía con versión conocida se distingue de no disponer de una copia consistente.
- Las referencias entre profesionales, categorías y horarios deben corresponder a la misma copia; no descartar las entidades deshabilitadas que las sostienen.
- Las horas representan atención local y admiten `HH:mm` y `HH:mm:ss`. Los instantes usan ISO-8601 UTC y los días semanales los valores del contrato.
- No generar IDs propios ni transformar la habilitación recibida. No imponer longitudes o límites de negocio que no estén respaldados por el contrato.
- Datos y versión forman una unidad observable, incluso ante consultas concurrentes o reinicio. Una repetición no debe duplicar entidades ni dejar residuos de la copia sustituida.
- Aplicar un snapshot establece consistencia para su versión; no demuestra que cátedra no haya publicado cambios posteriores. La evaluación continua de vigencia corresponde a CU-02/CU-06.
- Estado de sesión técnica y estado de sincronización son distintos: disponer de JWT utilizable no equivale a tener catálogo vigente. Health conserva su significado técnico.

## Comportamiento del backend y casos alternativos

<!-- Adapta los escenarios al backend. Expresa resultados observables,
sin introducir mecanismos de implementación ni escenarios exclusivos de UI. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Carga en curso con copia anterior | Mantener la copia consistente con aviso de posible desactualización; Turnos espera para operaciones que requieren vigencia. |
| Primera carga, sin copia | No habilitar búsquedas hasta aplicar una copia consistente. |
| Snapshot completo con colecciones vacías | Aplicar el catálogo vacío y su versión; no confundirlo con fallo o ausencia de inicialización. |
| Respuesta inválida, incompleta o incoherente | No aplicar nada ni cambiar la versión; detener el ciclo con diagnóstico seguro. |
| Red interrumpida, timeout o fallo temporal remoto | Reintentar dentro del límite conservando la copia; agotado el ciclo de arranque, mantener el proceso activo hasta reinicio. Los nuevos ciclos de recuperación durante la ejecución corresponden a CU-03. |
| Fallo temporal al guardar | Reintentar dentro del límite sin conservar efectos parciales del intento anterior. |
| Fallo no recuperable al guardar | Conservar la copia anterior y detener el ciclo sin declarar éxito. |
| JWT técnico vencido o HTTP 401 | Invalidar acceso técnico según la política existente y recuperar tras reinicio, sin login adicional. |
| Reinicio durante el reemplazo | No considerar completada la carga interrumpida ni servir una copia parcial; iniciar otro ciclo al obtener acceso técnico. |
| Cambios centrales posteriores al snapshot | Conservar la versión aplicada; su seguimiento corresponde a CU-02, sin declarar vigencia permanente. |

**Puntos de UI no aplicables y motivo:** navegación, pantallas, segundo plano y
recreación de actividades corresponden a KMP. Este servicio debe conservar datos,
versión y estado consistente independientemente de la aplicación cliente.

## Restricciones del pedido

<!-- Condiciones ya impuestas. El diseño técnico se desarrolla en PLAN.md;
no convertir preferencias del agente en restricciones. -->

- Respetar el enunciado, el contrato de cátedra y la política de consultas acordada en CU-01.
- Utilizar la persistencia propia de Catálogo y conservar su propiedad exclusiva. Turnos no mantiene otra réplica vigente.
- Mantener los secretos técnicos fuera de archivos versionados, logs y respuestas de usuario.
- Decisiones confirmadas el 2026-10-16 y aclaradas el 2026-10-19: snapshot en cada arranque; reintentos limitados ante fallos temporales de descarga y guardado; agotado el ciclo de arranque, recuperación automática al reiniciar. CU-03 establece la coordinación de nuevos ciclos durante la ejecución.
- El PLAN concretará límites, tiempos, clasificación de fallos temporales, consistencia y concurrencia; también revisará la configuración compartida fuera de los slices. No se decide aquí el diseño de clases o persistencia.

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye alternativas acordadas. No marcar criterios como superados en la SPEC. -->

- **CA-01 · RF-01, RF-02:** Al arrancar con acceso técnico utilizable, con o sin copia previa, se solicita un snapshot autenticado, sin registro ni login adicional por CU-01. Sin acceso técnico no se solicita y la copia previa se conserva.
- **CA-02 · RF-02, RF-04:** Tras aplicar un snapshot válido, las tres colecciones completas, incluidas entidades deshabilitadas, y la versión exacta quedan disponibles conjuntamente. Reconstruir elimina los elementos que ya no están en el snapshot, sin duplicarlos al repetir la carga.
- **CA-03 · RF-03:** Ante respuesta inválida, colección ausente, referencias incoherentes o versión menor que la aplicada, no cambia la copia ni su versión, no se informa éxito y el ciclo se detiene.
- **CA-04 · RF-04, RF-06:** Durante un reemplazo, las consultas no observan mezclas de copias ni datos/versiones parciales; se conserva la política de copia anterior con aviso y espera de Turnos.
- **CA-05 · RF-04, RF-05:** Después de reiniciar se conserva la última copia confirmada con su versión. Una interrupción del reemplazo no publica la carga incompleta y el arranque inicia un nuevo ciclo.
- **CA-06 · RF-06:** Sin copia consistente no se habilita consulta; un snapshot válido con colecciones vacías se reconoce como copia aplicada, con su versión.
- **CA-07 · RF-07:** Un fallo temporal de descarga o guardado puede recuperarse dentro del límite sin duplicados ni efectos parciales. Un resultado incierto de guardado se verifica antes de repetir o informar fallo. Al agotar el ciclo de arranque no se repiten intentos automáticamente y el backend sigue activo; reiniciar inicia otro ciclo. Un fallo no recuperable termina el ciclo. Los nuevos ciclos coordinados por CU-03 se verifican en su especificación.
- **CA-08 · RF-02, RF-08:** Un JWT vencido o rechazo 401 impide continuar con esa sesión y no genera renovación automática. Los errores se distinguen aunque falte `code`, sin revelar secretos ni depender de `detail`.
- **CA-09 · RF-02, RF-03, RF-04:** Se conservan campos, referencias, habilitación y versión del snapshot, aceptando ambos formatos de hora y los instantes UTC del contrato.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado esperado.
Tests, herramientas, comandos y evidencias corresponden al PLAN. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Arrancar sin copia y con copia anterior, con sesión técnica disponible/no disponible; observar solicitudes. | Carga completa cuando hay acceso; sin login adicional ni pérdida de copia por indisponibilidad. |
| CA-02 | Aplicar dos snapshots distintos, con elementos deshabilitados y elementos retirados; repetir el segundo. | Copia exacta del segundo, sin residuos/duplicados y con su versión. |
| CA-03 | Entregar JSON no interpretable, omitir una colección, incluir referencias inexistentes o una versión menor que la aplicada. | Rechazo completo, copia/versión previas intactas y diagnóstico seguro. |
| CA-04 | Consultar mientras se reemplaza una copia consistente. | No se observa estado parcial; carga en curso y copia anterior identificables para las políticas de consulta. |
| CA-05 | Reiniciar tras éxito e interrumpir una reconstrucción antes de completarla. | Última copia confirmada recuperable, carga incompleta no publicada y nuevo ciclo de arranque. |
| CA-06 | Comparar falta de copia con un snapshot completo vacío. | Ausencia de inicialización diferenciada de catálogo vacío aplicado. |
| CA-07 | Fallar descarga y guardado de forma temporal, incluir resultado incierto; recuperar antes del límite y agotar otro ciclo de arranque. | Verificación del resultado incierto, recuperación limitada y consistente, o detención del ciclo de arranque sin más intentos hasta reiniciar. |
| CA-08 | Vencer JWT, responder 401 y devolver otros errores sin `code`; inspeccionar solicitudes y salidas. | Sin renovación automática, diagnóstico por estado/código y sin secretos. |
| CA-09 | Entregar horas con/sin segundos, días contractuales y fechas UTC junto con ambas habilitaciones. | Valores interpretados y conservados sin alterar su significado. |

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

Ninguna decisión funcional pendiente identificada. Los parámetros y decisiones
técnicas indicados arriba se resolverán durante el PLAN.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba alcance, flujos, alternativas del backend y trazabilidad RF/CA.
Resuelve los marcadores pendientes. Mantén el diseño técnico en PLAN.md.
-->
