# TASKS: Consultar profesional, agenda y estado del catálogo

**Referencias:** [SPEC aprobada](SPEC.md), [PLAN aprobado](PLAN.md), issues [#11](https://github.com/prog2-perassiferrara/backend-catalogo/issues/11) y [#12](https://github.com/prog2-perassiferrara/backend-catalogo/issues/12).
**Estado:** Completada y verificada localmente, 2026-10-27.
**Aprobaciones:** SPEC el 2026-10-26; PLAN el 2026-10-27. TASKS aprobadas e implementación autorizada el 2026-10-27 («apruebo. implementa.»).

**Chequeo previo:** correcto: 7 RF cubiertos por 9 CA, todos con validación en PLAN y tareas asociadas; dependencias sin ciclos, sin alcance nuevo ni decisiones bloqueantes. Reconfirmar solo la parte afectada si cambia una decisión antes de implementar.

<!-- Cada tarea incluye la validación de su comportamiento. Registrar evidencia
real al ejecutarla; no completar casillas por escribir código. Actions y SonarCloud
posteriores son feedback del usuario, no tareas ni condiciones de espera.
-->

## Tareas

- [x] **T-01 · Adaptar la coordinación común y la verificación de esta ejecución**
  - **Objetivo:** permitir lecturas concurrentes sin presentar una sincronización o comprobación histórica como catálogo vigente.
  - **Alcance:** `CatalogSynchronizationGuard`, composición y adaptación de CU-01/02/03 conforme a D-02. Sustituir el bloqueo suelto por lectura/escritura reentrante y mantener la necesidad de verificación en memoria. Solo una comprobación correcta o incremento validado y confirmado la satisface; snapshot, duplicados y rechazos no la simulan. Invalidar ante fallos aunque no puedan persistirse. Conservar políticas, presupuestos y reconciliación JDBC existentes; añadir nota técnica breve a los PLAN afectados.
  - **Dependencias:** ninguna; parte de la sincronización existente, incluido el arreglo JDBC de `176d74d`.
  - **Referencias:** RF-02/05/06; CA-04/05/08/09; PLAN, «Estado, operaciones y errores».
  - **Validación:** regresión enfocada de snapshot, incrementos, recuperación y commit incierto. Probar reentrada CU-02 → CU-03 → CU-01, liberación tras excepción, reinicio que exige verificación y fallo no persistido que no revive un `CURRENT` anterior. Cambio preparatorio separable para commit del usuario.

- [x] **T-02 · Definir contratos de consulta y política de vigencia**
  - **Objetivo:** representar datos y estado funcional con una única interpretación de D-01.
  - **Alcance:** puertos de entrada/salida, modelos, enums y excepciones del PLAN; `CatalogStatusPolicy` con reloj inyectado. Conservar dominio sin dependencias externas y documentar contratos mediante Javadoc. Distinguir existencia de copia de vigencia, incluidos catálogos vacíos con versión confirmada.
  - **Dependencias:** ninguna; utiliza los modelos y decisiones aprobados.
  - **Referencias:** RF-01/02/03/04/05/07; CA-01/02/03/04/05/06; PLAN, componentes y datos/contratos.
  - **Validación:** `CatalogStatusPolicyTests` con reloj controlado: antes de 60 s, exactamente en el límite y después; comprobación ausente/futura, versiones distintas, acceso inutilizable y cada fase con/sin copia. Detectar aceptación tras reinicio o con fallo/trabajo en curso aunque la fecha sea reciente. No ejecutar consultas ni credenciales centrales para evaluar el estado.

- [x] **T-03 · Leer profesional, horarios y metadata en una misma vista MySQL**
  - **Objetivo:** obtener datos completos de una copia confirmada, sin mezclar versiones ni confundir errores con ausencia.
  - **Alcance:** `JpaCatalogQueryAdapter` y bean separado `TransactionalCatalogQueryReader`, consulta de horarios por profesional y conversiones a dominio. Transacción de lectura `REQUIRES_NEW`, `REPEATABLE_READ`, timeout de 5 s; traducción segura de fallos que atraviesan el proxy. Reutilizar tablas/migraciones y ordenar horarios según el PLAN, sin filtrar deshabilitados ni generar slots.
  - **Dependencias:** T-02.
  - **Referencias:** RF-01/03/05/06/07; CA-01/02/03/08/09; PLAN, persistencia y estrategia de validación.
  - **Validación:** `CatalogQueryPersistenceTests` con MySQL 8.4.8 real: profesional con varios horarios y otro profesional, deshabilitados, ausencia, colección vacía y fallo local. Intercalar SELECT y commit reales para demostrar una vista consistente. Comprobar resultados materializados, orden estable y ninguna escritura de negocio; no reemplazar la transacción por mocks.

- [x] **T-04 · Resolver CU-05 y CU-06 sobre datos y estado locales**
  - **Objetivo:** entregar datos habilitantes solo con vigencia acreditada y mantener consultable el estado funcional.
  - **Alcance:** ambos casos de uso, política y coordinador compartidos, repositorio local y puerto técnico existente. Evaluar vigencia al terminar la lectura; distinguir entrada, ausencia e indisponibilidad. CU-05 rechaza sin esperar al sincronizador; CU-06 puede leer solo metadata confirmada y comunicar sincronización cuando no obtiene acceso compartido. Aplicar la protección de arranque del PLAN y liberar acceso siempre. Sin llamadas centrales, reintentos de consulta ni inicio de recuperación.
  - **Dependencias:** T-01, T-02 y T-03.
  - **Referencias:** RF-01 a RF-07; CA-01/02/03/04/05/06/08/09; PLAN, recorridos y operaciones.
  - **Validación:** `ProfessionalAgendaQueryTests` y casos reales de estado con fronteras controladas. Verificar 404 solo sobre copia vigente, flags y agendas vacías, pérdida de vigencia durante lectura, copia ausente/fallida y lectura imposible. Observar datos, versión, recibos y gateways para demostrar ausencia de efectos y peticiones centrales, también tras repetir consultas fallidas.

- [x] **T-05 · Exponer los tres GET con DTO, errores y permisos específicos**
  - **Objetivo:** publicar el contrato aprobado para Turnos y KMP preservando el aislamiento de identidades.
  - **Alcance:** controlador, DTO, mapper y manejador de errores del PLAN; starter de validación gestionado por Boot y reglas específicas en seguridad. Aplicar `@Positive`, conversión segura, `application/problem+json` y `Cache-Control: no-store`. Reutilizar el JWT/rol de servicio de D-03, sin cambiar claves, emisión de Turnos ni entrega técnica. Actualizar README con rutas, campos, permisos, ejemplos ficticios y errores.
  - **Dependencias:** T-04.
  - **Referencias:** RF-01/03/04/07; CA-01/02/03/04/06/07/09; PLAN, HTTP, errores y configuración.
  - **Validación:** `CatalogQueryHttpTests` con MVC, casos de uso y lectura reales; JWT firmados. Comprobar JSON, horas locales, instantes UTC, estado resumido/detallado, ausencia de secretos y códigos 400/404/503. Probar ID cero/negativo/no numérico/overflow, JWT ausente/inválido/vencido, identidad y permisos incorrectos, incluido usuario con rol de servicio. Mantener regresión del token central, health, JWT y CORS.

- [x] **T-06 · Verificar concurrencia y reinicio de extremo a extremo local**
  - **Objetivo:** proteger la coherencia y el acceso durante escrituras o arranque, además de los casos secuenciales.
  - **Alcance:** escenarios de `CatalogQueryConcurrencyTests` y pruebas de integración del PLAN, reutilizando fixtures. Controlar intercalados con barreras/latches y plazos finitos; ejecutar escritores de snapshot/incremento reales. No introducir mecanismos productivos adicionales para pruebas.
  - **Dependencias:** T-01, T-03, T-04 y T-05.
  - **Referencias:** RF-02/05/06; CA-04/05/08/09; PLAN, concurrencia y validación.
  - **Validación:** dos lectores admitidos simultáneamente; exclusión del escritor, CU-05 no habilitante y CU-06 disponible durante sincronización; consultas iniciales que no impiden CU-01. Comprobar liberación tras fallo y copias/versiones sin mezcla. Reabrir contexto sobre la misma DB: el `CURRENT` anterior no habilita hasta una nueva comprobación válida. Distinguir estos ensayos de un incremento recibido de cátedra.

- [x] **T-07 · Completar regresión, revisión local y cierre técnico**
  - **Objetivo:** demostrar aceptación y conservar una ejecución reproducible sin modificar los otros repositorios.
  - **Alcance:** terminar pruebas afectadas, revisar Javadoc, contratos/diagramas y README; completar el cierre de este archivo con evidencia real y desviaciones. Revisar límites entre capas, constructor DI y ausencia de secretos. Ensayo JAR o Compose aislado con MySQL y sesión central ficticia según el PLAN, sin modificar el entorno personal ni la infraestructura central.
  - **Dependencias:** T-01 a T-06.
  - **Referencias:** RF-01 a RF-07; CA-01 a CA-09; PLAN, estrategia de validación y regresiones.
  - **Validación:** pruebas enfocadas con `./mvnw -B -ntp -Dtest=... test`; luego `./mvnw -B -ntp clean verify`, JaCoCo, `git diff --check` y revisión local Sonar CLI/IDE según disponibilidad. Incluir regresiones de CU-01/02/03 y recuperación JDBC sin debilitar aserciones. En el ensayo operativo comprobar los tres GET, permisos, límite de vigencia y reinicio con la misma DB. Registrar límites reales de herramientas/entorno; no presentar el evento central aún pendiente como verificado.

## Cierre

**Estado:** verificado localmente el 2026-10-27 sobre `176d74d` más estos cambios,
con implementación autorizada y sin cambios de Git por el agente.

| CA | Estado | Evidencia |
| --- | --- | --- |
| CA-01 | Verificado | `CatalogQueryHttpTests.deliversTheCompleteAgendaWithFreshnessFlagsAndStableLocalTimes`: HTTP/JPA/JWT reales, datos, orden y comprobación conjunta. |
| CA-02 | Verificado | HTTP: ID 0/negativo/no numérico/overflow, ausencia solo vigente, copia vacía confirmada y lectura SQL fallida diferenciadas. |
| CA-03 | Verificado | HTTP/SQL: profesional deshabilitado, horarios habilitados/deshabilitados, lista completa ordenada y profesional sin horarios. |
| CA-04 | Verificado | `CatalogStatusPolicyTests`, HTTP sin copia/fallo y `CatalogQueryConcurrencyTests` durante escrituras reales; CU-05 no habilitante y CU-06 legible. |
| CA-05 | Verificado | Política antes/en/después de 60 s y fecha futura; consultas con acceso inutilizable, vencimiento durante lectura, fallo no persistido y nuevo coordinador sobre la misma DB. JAR reiniciado durante sincronización rechaza y después vuelve a `CURRENT`. |
| CA-06 | Verificado | HTTP con filtros/casos de uso/SQL reales: resumen y detalle, estado fallido/sin copia con 200, lectura fallida con 503 y respuestas sin diagnósticos ni secretos. |
| CA-07 | Verificado | `CatalogQueryHttpTests` y `SecurityHttpTests`: firma, vencimiento, sujeto/perfil/rol, usuario con rol de servicio, 401/403 y regresión de token central/health/CORS. |
| CA-08 | Verificado | `CatalogQueryConcurrencyTests`: dos lectores, escritores reales de snapshot/incremento, SELECT intercalados con commit y consultas iniciales sin ocupar el acceso requerido por CU-01. |
| CA-09 | Verificado | Fotografías SQL antes/después de consultas y fallos, puertos locales observados y JAR aislado: repetir GET no añade solicitudes REST; solo login/snapshot por arranque. El recorrido de lectura no consume Redis/Kafka ni inicia recuperación. |

**Regresión ejecutada:**

- Foco de coordinación: 41 pruebas correctas.
- Foco de política, consultas, HTTP, concurrencia y seguridad: 89 pruebas correctas. Después se añadió el caso de catálogo vacío confirmado, incluido en la suite completa.
- `./mvnw -B -ntp clean verify`: **342 pruebas, cero fallos/errores/omisiones, BUILD SUCCESS**, finalizado el 2026-10-27 a las 16:57:39 -03:00. Incluye CU-01/02/03, rollback/commit incierto y las regresiones de JWT/arranque. JaCoCo: 1.467/1.530 líneas (95,88 %) y 632/768 ramas (82,29 %), sin desalineación de bytecode.
- Sonar for IDE, puente local 64120: 44 Java afectados, HTTP 200 y ningún hallazgo. Sonar CLI 1.10.0 autenticada: escaneo de secretos de esos Java correcto. El acceso Vortex previamente rechazado con 403 no se reintentó; no se presenta como análisis estático CLI correcto. El feedback remoto posterior corresponderá a la revisión publicada por el usuario.
- Javadoc de los 29 Java productivos afectados: `-Xdoclint:all,-missing`, sin errores ni avisos. Dependencias entre capas, constructor DI, enlaces/documentos y `git diff --check` revisados.
- JAR empaquetado con MySQL 8.4.8/Redis 8.4.0 descartables y REST ficticio: tres GET, permisos, 400/404/503, rechazo de comprobación de más de 60 s y reinicio sobre la misma DB correctos. Hubo exactamente cuatro solicitudes REST ficticias en dos arranques: login y snapshot por cada uno. Durante el segundo snapshot retenido, agenda devuelve 503 y estado informa copia conservada/sincronización; al completar vuelve a `CURRENT`. Recursos aislados cerrados/eliminados.

**Límites:** no se modificaron Turnos/KMP, secretos, Compose personal ni infraestructura
central. El ensayo usó un broker ficticio deliberadamente inaccesible para comprobar
las consultas con seguimiento Redis; no prueba un evento Kafka real de cátedra.
Ese ensayo de integración central continúa pendiente en su funcionalidad correspondiente.
Diagramas revisados en texto conforme a las responsabilidades implementadas; la
apariencia renderizada sigue sin verificarse.

**Desviaciones del PLAN:** ninguna de alcance, contrato o decisiones aprobadas.
La prueba SQL de vista consistente y la de exclusión del coordinador quedaron
juntas en `CatalogQueryConcurrencyTests`, reutilizando el mismo entorno real,
en vez de añadir otra clase de persistencia. El ensayo operativo se realizó con
el JAR, sin requerir reconstruir el Compose personal.
