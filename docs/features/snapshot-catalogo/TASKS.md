# TASKS: Inicializar o reconstruir el catálogo mediante snapshot

**Referencias:** [SPEC aprobada](SPEC.md), [PLAN aprobado](PLAN.md), issue #7.
**Estado:** T-01 commiteada por el usuario. Segunda entrega implementada y verificada localmente el 2026-10-17. T-06 conserva pendiente únicamente la revisión de Actions/SonarCloud después del push del usuario.

## Primera entrega: refactorización compartida

- [x] **T-01 · Separar configuración compartida sin cambiar comportamiento**
  - **Objetivo:** preparar la configuración que consumirán ambos slices y entregar un cambio independiente de snapshot.
  - **Alcance:** trasladar `IntegrationProperties`, su validación temprana y el reloj UTC a `infrastructure/config`; adaptar wiring, imports, pruebas y documentación afectada. Conservar las variables, mensajes seguros, políticas de sesión y el runner técnico actual. No crear todavía el slice de sincronización, sus puertos, tablas o nuevo coordinador.
  - **Dependencias:** ninguna.
  - **Referencias:** RF-01/RF-08; regresiones de CA-01/CA-08; PLAN, configuración y orden de implementación.
  - **Validación:** suite existente con `./mvnw -B -ntp clean verify`; comprobar validación de configuración antes de abrir conexiones, un único reloj/bean de propiedades, login técnico y seguridad existentes. Revisar calidad local disponible y `git diff --check`; no añadir pruebas que solo comprueben nombres de paquetes.
  - **Entrega:** explicar archivos trasladados y resultados; presentar al usuario para su commit separado. T-02 y siguientes comienzan después de que el usuario confirme ese commit. El agente no modifica Git.

## Segunda entrega: funcionalidad de snapshot

- [x] **T-02 · Definir el snapshot y consumir su contrato HTTP**
  - **Objetivo:** obtener una copia completa interpretable, preservando los valores externos.
  - **Alcance:** modelos/validación, puerto `CatalogSnapshotGateway`, WebClient síncrono, DTO y mapper manual. Distinguir ausencia de campo de valores válidos vacíos/false; comprobar referencias e IDs duplicados. Incorporar tiempos 5/10/20 s y límite de respuesta del PLAN, sin reintentos ocultos ni nuevas dependencias.
  - **Dependencias:** T-01 entregada y commiteada por el usuario.
  - **Referencias:** RF-02/RF-03/RF-08; CA-03/CA-08/CA-09.
  - **Validación:** dominio real y servidor HTTP ficticio para copia válida, entidades deshabilitadas, colecciones vacías/ausentes, tipos/referencias inválidos, ambas horas contractuales, errores sin `code`, timeout y exceso de payload. Afirmar la solicitud y bearer enviados; verificar diagnósticos sin secretos ni cuerpos externos.

- [x] **T-03 · Persistir datos y versión mediante el escritor transaccional**
  - **Objetivo:** reemplazar una copia como unidad indivisible y conservar la última confirmada.
  - **Alcance:** migración V2, cuatro entidades/tablas, repositorios y mapper; `JpaCatalogSnapshotAdapter` invoca el bean separado `TransactionalCatalogSnapshotWriter` con `@Transactional`. Reemplazar en orden de referencias, confirmar metadata/runId con los datos y rechazar versiones menores bajo bloqueo. Registrar intentos sin modificar la versión aplicada por adelantado.
  - **Dependencias:** T-02, para modelos y contrato de snapshot.
  - **Referencias:** RF-03/RF-04/RF-05/RF-06/RF-07; CA-02/CA-03/CA-04/CA-05/CA-06/CA-07/CA-09.
  - **Validación:** MySQL Testcontainers con repositorios, mapper y proxy transaccional reales: primera copia, reemplazo, ausencia de residuos, repetición, copia vacía, FKs y versión menor. Provocar un fallo después de modificar datos y verificar rollback; probar lectura concurrente multitabla/versión en RR con latches. Reabrir contexto sobre la misma base y comprobar persistencia. Verificar traducción de errores al regresar del proxy y consulta fresca de metadata para resultado incierto; no sustituir las transacciones por mocks.

