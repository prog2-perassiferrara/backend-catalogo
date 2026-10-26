# TASKS: Incorporar incrementos y recuperar discontinuidades

**Referencias:** [SPEC aprobada](SPEC.md), [PLAN aprobado](PLAN.md), issues [#8](https://github.com/prog2-perassiferrara/backend-catalogo/issues/8) y [#9](https://github.com/prog2-perassiferrara/backend-catalogo/issues/9).
**Estado:** completada y verificada localmente el 2026-10-23.
**Aprobación y autorización:** el usuario confirmó «apruebo tasks. implementa».

**Chequeo previo:** correcto: 9 RF cubiertos por 10 CA, todos con validación en PLAN y tareas asociadas; dependencias sin ciclos, sin alcance nuevo ni decisiones bloqueantes bajo el supuesto de integración aprobado. Implementación autorizada explícitamente.

<!-- Cada tarea reúne implementación y pruebas del comportamiento correspondiente.
Registrar evidencia real al ejecutarla; no completar casillas por escribir código.
Revisar solo la parte afectada si cambia sustancialmente una decisión aprobada.
-->

## Tareas

- [x] **T-01 · Preparar la colaboración interna y la coordinación compartida**
  - **Objetivo:** obtener la configuración adquirida y reutilizar CU-01 desde CU-03 sin acoplar repositorios ni permitir escrituras concurrentes.
  - **Alcance:** puerto público de consulta en `integration`, su implementación y adaptación dentro de `synchronization`; configuración compartida fuera de slices. Inyectar el mismo `ReentrantLock` en las operaciones coordinadas y conservar en `SynchronizationResult` la clasificación tipada de los fallos de snapshot. Mantener políticas de login, vencimiento y 401, sin endpoint nuevo ni exposición de credenciales.
  - **Dependencias:** ninguna; reutiliza el snapshot y acceso técnico existentes.
  - **Referencias:** RF-06/RF-07/RF-08/RF-09; CA-06/CA-07/CA-09/CA-10; PLAN, módulos y concurrencia.
  - **Validación:** regresiones de snapshot y acceso técnico con casos de uso reales: consulta solo con sesión utilizable, vencimiento sin renovación, resultado temporal/terminal distinguible y exclusión compartida con reentrada CU-03 → CU-01. Comprobar que modelos y diagnósticos no muestran secretos. Mantener el límite inicial de tres intentos y el comportamiento de CU-01.
  - **Entrega:** cambio preparatorio coherente, apto para commit separado del usuario antes de la funcionalidad incremental; el agente no gestiona Git.

- [x] **T-02 · Obtener y validar lotes incrementales desde Redis**
  - **Objetivo:** construir una unidad verificable desde la versión local hasta la publicación observada.
  - **Alcance:** modelos de lote y puertos de lectura; adaptador/DTO Redis con `StringRedisTemplate` y Lettuce. Añadir dependencias de Redis y sus pruebas según el PLAN. Leer metadata, ventana, todos los cambios del rango e IDs afectados; unir repetidos, recuperar entidades actuales y volver a comprobar metadata. Conservar formatos de hora, UTC y habilitación; aplicar límites de 5 s por conexión/comando, 20 s totales y 8 MiB. Traducir fallos sin reintentos ocultos ni transacciones MySQL abiertas.
  - **Dependencias:** T-01 para configuración interna y control de acceso.
  - **Referencias:** RF-02/RF-03/RF-04/RF-06; CA-01/CA-04/CA-05/CA-07/CA-08; PLAN, datos y límites Redis.
  - **Validación:** `CatalogRedisChangesTests` con Redis aislado y datos ficticios: rango continuo, arrays vacíos, IDs repetidos, deshabilitados, ambos formatos de hora y campos adicionales. Probar límite de historial, cambio ausente, ID faltante, JSON incompatible, publicación que cambia durante la lectura, timeout y tamaño excedido. No aceptar datos mezclados ni inventar estados históricos. El ensayo usa el supuesto documentado de publicación central coherente; no lo demuestra.

- [x] **T-03 · Confirmar datos, versión y reconocimientos en MySQL**
  - **Objetivo:** persistir cada lote de forma indivisible y reconocer eventos/rechazos después de un reinicio.
  - **Alcance:** migración aditiva `V3__catalog_updates.sql`, metadata fechada, tabla de eventos procesados y tabla de rechazos del PLAN. Repositorios/mapper/adaptador y bean escritor separado con `@Transactional(REQUIRES_NEW)`. Comprobar versión base bajo bloqueo, validar referencias con filas existentes y cambios del lote, y guardar en orden de dependencias. Confirmar datos/versión/runId/recibo juntos; distinguir recibo cubierto y rechazo sin efectos. Mantener `eventId` completo e indexación por huella, comparación exacta y coordenadas únicas para rechazos. Traducir también fallos al regresar del proxy y permitir reconciliación fresca.
  - **Dependencias:** T-02 para contratos y modelos del lote.
  - **Referencias:** RF-02/RF-04/RF-05/RF-08/RF-09; CA-02/CA-03/CA-04/CA-06/CA-09/CA-10; PLAN, persistencia y deduplicación.
  - **Validación:** `CatalogUpdatePersistenceTests` sobre MySQL 8.4.8 real: actualizar desde V2, lote completo, filas no afectadas conservadas, bajas lógicas, referencias y rollback tras una escritura parcial forzada. Verificar datos/versión/recibo con una lectura coherente, rechazo sin avance, evento ya cubierto y duplicados tras reabrir contexto. Cubrir IDs largos y diferencias de mayúsculas, base modificada y pérdida controlada de confirmación después de un commit real. No simular las transacciones con mocks.

- [x] **T-04 · Coordinar CU-02 y CU-03 con recuperación acotada**
  - **Objetivo:** aplicar incrementos o recuperar mediante CU-01 desde el progreso confirmado, sin superposición ni duplicados.
  - **Alcance:** puertos y casos de uso `UpdateCatalogUseCase` y `RecoverCatalogUseCase`, resultados funcionales, estados y presupuesto en memoria. Distinguir evento duplicado/cubierto, actualización, espera y fallo permanente. Incrementos: hasta 3 intentos separados 5 s y próxima oportunidad no antes de 30 s al agotarlos; conservar candidato/runId y reconciliar incertidumbre. Recuperación: un ciclo de snapshot por invocación habilitada, hasta 3 ciclos por bloque, pausas de 30 s y 5 min entre bloques agotados. Mensajes no reinician límites; éxito limpia el pendiente; fallo permanente o acceso inválido detiene la repetición. Registrar estado seguro sin declarar vigencia indefinida.
  - **Dependencias:** T-01, T-02 y T-03.
  - **Referencias:** RF-01 a RF-09; CA-01/CA-02/CA-03/CA-05/CA-06/CA-07/CA-08/CA-09/CA-10; PLAN, secuencia y D-02/D-05.
  - **Validación:** `CatalogIncrementalSynchronizationTests` y `RecoverCatalogUseCaseTests` ejecutan los casos reales, con reloj y fronteras controlados. Probar lote 7 → 10 sin publicar estados intermedios falsos, evento antiguo/duplicado, ejemplo local 3/actual 7/límite 4, commit incierto reconciliado y preservación de copia/progreso. Verificar fronteras de 5 s/30 s/5 min, tres ciclos, nuevo bloque automático, mensajes durante pausas, interrupción y exclusión con snapshot. Redis inaccesible por red no debe generar snapshots continuos; fallo permanente/401 no renueva credenciales. Reutilizar CU-01 real en las pruebas de integración pertinentes.

- [x] **T-05 · Consumir Kafka con confirmación posterior a persistencia**
  - **Objetivo:** convertir notificaciones en trabajo idempotente y continuar ante mensajes incompatibles con rechazo durable.
  - **Alcance:** dependencias Spring Kafka/Testcontainers, DTO y listener en `infrastructure/messaging`. Contenedor dinámico con topic/grupo adquiridos; un consumidor, deserialización de texto y validación del esquema v1, ignorando extras compatibles. Configurar los valores de offsets/polls del PLAN, sin crear topics centrales. Ack solo con recibo o rechazo confirmado; `nack(Duration)` en el hilo consumidor para espera y detención ante fallo permanente. El listener delega la lógica de catálogo y no construye sus colaboradores.
  - **Dependencias:** T-04.
  - **Referencias:** RF-01/RF-02/RF-05/RF-06/RF-08; CA-01/CA-02/CA-04/CA-08/CA-09/CA-10; PLAN, offsets y D-01/D-06.
  - **Validación:** `CatalogNotificationConsumerTests` con Kafka/MySQL reales: reentrega después de commit sin ack, reinicio, mensaje ya cubierto y confirmación perdida. Un mensaje incompatible seguido de uno válido debe dejar rechazo durable y permitir continuar; si guardar el rechazo falla, no avanzar su offset. Probar que fallo/espera no quedan saltados por una confirmación posterior, que `nack` mantiene el consumidor activo durante la pausa y que no existe descarte automático por agotar reintentos del framework. Capturar diagnósticos para verificar ausencia de cuerpos y secretos.

- [x] **T-06 · Activar seguimiento periódico y gestionar recursos dinámicos**
  - **Objetivo:** detectar notificaciones perdidas y recuperar la ejecución sin depender de búsquedas ni de Kafka operativo.
  - **Alcance:** composición y arranque fuera de slices, clientes creados con configuración adquirida y cierre explícito. Después de CU-01, con copia confirmada y acceso utilizable, comprobar inmediatamente y luego cada 30 s desde el fin de la ejecución anterior mediante scheduler de un hilo. Evitar solapamiento, ticks acumulados y cancelación silenciosa del seguimiento por una excepción. Desactivar clientes/autoconfiguración predeterminados que conectarían a localhost; conservar condiciones actuales de arranque y health. Detener trabajo ante acceso inválido y cerrar scheduler, consumidor y conexiones al apagar.
  - **Dependencias:** T-04 y T-05.
  - **Referencias:** RF-01/RF-06/RF-07/RF-08/RF-09; CA-01/CA-06/CA-07/CA-08/CA-09/CA-10; PLAN, ciclo de vida y D-04.
  - **Validación:** arranque sin cátedra, sin copia, con copia vacía versionada y con copia anterior, conservando CU-01. Publicar cambios sin Kafka o durante el arranque y comprobar detección por Redis. Cortar/restablecer dependencias aisladas, provocar una excepción de comprobación y verificar que el seguimiento continúa respetando pausas. Coordinar Kafka/scheduler/snapshot con latches; comprobar unicidad y cierre/interrupción, ausencia de conexiones localhost predeterminadas y clasificación histórica distinta de vigencia comprobada.

- [x] **T-07 · Verificar localmente y completar documentación/evidencias**
  - **Objetivo:** demostrar los CA, preservar las funciones existentes y dejar una entrega comprensible y reproducible.
  - **Alcance:** README, Javadoc y diagramas afectados; evidencias breves en este archivo. Revisar dependencias hacia el dominio, puertos entre slices, constructor DI y seguridad de datos/logs. Mantener las APIs CU-04/05/06 y la infraestructura central fuera de alcance. Explicar al usuario el recorrido concreto y los patrones realmente utilizados. Actualizar el cierre con estados y limitaciones reales.
  - **Dependencias:** T-01 a T-06.
  - **Referencias:** RF-01 a RF-09; CA-01 a CA-10; PLAN, validación y regresiones.
  - **Validación:** pruebas enfocadas y `./mvnw -B -ntp clean verify` con JaCoCo; regresiones de snapshot, login técnico, JWT y arranque. Revisar con Sonar CLI/IDE según disponibilidad, escaneo de secretos y `git diff --check`, registrando limitaciones de herramientas sin presentar un análisis rechazado como correcto. Comprobar JAR/Compose con datos ficticios y recursos aislados, sin usar secretos ni volúmenes de desarrollo. Distinguir estos ensayos de una prueba real con cátedra; registrar si esta última no pudo realizarse.

## Corrección posterior al feedback del PR #19

**Estado:** corregido y verificado localmente, 2026-10-24. El análisis remoto
sobre `2a3c3b9` tiene CI correcto con 232 pruebas e importación JaCoCo, pero Gate
fallido por cobertura nueva 78,6 % y fiabilidad E. Evidencia:
[Actions](https://github.com/prog2-perassiferrara/backend-catalogo/actions/runs/40103754770)
y [SonarCloud PR 19](https://sonarcloud.io/dashboard?id=prog2-perassiferrara_backend-catalogo&pullRequest=19).
Este resultado remoto describe la revisión previa, no las correcciones locales.

Se atendieron los 12 hallazgos sin supresiones ni transiciones remotas:

- S3077: acceso al template/namespace bajo el monitor de inicialización; template configurado antes de publicarlo, sin mutarlo después.
- S2095: scheduler administrado por Spring con `destroyMethod=shutdownNow`; el monitor conserva la parada operativa. La regresión de contextos comprueba su cierre incluso con el monitor sustituido.
- Tres S6809: entradas transaccionales públicas y consultas internas privadas, conservando la unidad de escritura/recibo y las lecturas frescas externas.
- S107: CU-02 consulta su metadata mediante `CatalogUpdateRepository`; siete dependencias explícitas, sin agrupar parámetros artificialmente.
- S1128 y cinco S5778: import sin uso eliminado y argumentos preparados antes de las aserciones de excepción.

Pruebas nuevas: fallos de acceso, reintento local con el mismo lote/runId,
notificación adelantada, interrupción y estado no verificable; clasificación de
fallos Redis sin filtrar mensajes; rechazo de esquemas, pérdida del ack y pausas;
copia ausente/vacía, continuidad y cierre del monitor. Ejecutan los componentes
reales y controlan sus fronteras, sin simular transacciones como prueba de atomicidad.
Referencias: RF-02/05/06/07/08/09, CA-02/06/08/09/10 y T-01/T-03 a T-07.

**Verificación posterior:** suite enfocada de 47 pruebas correcta. Luego
`./mvnw -B -ntp clean verify`, 2026-10-24 12:01:21 -03:00, base `2a3c3b9` más
correcciones locales: **270 pruebas, cero fallos/errores/omisiones, BUILD SUCCESS**.
JaCoCo sin diferencias de bytecode: **1.275/1.359 líneas (93,82 %) y 558/688 ramas
(81,10 %)**. Estimación de cobertura nueva mediante diff con `81ae780` y JaCoCo:
578/613 líneas y 251/336 condiciones, **87,36 %**; no es un nuevo Gate remoto.

Sonar for IDE, puente 64120 HTTP 200, **122 Java sin hallazgos**; Javadoc sin
avisos, capas/DI, escaneo de secretos y `git diff --check` correctos. CLI utilizada
para consultar issues, Gate y métricas remotos; Vortex conserva la limitación 403
ya registrada, sin presentarla como análisis correcto ni repetir remediaciones.
Los ensayos JAR/Compose anteriores conservan sus fechas; no se repitieron contra
cátedra ni se modificaron contenedores de desarrollo. No se cambian RF/CA,
presupuestos, esquema SQL, APIs o credenciales; ajuste registrado en PLAN.

Actions/SonarCloud posteriores siguen siendo feedback del usuario, sin tarea de
espera ni cambios de estados remotos/Git por parte del agente.

**Confirmación remota posterior, 2026-10-24:** revisión `3e80c5c`,
[Actions correcto](https://github.com/prog2-perassiferrara/backend-catalogo/actions/runs/40106169310)
y SonarCloud Gate **OK**: cobertura nueva **87,4 %**, fiabilidad/seguridad/
mantenibilidad **A**, duplicación nueva **0 %**. Único hallazgo restante S1128:
import estático de AssertJ sin uso en `CatalogTrackingLifecycleTests`; eliminado
localmente. Cambio sin comportamiento, verificado mediante diff, sin repetir la
suite completa. El resultado remoto corresponde al commit anterior a esa limpieza.
Kafka central conserva la limitación operativa documentada.

## Evidencias de ejecución

- **T-01:** `TechnicalCatalogSettingsTests`, `TechnicalSnapshotAccessTests` y regresiones de CU-01 prueban sesión utilizable, vencimiento/401, resultados tipados y representación sin secretos. La prueba concurrente de `CatalogIncrementalSynchronizationTests` comparte el bloqueo con CU-01 real.
- **T-02:** `CatalogRedisChangesTests` usa Redis real: rango 7 → 10, unión de IDs, deshabilitados/horas, ventana, incremento o entidad ausentes, JSON incompatible, publicación concurrente y límites de bytes/tiempo. No demuestra el mecanismo de publicación central.
- **T-03:** `CatalogUpdatePersistenceTests` usa MySQL real: lote y recibo atómicos, filas ajenas conservadas, trigger que fuerza rollback tras una escritura parcial, referencias/base inválidas, identificadores largos y sensibles a mayúsculas, rechazo idempotente y commit real cuya confirmación se pierde antes de reconciliar.
- **T-04:** `RecoverCatalogUseCaseTests` verifica tres ciclos, fronteras de 30 s/5 min, nuevos bloques y parada permanente. `CatalogIncrementalSynchronizationTests` comprueba duplicados, presupuestos, incertidumbre y exclusión. La prueba de discontinuidad de `CatalogUpdatePersistenceTests` ejecuta CU-03 → CU-01 reales y confirma la nueva base antes de reconocer el evento.
- **T-05:** `CatalogNotificationConsumerTests` ejecuta Kafka/Redis/MySQL reales: offset retenido hasta el commit, reentrega tras cerrar/recrear consumidor, rechazo durable seguido de evento válido y fallo temporal del guardado del rechazo que impide saltar el offset anterior.
- **T-06:** la comprobación programada ejecuta CU-02 y Redis/MySQL sin Kafka operativo y continúa después de fallo al iniciar clientes. Las regresiones de arranque conservan las políticas existentes; aíslan el monitor nuevo para no mezclar objetivos de CU-01. Ensayo completo del 2026-10-23 correcto: JAR con snapshot 7 y comprobación inicial hasta 10; evento hasta 11; Compose sobre la misma DB aplica snapshot 11 y conserva recibos, luego confirma 12. Corte real de Redis conserva copia y registra fallo; restaurarlo permite volver a CURRENT sin reiniciar. Corte de Kafka no impide detectar 13 por Redis; tras restaurarlo, el evento 14 queda reconocido. Health y datos comprobados; recursos propios cerrados, sin tocar volúmenes de desarrollo.
- **T-07:** Sonar for IDE, puente local 64121, HTTP 200 sobre 119 Java sin hallazgos después de las correcciones. Sonar CLI 1.10.0 autenticada en la organización; análisis Java no disponible por Vortex 403, sin tratarlo como resultado limpio ni modificar estados remotos. Javadoc `-Xdoclint:all,-missing` correcto y sin avisos, sin exigir documentación redundante de miembros obvios. Dominio puro, dependencias de aplicación e inyección por constructor revisados; sin `@Data`/`@Autowired`. Secretos y `git diff --check` correctos. Suite final, cobertura y ensayo empaquetado correctos, ver cierre. Guía de lectura y explicación del recorrido/patrones entregadas en el chat de esta implementación.

### Ensayo operativo aportado por el usuario, 2026-10-24

Compose real con MySQL healthy y Catálogo activo. Logs a las 14:19:28-32 UTC:
acceso técnico `READY`, snapshot `SNAPSHOT_APPLIED` y comprobación inicial Redis
`CURRENT (CATALOG_CHECKED)`. Esto verifica acceso/lectura de metadata Redis en
ese instante; no demuestra haber aplicado un incremento central nuevo ni el
mecanismo de publicación del productor.

El usuario contrastó broker y `kafkaCatalogTopic` de `integration` con el consumidor:
coinciden. El broker responde reiteradamente `UNKNOWN_TOPIC_OR_PARTITION`.
Recepción y confirmación de eventos centrales siguen pendientes de verificar;
requiere comprobar con cátedra la disponibilidad del topic/particiones en ese
cluster. No se cambian nombres ni se crean topics centrales. Evidencia recibida
por chat, sin consultar credenciales ni modificar código o contenedores.

## Cierre

**Estado:** 10/10 CA verificados en entornos aislados. El ensayo real parcial
del 2026-10-24 se distingue arriba; no se afirma validación completa contra
cátedra ni se convierte el supuesto del productor en una garantía.

| CA | Estado | Evidencia |
| --- | --- | --- |
| CA-01 | Verificado, aislado | T-02/T-04/T-06: lote 7 → 10 sin Kafka, notificación y seguimiento real JAR/Compose. |
| CA-02 | Verificado, aislado | T-03/T-05: offset retenido hasta commit, reentrega después de recrear consumidor, recibos conservados entre procesos. |
| CA-03 | Verificado, aislado | T-02/T-03: rango completo, trigger SQL que revierte unidad parcial y conserva versión/filas previas. |
| CA-04 | Verificado, aislado | T-02/T-03/T-05: IDs/referencias, deshabilitados, horas, extras y rechazo durable sin aplicación falsa. |
| CA-05 | Verificado, aislado | T-02/T-04: ventana/cambio/entidad ausentes, publicación cambiante y recuperación real por snapshot. |
| CA-06 | Verificado, aislado | T-04: límites 3 ciclos/30 s/5 min con reloj controlado, mensajes durante pausa, siguiente bloque, fallo permanente y nueva base 12. |
| CA-07 | Verificado, aislado | T-01/T-02/T-04: cambio de publicación detectado y exclusión compartida con CU-01 real, sin aceptar lote mezclado. |
| CA-08 | Verificado, aislado | T-03/T-04/T-05/T-06: límites de reintentos, rollback/reconciliación de commit real incierto, guardado de rechazo recuperado y cortes/restauración Redis/Kafka sin reiniciar. |
| CA-09 | Verificado, aislado | Regresiones CU-01 y T-03/T-05/T-06: sin copia/vacío versionado, contextos/consumidor recreados, datos y recibos conservados. |
| CA-10 | Verificado, aislado | T-01/T-04/T-06/T-07: estado en curso/comprobado/espera/fallo, acceso vencido/401, representación de secretos, logs ficticios y escaneo. |

**Regresión:** `./mvnw -B -ntp clean verify`, 2026-10-23 16:24:53 -03:00,
base `81ae780` más cambios locales de esta funcionalidad: **232 pruebas, cero
fallos/errores/omisiones, BUILD SUCCESS**. JaCoCo sin diferencias de bytecode:
**1.228/1.356 líneas (90,56 %) y 520/688 ramas (75,58 %)**. Informes en
`target/surefire-reports/` y `target/site/jacoco/`.

JAR y Compose del proyecto verificados con MySQL/Redis/Kafka y REST ficticios,
clave pública efímera y puertos/redes propios. El JAR no contiene `.env`, claves ni
`setup_probe` de pruebas. Logs del ensayo sin las contraseñas ficticias de REST,
Redis y DB; no se leen secretos reales. Javadoc sin avisos; Sonar IDE sin hallazgos,
CLI limitada por Vortex 403; dependencias de capas/DI, secretos y diff correctos.

**Límites:** el ensayo real del usuario confirma lectura de metadata Redis,
actualizando el diagnóstico anterior de autenticación. No verifica incrementos
centrales, consumo Kafka ni publicación coherente. Persiste el rechazo de metadata
Kafka pese a coincidir topic/broker con `integration`; su causa requiere revisión
externa. Windows y APIs CU-04/05/06 permanecen fuera de estas verificaciones.
Los ensayos locales no sustituyen esos contratos ni el feedback remoto posterior.

**Desviaciones del PLAN:** ajustes técnicos menores registrados en PLAN el
2026-10-23: executor de lectura para el timeout global, configuración JSON
estricta compartida y estado en curso explícito. Las pruebas se agrupan por
frontera/efecto; las regresiones de CU-01 aíslan el monitor y el arranque completo
se comprueba aparte. Se amplía a 20 s la espera de la copia inicial en una prueba
previa de arranque, cuyo límite de 5 s falló con el primer snapshot aún en curso;
conserva todas las aserciones y no cambia plazos del backend. Su MySQL descartable
usa tmpfs después de observar timeout JDBC durante la creación de la tabla Flyway
en el disco del host; sigue ejecutando MySQL real. Las demás pruebas SQL y la
configuración productiva conservan su almacenamiento.
Se mantienen RF/CA, transacciones, presupuestos y contratos,
sin nuevos endpoints ni cambios de políticas. La implementación y los ensayos
aislados no usaron infraestructura central; el ensayo operativo posterior fue
ejecutado por el usuario. No se gestionó Git.

**Retroalimentación posterior:** GitHub Actions/SonarCloud se revisan cuando el
usuario comparte resultados. No constituyen una tarea ni una espera de push/merge
para completar el cierre local; los defectos demostrados se atienden con su
justificación y evidencia correspondientes.
