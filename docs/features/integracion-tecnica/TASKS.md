# TASKS: Autenticación técnica y configuración de cátedra

**Referencias:** [SPEC aprobada](SPEC.md), [PLAN aprobado](PLAN.md).
**Estado:** Implementado y verificado; login real confirmado por el usuario.
**Aprobación del PLAN:** confirmada por el usuario en esta sesión, 2026-10-08.

Cinco tareas para #6. El usuario autorizó implementar en esta sesión, 2026-10-08. Endpoint y protección para Turnos quedan en #13. Las pruebas se incorporan con el comportamiento que verifican; los resultados se registrarán al ejecutarlas.

## Tareas y dependencias

- [x] **T-01 · Modelar y conservar la sesión técnica**
  - **Objetivo:** representar acceso disponible, pendiente o fallido sin publicar una sesión parcial.
  - **Alcance:** modelos y puertos del slice `integration`; adaptador en memoria con reemplazo atómico. JWT, vencimiento conocido, configuración y estados locales separados del aprovisionamiento externo. Sin tablas ni migraciones.
  - **Dependencias:** ninguna.
  - **Referencias:** RF-03, RF-04; CA-04, CA-05, CA-11.
  - **Validación:** probar sesiones completas e incompletas y lecturas coherentes tras reemplazo; comprobar que sus representaciones de texto no contienen secretos ficticios.

- [x] **T-02 · Incorporar configuración y cliente de cátedra**
  - **Objetivo:** autenticar y recuperar configuración respetando el contrato externo.
  - **Alcance:** propiedades externas obligatorias, validación local, starter WebFlux y adaptador WebClient síncrono. POST de login con `rememberMe: true`; GET autenticado de configuración sin envoltorio. DTO/mapeo, lectura de `exp` si existe, timeouts de 5 s y errores por HTTP/`code`, sin exponer `detail` ni cuerpos sensibles.
  - **Dependencias:** T-01.
  - **Referencias:** RF-01, RF-02, RF-04, RF-05; CA-01, CA-02, CA-03, CA-05, CA-06, CA-07.
  - **Validación:** usar un servidor HTTP ficticio del JDK y el cliente real. Verificar rutas, body y bearer; estados y respuestas inválidas; rechazos sin `code`; red interrumpida o demorada; ausencia de secretos en diagnósticos. No llamar al registro técnico ni a cátedra real.

- [x] **T-03 · Resolver un intento y consultar el token**
  - **Objetivo:** habilitar acceso únicamente con sesión utilizable y conservar el mismo JWT durante `PENDING`.
  - **Alcance:** implementar los dos casos de uso. Distinguir resultados disponibles, recuperables y terminales; validar `PROVISIONED`, configuración completa e identidad esperada. Consultar el token con tiempo controlable. Ante vencimiento o 401 de cátedra, invalidar acceso sin renovar automáticamente.
  - **Dependencias:** T-01, T-02.
  - **Referencias:** RF-02, RF-03, RF-05, RF-06; CA-02, CA-03, CA-04, CA-06, CA-07, CA-11.
  - **Validación:** probar los cuatro estados de aprovisionamiento, sesión parcial y transición desde `PENDING` sin segundo login. Comprobar token antes y desde su vencimiento, ausencia de `exp` y rechazo 401. Una consulta local del token no inicia autenticación.

- [x] **T-04 · Ejecutar el ciclo limitado al arrancar**
  - **Objetivo:** recuperar acceso ante fallos temporales sin bloquear el servicio ni reintentar indefinidamente.
  - **Alcance:** `ApplicationReadyEvent`, ExecutorService de un hilo, hasta 3 intentos totales y espera de 5 s entre intentos recuperables. Detener ante éxito o fallo terminal; agotado el límite, conservar backend activo sin integración hasta reinicio. Cerrar e interrumpir la tarea con Spring.
  - **Dependencias:** T-03.
  - **Referencias:** RF-01, RF-05, RF-06; CA-01, CA-07, CA-09, CA-10, CA-11.
  - **Validación:** probar fallo/PENDING seguido de éxito, fallo terminal, agotamiento y nuevo contexto tras reinicio. Verificar cantidad exacta de peticiones, ausencia de otro ciclo y cierre sin nuevos intentos. Usar duraciones de prueba y sincronización controladas, sin esperas arbitrarias.