- [x] **T-04 · Coordinar carga, acceso técnico y reintentos**
  - **Objetivo:** completar un ciclo limitado, sin duplicar efectos ni confundir incertidumbre con fallo demostrado.
  - **Alcance:** `SynchronizeCatalogUseCaseImpl`, sus puertos y resultados; adaptador hacia los puertos públicos de `integration` y nuevo caso de invalidación dentro de ese slice. Controlar un ciclo a la vez, tres intentos totales y pausas de 5 s; reutilizar candidato/runId durante reintentos locales. Verificar metadata antes de repetir un guardado incierto. Mantener estados de copia y acceso técnico separados.
  - **Dependencias:** T-02 y T-03.
  - **Referencias:** RF-02/RF-06/RF-07/RF-08; CA-04/CA-06/CA-07/CA-08.
  - **Validación:** ejecutar el caso de uso real con reloj y límites controlados. Probar descarga temporal seguida de éxito, fallo temporal de guardado sin nueva descarga, límite agotado, fallo terminal y ejecución concurrente rechazada como ocupada. Combinar transacción real con pérdida controlada de confirmación: runId aplicado evita otra escritura; sin confirmación verificable no se declara éxito. Un 401 invalida la sesión real de `integration` y no dispara login ni afecta permisos de usuario.

- [x] **T-05 · Conectar el arranque y la recuperación tras reinicio**
  - **Objetivo:** cargar snapshot en cada arranque únicamente después de obtener acceso técnico utilizable.
  - **Alcance:** sustituir el runner técnico por `CatalogStartupRunner`, coordinador fuera de los slices que consume sus puertos públicos. Conservar executor único, interrupción, unicidad y presupuesto de reintentos técnicos; iniciar el ciclo de snapshot después de AVAILABLE. Adaptar fixtures HTTP y las expectativas de solicitudes existentes.
  - **Dependencias:** T-04.
  - **Referencias:** RF-01/RF-05/RF-07/RF-08; CA-01/CA-05/CA-07/CA-08.
  - **Validación:** arranque con y sin copia, éxito técnico antes de snapshot, integración no disponible sin solicitud de snapshot y nuevo arranque con copia previa. Mantener pruebas de PENDING, límite, fallo inesperado y cierre/interrupción. Verificar un único login, ausencia de renovación por fallo del snapshot y conservación del JWT utilizable ante fallos distintos de 401/vencimiento. Un intento interrumpido no publica datos parciales.

- [ ] **T-06 · Verificar la entrega y documentar evidencias**
  - **Objetivo:** demostrar todos los CA y dejar ejecución reproducible y documentación coherente con lo implementado.
  - **Alcance:** README, diagramas/documentos afectados y evidencias breves en este archivo. Revisar propiedad de datos, imports de dominio/aplicación, contratos públicos entre slices, constructor/Javadoc y ausencia de secretos. No agregar APIs de CU-04/05/06, Kafka/Redis o recuperación incremental.
  - **Dependencias:** T-02 a T-05.
  - **Referencias:** RF-01 a RF-08; CA-01 a CA-09.
  - **Validación:** `./mvnw -B -ntp clean verify`, JaCoCo y revisión de caminos relevantes; Sonar CLI/IDE según acceso y `git diff --check`. Comprobar JAR/Compose con infraestructura aislada y datos ficticios, sin tocar `.env`, claves o volúmenes de desarrollo. Después del push del usuario, revisar Actions y SonarCloud, distinguiendo evidencia local/remota y sus limitaciones.

## Evidencias de ejecución

### T-01, 2026-10-17

- `IntegrationProperties` trasladada a `infrastructure/config`; nuevo `CatalogConfiguration`
  aloja su validación temprana, el bean de propiedades y el reloj UTC. Se conservan
  nombres de beans, variables, validaciones y mensajes. Configuración de cliente y
  runner técnico permanece en `integration`, sin cambiar sus políticas.
- Imports de cliente y pruebas adaptados; README y PLAN de integración actualizados.
  No se añadieron dependencias, pruebas de nombres de paquetes ni código de snapshot.
- `./mvnw -o -B -ntp clean verify`: BUILD SUCCESS, 130 pruebas, cero fallos,
  errores u omitidas; finalizó a las 19:32:45 -03:00. Incluye arranque real con MySQL,
  rechazo de configuración inválida, login técnico y seguridad HTTP. El contexto
  completo resuelve las propiedades y el reloj compartidos sin beans duplicados.
