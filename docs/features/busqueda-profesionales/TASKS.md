# TASKS: Buscar profesionales con filtros locales

**Referencias:** [SPEC aprobada](SPEC.md), [PLAN aprobado](PLAN.md), [issue #10](https://github.com/prog2-perassiferrara/backend-catalogo/issues/10).
**Estado:** Completada y verificada localmente, 2026-10-28.
**Aprobaciones:** SPEC, PLAN y TASKS aprobados por el usuario el 2026-10-28; implementación autorizada («apruebo. implementa»).

**Chequeo previo:** correcto: 7 RF cubiertos por 9 CA, con validación en PLAN y tareas asociadas; dependencias sin ciclos, sin alcance nuevo ni decisiones bloqueantes. Reconfirmar solo la parte afectada si cambia una decisión antes de implementar.

<!-- Cada tarea incluye la validación de su comportamiento. Registrar evidencia
real al ejecutarla; no completar casillas por escribir código. Actions y SonarCloud
posteriores son feedback del usuario, no tareas ni condiciones de espera.
-->

## Tareas

- [x] **T-01 · Definir contratos de búsqueda y categorías**
  - **Objetivo:** representar criterios, vistas leídas y resultados con estado sin exponer infraestructura.
  - **Alcance:** dos puertos de entrada, seis valores de dominio del PLAN y ampliación de `CatalogQueryRepository`; listas defensivas y contratos Javadoc. Reutilizar modelos de profesional/categoría, estado y errores existentes; incorporar los motivos de entrada inválida y falta de copia.
  - **Dependencias:** ninguna.
  - **Referencias:** RF-01/02/03/04/07; CA-01/03/04/07; PLAN, «Módulos y componentes afectados» y «Datos y contratos».
  - **Validación:** comprobar firmas y dependencias: dominio solo JDK, sin DTO, JPA, `Page`, `Pageable` o `Specification`. Verificar contrato de campos opcionales y resultados vacíos; no añadir pruebas que solo repitan accesores de records.

- [x] **T-02 · Implementar filtros y lectura paginada consistente en MySQL**
  - **Objetivo:** obtener coincidencias, categorías y total de una copia confirmada sin duplicados.
  - **Alcance:** `ProfessionalSpecifications`, `JpaSpecificationExecutor`, ampliación del lector/adaptador y mapeo de categoría. AND de filtros opcionales, LIKE literal sin mayúsculas/acentos, `EXISTS` por horario habilitado/día y orden apellido/nombre/ID. Leer metadata primero y resolver categorías en lote en la transacción de lectura existente; conservar la traducción segura de errores. Sin cargar todos los profesionales para filtrarlos en memoria, nuevas tablas o dependencias.
  - **Dependencias:** T-01.
  - **Referencias:** RF-01/02/03/04/06/07; CA-01/02/03/04/06/07/09; PLAN, «Filtros y orden» y persistencia.
  - **Validación:** `CatalogSearchPersistenceTests` con MySQL 8.4.8 real: filtros individuales/combinados, acentos/case y `%_!` literales, estado omitido/verdadero/falso, días y horarios múltiples/deshabilitados. Probar columnas con collation sensible, categorías deshabilitadas, copia vacía, categoría inexistente, páginas con nombres repetidos y total correcto. Observar consultas para detectar categorías cargadas por fila; ninguna modificación de la réplica.

- [x] **T-03 · Resolver búsquedas y categorías con estado local**
  - **Objetivo:** permitir consultar copia anterior con aviso sin presentarla como vigente.
  - **Alcance:** ambos casos de uso y composición por constructor. Validar criterios según los límites aprobados; reutilizar guard, policy y acceso técnico en memoria. Lectura compartida opcional y sin espera; sin ella, leer igualmente la copia confirmada y no acreditar `CURRENT`. Conservar consulta tras reinicio sin bloquear CU-01. Diferenciar falta de copia, fallo local y vacío válido; liberar acceso siempre.
  - **Dependencias:** T-01 y T-02.
  - **Referencias:** RF-03/04/05/06/07; CA-04/05/06/07/09; PLAN, «Solución propuesta» y «Estado, operaciones y errores».
  - **Validación:** `CatalogSearchUseCaseTests` ejecutando casos de uso y política reales con reloj controlado. Probar copia vigente/anterior/ausente, todas las condiciones no vigentes del PLAN, acceso técnico inutilizable, guard sin verificación y lectura fallida. Verificar que no se exige `CURRENT` para devolver datos, que los errores liberan acceso y que ninguna consulta inicia login, recuperación o llamadas centrales.

- [x] **T-04 · Publicar los dos GET y su contrato JSON protegido**
  - **Objetivo:** entregar a KMP búsqueda paginada y categorías con estado en la misma respuesta.
  - **Alcance:** controlador, petición validada, DTO y mapper del PLAN; ampliar el advice sin alterar errores de CU-05. GET `/api/catalog/professionals` y `/api/catalog/categories`, JWT de usuario existente, `problem+json` y `no-store`. Aplicar página 0, tamaño 20, máximo 100, nombre normalizado de hasta 100 y validación segura del desplazamiento. Actualizar README con parámetros, respuestas, códigos y ejemplos ficticios.
  - **Dependencias:** T-03.
  - **Referencias:** RF-01/02/03/04/05/07; CA-01/03/04/05/07/08; PLAN, «HTTP», errores y seguridad.
  - **Validación:** `CatalogSearchHttpTests` con MVC, casos de uso, JPA y JWT firmados reales. Verificar JSON, flags, resumen sin metadata privada, paginación y final vacío. Probar nombre vacío/100/101, categoría/fecha/tipos inválidos, rangos y desbordamientos. JWT ausente/inválido/vencido, servicio y usuario sin rol deben recibir rechazo; CU-05 conserva su código de ID inválido. Confirmar errores seguros y cabeceras sin caché.

- [x] **T-05 · Verificar lecturas durante sincronización y después de reiniciar**
  - **Objetivo:** demostrar coherencia de página, categorías, total y estado en intercalados reales.
  - **Alcance:** `CatalogSearchConcurrencyTests` reutilizando MySQL, escritores y fixtures existentes. Barreras/latches y plazos finitos, sin sleeps arbitrarios ni mecanismos productivos nuevos. Incluir búsqueda y categorías durante escrituras de snapshot/incremento, y copia persistida con verificación nueva pendiente.
  - **Dependencias:** T-02, T-03 y T-04.
  - **Referencias:** RF-03/04/06/07; CA-03/04/05/06/07/09; PLAN, concurrencia y estrategia de validación.
  - **Validación:** pausar el lector SQL tras metadata, confirmar otra copia y comprobar que conserva filas/categorías/total anteriores. Usar una página completa que ejecute COUNT y generaciones con totales distintos. Con escritor real retenido, ambos GET terminan antes de liberarlo y devuelven copia anterior con estado no vigente. Sin copia devuelven 503; con guard nuevo y copia histórica siguen consultables sin fingir verificación actual. Verificar liberación y ausencia de cambios de negocio.

- [x] **T-06 · Completar regresión, revisión local y cierre técnico**
  - **Objetivo:** demostrar los nueve CA y conservar contratos/documentación coherentes.
  - **Alcance:** regresión de consultas/seguridad/CU-01/02/03, ensayo operativo aislado, revisión de Javadoc, límites entre capas, README y diagrama. Completar el cierre de este archivo con evidencia real, límites y desviaciones; no modificar otros repositorios ni el entorno personal.
  - **Dependencias:** T-01 a T-05.
  - **Referencias:** RF-01 a RF-07; CA-01 a CA-09; PLAN, estrategia de validación y regresiones.
  - **Validación:** foco con los cuatro tests propuestos y regresiones pertinentes; luego `./mvnw -B -ntp clean verify`, JaCoCo, `git diff --check`, Sonar CLI/IDE según disponibilidad y revisión Javadoc. Ensayar JAR o Compose aislado con MySQL/cátedra ficticia: ambos GET, filtros, paginación, permisos y copia anterior, observando que consultar no agrega solicitudes REST. Registrar lo ejecutado y lo no comprobable; no presentar esto como incremento Kafka central o KMP probados.

## Cierre

**Estado:** verificado localmente el 2026-10-28 sobre `f4caf6a` más estos cambios.

| CA | Estado | Evidencia |
| --- | --- | --- |
| CA-01 | Verificado | `CatalogSearchPersistenceTests.filtersAreCombinedInSqlAndSchedulesNeverMultiplyResults`: filtros individuales/combinados, nombres completos, acentos, habilitación y caracteres LIKE literales. HTTP verifica la normalización y combinación. |
| CA-02 | Verificado | Persistencia: fechas de lunes/martes/miércoles, horarios habilitados/deshabilitados, múltiples horarios coincidentes y profesional sin horarios; sin duplicar resultados o totales. |
| CA-03 | Verificado | Categorías ordenadas con flags originales, categoría deshabilitada y copia vacía en persistencia/HTTP. |
| CA-04 | Verificado | Casos de uso/HTTP: cero coincidencias y categoría inexistente con 200; sin copia con `CATALOG_COPY_UNAVAILABLE`, fallo SQL con `CATALOG_UNAVAILABLE`, ambos 503. |
| CA-05 | Verificado | Casos de uso: fases no vigentes, comprobación vencida, acceso inutilizable y verificación inicial pendiente. Concurrencia recrea guard sobre DB con estado histórico `CURRENT`; solo una ejecución correcta de CU-02 habilita vigencia. JAR conserva búsquedas con comprobación de más de 60 s y REST ficticio no disponible. |
| CA-06 | Verificado | `CatalogSearchConcurrencyTests`: SELECT de metadata intercalado con commit de snapshot conserva filas/categorías/COUNT de versión 7 frente a nueva versión 8 con otro total. Ambos GET completan durante escrituras reales retenidas de snapshot/incremento, con copia anterior y `SYNCHRONIZING`. |
| CA-07 | Verificado | Persistencia/HTTP: recorrido completo con desempate por ID, tamaños 1/20/100, totales correctos y final vacío. Estadísticas Hibernate: metadata, página, COUNT y categorías se resuelven con cuatro consultas, sin N+1. |
| CA-08 | Verificado | HTTP con JWT firmados y seguridad real: 400/401/403, tipos/rangos/overflow, nombre 100/101 y fecha imposible. Error de ID de CU-05 preservado, sin secretos ni caché. |
| CA-09 | Verificado | Fotografías SQL antes/después incluyen categorías, profesionales, horarios, estado y recibos; puertos locales en casos de uso. JAR aislado: búsquedas repetidas agregan cero solicitudes REST. |

**Regresión ejecutada:**

- Foco SQL/casos de uso/HTTP: 58 pruebas correctas; concurrencia: 6 correctas. Total nuevo: 64 pruebas, sin fallos/errores/omisiones.
- `./mvnw -B -ntp clean verify`: **406 pruebas, cero fallos/errores/omisiones, BUILD SUCCESS**, finalizado el 2026-10-28 a las 10:12:38 -03:00. Incluye CU-01/02/03, agenda/estado, JWT y recuperación JDBC. JaCoCo: 1.592/1.650 líneas (96,48 %) y 697/838 ramas (83,17 %).
- Sonar for IDE: puentes 64120/64121, 32 Java afectados sin hallazgos. Sonar CLI: escaneo de secretos correcto en esos archivos; no se reintentó Vortex previamente rechazado con 403 ni se afirma un análisis estático CLI completo. El análisis remoto posterior corresponde al código que publique el usuario.
- Javadoc de 25 Java productivos afectados: `-Xdoclint:all,-missing`, sin errores ni avisos. Revisadas dependencias entre capas, constructor DI, enlaces, contratos, diagrama y `git diff --check`.
- JAR empaquetado con MySQL 8.4.8/Redis 8.4.0 descartables y REST ficticio: dos GET, filtros combinados, LIKE literal, paginación, permisos y 400/401/403 correctos. Tras envejecer la comprobación, ambos GET devuelven copia con `STALE`; REST ficticio no disponible. Solo hubo login y snapshot durante el arranque, ninguna petición REST adicional por consultar. Procesos/contenedores aislados cerrados y claves ficticias eliminadas.

**Límites:** el ensayo utiliza un broker ficticio deliberadamente inaccesible;
no verifica un incremento Kafka central ni la parte KMP. No se modificaron el
Compose personal, la infraestructura de cátedra, secretos ni otros repositorios.
Concurrencia/reinicio lógico se comprobaron con escritores y DB reales; el ensayo
JAR no reinició el proceso. El diagrama tiene sintaxis Mermaid comprobada y coincide
con las responsabilidades implementadas; no se verificó su apariencia renderizada.

**Desviaciones del PLAN:** ninguna de alcance, contratos o decisiones aprobadas.
El ensayo operativo se hizo con JAR, según la alternativa prevista.