- [x] **T-05 · Integrar ejecución, documentar y verificar**
  - **Objetivo:** entregar ejecución reproducible y conservar las garantías del setup.
  - **Alcance:** variables en `application.yaml`, `.env.example` y Compose; README con registro manual, arranque, reintentos y recuperación. Adaptar las pruebas existentes a configuración y HTTP ficticios. Mantener MVC, MySQL, Flyway y health; documentar la entrega a Turnos pendiente de #13.
  - **Dependencias:** T-02, T-04.
  - **Referencias:** RF-01 a RF-07; CA-01 a CA-11.
  - **Validación:** ejecutar `./mvnw verify` y comprobar los modos Compose/local con datos ficticios. Verificar configuración local inválida frente a cátedra inaccesible, health y ausencia de secretos en archivos y logs. Registrar resultados reales y cualquier criterio pendiente.

## Evidencias de ejecución

Verificado el 2026-10-08. Las pruebas ejecutadas por el agente utilizaron credenciales y JWT ficticios; el agente no leyó `.env` ni llamó a cátedra real. El usuario realizó además la prueba real documentada abajo. No se hicieron commits ni push.

| Tarea / criterios | Comprobación ejecutada | Resultado |
| --- | --- | --- |
| T-01 · CA-04/05/11 | Sesiones completas/incompletas, acceso no habilitado, reemplazo de sesión y representaciones sin secretos en `IntegrationBehaviorTests`; revisión de modelos inmutables y `AtomicReference`. | Correcto. Sesión solo en memoria, sin JPA ni migraciones. |
| T-02 · CA-01/02/03/05/06/07 | Adaptador WebClient real contra `HttpServer`: login/body, GET sin envoltorio, bearer, campos compatibles, errores sin código, conexión cortada y timeout. `IntegrationPropertiesTests` comprueba faltantes, formatos y longitudes. | Correcto. Diagnósticos sin secretos; sin reintento oculto del cliente. |
| T-03 · CA-02/03/04/06/07/11 | Estados de aprovisionamiento, configuración parcial, `groupId` distinto, conservación del JWT durante `PENDING`, expiración exacta, ausencia/valor inválido de `exp` y 401 tras adquirir token. | Correcto. Solo sesión completa habilitada; no renovación automática. |
| T-04 · CA-01/07/09/10/11 | Ciclo real con espera controlada: fallo seguido de éxito, terminal, tres intentos agotados, segundo evento de arranque, nueva sesión tras reinicio e interrupción durante espera. Arranque Spring real mediante `CatalogApplicationTests`. | Correcto. Sin nuevo ciclo automático ni login de registro. |
| T-05 · CA-01 a CA-11 | `./mvnw -B -ntp verify`; arranque Spring con MySQL real y cátedra ficticia no disponible; health y consulta SQL. | BUILD SUCCESS: 56 pruebas, 0 fallos, 0 errores, 0 omitidas. |
| T-05 · CA-05/08 | `docker compose ... config --quiet` y `up --build -d`, proyecto aislado `prog2-catalog-integration-check`, MySQL 8.4.8 y fixture HTTP del JDK. | Imagen construida, login al arrancar y diagnóstico `attempt 1/3: READY`. Health HTTP 200 con solo `{"status":"UP"}`. Ruta interna de token: 404. Logs sin secretos ficticios. |
| T-05 · CA-08 | JAR local con MySQL de Docker y fixture HTTP publicada en localhost, variables externas y sin contraseña root en el backend. | Health UP e integración READY. Proceso local detenido tras comprobarlo. |