- JaCoCo local: 355/370 líneas (95,9 %) y 193/218 ramas (88,5 %).
- Sonar CLI, escaneo de secretos de fuentes/documentos e informes: sin secretos.
  SonarQube for IDE, puerto 64121: HTTP 200, seis Java solicitados y cero hallazgos.
  No se ejecutó análisis remoto; la revisión local no sustituye SonarCloud de CI.
- `git diff --check`: sin errores. T-01 lista para el commit independiente del
  usuario; T-02 a T-06 pendientes, sin implementación de CU-01 todavía.

### Segunda entrega, 2026-10-17

- **T-02:** `CatalogSnapshotValidationTests` y `CatalogSnapshotClientTests`: 22 y 15
  escenarios, respectivamente. Copias completas/vacías, deshabilitados, campos
  ausentes, coerciones inválidas, duplicados, referencias, horas y errores con/sin
  código. Se verifican límite de cuerpo, timeout global, lectura detenida y corte
  posterior a cabeceras. Un 401 recibido se conserva aunque falle la lectura del
  cuerpo; HTTP 500/503 siguen siendo temporales. No hay login ni reintentos ocultos.
- **T-03:** diez pruebas sobre MySQL 8.4.8 real. Actualización V1 a V2, reemplazo,
  repetición/runId, versión igual/menor, FK, catálogo vacío y precisión temporal.
  Trigger SQL fuerza un fallo después de modificar datos; otra prueba aborta la
  conexión transaccional real. Ambas conservan copia/versión previas. Una escritura
  pausada verifica copia anterior durante el reemplazo, nueva tras commit y lectura
  RR coherente. Contextos cerrados/reabiertos conservan datos. Los spies delegan al
  repositorio real; no sustituyen transacciones.
- **T-04:** catorce pruebas del caso de uso y tres de acceso técnico real. Presupuesto
  compartido de tres intentos, reutilización de candidato/runId, fallos terminales,
  metadata no verificable, exclusión concurrente y cancelación antes de escribir.
  Una frontera controlada pierde la confirmación después de un commit MySQL real:
  la reconciliación reconoce el runId sin volver a descargar ni escribir. 401 o
  vencimiento cierran la sesión real; otros fallos no renuevan ni revocan el JWT.
- **T-05:** cuatro pruebas del coordinador y dieciséis del contexto completo,
  conservando las regresiones técnicas existentes. Login precede a snapshot,
  acceso no disponible impide descargar, eventos de arranque repetidos no duplican
  el ciclo y un fallo de snapshot no invalida por sí mismo la sesión técnica.
  Dos reinicios descargan de nuevo sobre una copia existente; 401 conserva datos
  mientras cierra el acceso. Se mantienen health, seguridad y validación temprana.
- **Suite final:** `./mvnw -o -B -ntp clean verify`, BUILD SUCCESS, 199 pruebas,
  cero fallos, errores u omitidas; finalizó a las 20:01:51 -03:00. JaCoCo limpio:
  694/750 líneas (92,5 %) y 302/348 ramas (86,8 %).
- **JAR/Compose:** ensayo con infraestructura aislada, servidor y credenciales
  ficticios, usando Dockerfile/Compose propios con un override temporal. Carga de
  versión 9, hora SQL `09:00:00`, nueva carga tras reinicio, conservación de copia
  después de exactamente tres 503 y health 200. El JAR local recupera la misma
  base persistida y vuelve a cargar. Cuatro arranques producen cuatro logins, tres
  snapshots correctos y tres intentos fallidos; sin login adicional por reintento.
  Se retiraron contenedores, volumen, redes e imagen de ese ensayo. Evidencia:
  `/tmp/catalog-snapshot-smoke.log`. No se leyó `.env`, ni se usaron claves reales
  ni se tocaron recursos de desarrollo. No se probó cátedra real ni su volumen de datos.
- **Calidad local:** escaneos Sonar CLI sin secretos; imports de capas y
  `git diff --check` correctos. Mermaid 12.1.0 interpreta los cuatro bloques de
  PLAN de integración/snapshot; apariencia renderizada no revisada. Sonar IDE:
  HTTP 200, 80 Java solicitados, solo S4502 de CSRF ya revisado en la funcionalidad
  de seguridad. Sin cambios a esa política ni nuevos hallazgos informados por IDE.
- **T-06 pendiente remoto:** después del commit/push del usuario, revisar Actions
  y SonarCloud de estos cambios. La evidencia local no demuestra un Quality Gate
  remoto nuevo. No se modificó Git ni se implementaron CU-02 a CU-06.