Los reportes se generan en `target/surefire-reports/`: 32 pruebas de comportamiento de integración, 11 de configuración y 13 de arranque/regresión. `target/` está ignorado por Git.

La prueba Compose utilizó puertos, red y volumen propios. Se retiraron únicamente sus contenedores, red y volumen; no se modificó el entorno del usuario.

**Detalle de implementación:** las variables `CATEDRA_*` se leen y validan directamente mediante `Environment`, antes de abrir conexiones. No fue necesario duplicarlas con alias en `application.yaml`. Los modelos y DTO usan records, vistos en clase; los tipos con secretos sobrescriben `toString`. El cliente desactiva explícitamente el reintento TCP implícito de Reactor Netty para respetar el límite acordado.

### Reorganización autorizada por el usuario

El 2026-10-08 se reubicaron nueve archivos para seguir la estructura del ejemplo del profesor: enums en `domain/enums`, excepciones según responsabilidad en `domain/exception` y `application/exception`, cliente en `client/adapter` y DTO en `client/dto`. Se actualizaron paquetes, imports de producción/pruebas y visibilidad de los DTO, sin cambios de comportamiento. El PLAN refleja las rutas actuales y AGENTS.md general registra el criterio compartido para ambos backends, sin imponerlo a KMP.

Verificación posterior: `./mvnw -B -ntp clean verify`, finalizado el 2026-10-08 a las 10:33:52 -03:00. BUILD SUCCESS: 56 pruebas, 0 fallos, 0 errores, 0 omitidas. La limpieza retiró clases compiladas de los paquetes anteriores. También se comprobaron correspondencia entre paquetes/rutas, referencias documentales y `git diff --check`. No se hicieron commits.

### Documentación del código autorizada por el usuario

El 2026-10-08 se agregó Javadoc en español a las 22 clases/interfaces/enums de producción del slice, con responsabilidades, contratos de puertos, significado de estados, efectos y errores relevantes. Las implementaciones aprovechan la herencia de los contratos y documentan sus decisiones propias. Se identifican Adapter y Repository en sus componentes; se explican recuperación, reemplazo atómico, timeouts, lectura de vencimiento y protección de secretos. La convención quedó registrada en AGENTS.md general y SESSION_HANDOFF.md; OpenAPI/Swagger sigue diferido a las APIs HTTP propias.

Validación: comparación del código anterior y posterior eliminando únicamente comentarios y espacios, conservando literales; ningún token ejecutable cambió. `./mvnw -o -B -ntp -DskipTests test-compile`: BUILD SUCCESS, 2026-10-08 20:29:48 -03:00. La herramienta `javadoc` del JDK generó las 22 páginas con `-private -Xdoclint:all,-missing`, sin errores ni advertencias; se comprobó su existencia en `target/site/apidocs/`. Las dependencias para resolver tipos se tomaron del JAR existente, sin añadir plugins ni dependencias al proyecto. `git diff --check` correcto. No se repitieron las pruebas de comportamiento por ser cambios documentales; no se hicieron commits.

### Prueba real aportada por el usuario

El 2026-10-08 el usuario ejecutó `docker compose up --build -d` con su configuración local y compartió logs sin secretos. MySQL quedó healthy, Tomcat inició y, a las 13:06:18 UTC, se registró `Technical integration attempt 1/3: READY`. Confirma login técnico y adquisición de configuración utilizable contra cátedra, según las validaciones de #6 (CA-02/04/08). No se inspeccionaron credenciales ni JWT.

**Pendiente fuera de #6:** verificación de conectividad Redis/Kafka y sincronización en sus funcionalidades. El endpoint protegido y el consumo desde Turnos siguen pendientes de #13 y Turnos #4. Los adaptadores futuros que usen el JWT deben propagar su rechazo 401 a la política de acceso no disponible; no se implementan en esta entrega.
