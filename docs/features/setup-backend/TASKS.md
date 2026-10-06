# TASKS: Inicializar backend de catálogo y sincronización

**Referencias:** [SPEC aprobada](SPEC.md), [PLAN aprobado](PLAN.md).
**Estado:** Implementado y verificado.
**Aprobación del PLAN:** confirmada por el usuario el 2026-10-06.

El usuario autorizó explícitamente implementar el setup el 2026-10-06. Se completaron las tareas y verificaciones registradas abajo. No incluye registro/autenticación ni CU-01; no se hicieron commits ni push.

## Tareas y dependencias

- [x] **T-01 · Verificar herramientas y versiones disponibles**
  - **Objetivo:** confirmar que se pueden obtener y ejecutar las versiones acordadas antes de generar la base.
  - **Alcance:** Java 25, Maven 3.10.0, Spring Boot 4.1.1, imagen `mysql:8.4.8`, Docker/Compose y acceso al daemon; identificar imágenes Java 25 disponibles para build y runtime. Registrar tags concretos utilizados.
  - **Dependencias:** ninguna.
  - **Referencias:** RF-01, RF-03, RF-05; CA-01, CA-04, CA-06.
  - **Validación:** comprobar disponibilidad de versiones e imágenes y acceso al runtime. Las versiones de CLI ya observadas en el PLAN no demuestran acceso al daemon. Si una versión es inaccesible o incompatible, informar y consultar antes de sustituirla.

- [x] **T-02 · Incorporar build y punto de arranque**
  - **Objetivo:** disponer de un módulo Maven compilable para el backend.
  - **Alcance:** `pom.xml`, wrapper Maven 3.10.0, Java 25, parent Spring Boot 4.1.1, paquete `com.prog2.catalog`, `CatalogApplication` y servidor Spring MVC. Incorporar dependencias cuando las tareas que las requieren configuren su uso; no generar slices vacíos ni CRUD.
  - **Dependencias:** T-01.
  - **Referencias:** RF-01, RF-05; CA-01, CA-06.
  - **Validación:** compilar mediante el wrapper y comprobar que resuelve las dependencias del build. El arranque con persistencia se verifica tras T-04.

- [x] **T-03 · Configurar persistencia y migraciones**
  - **Objetivo:** integrar JPA/Hibernate, MySQL y Flyway manteniendo la propiedad de datos del servicio.
  - **Alcance:** dependencias acordadas, driver, módulo Flyway MySQL, `ddl-auto=validate`, `open-in-view=false`, inicialización SQL básica deshabilitada y Flyway habilitado. Usar versiones gestionadas por Spring Boot. No crear entidades ni migraciones ficticias de negocio.
  - **Dependencias:** T-02.
  - **Referencias:** RF-02, RF-06; CA-02, CA-07, CA-08.
  - **Validación:** comprobar resolución de artefactos y configuración. El comportamiento se verifica con las pruebas de T-05 y el arranque de T-07.

- [x] **T-04 · Configurar variables externas y health**
  - **Objetivo:** ofrecer comprobación técnica de disponibilidad y fallar claramente ante configuración incompleta o persistencia inaccesible.
  - **Alcance:** `application.yaml`, variables obligatorias de conexión sin secretos por defecto, Actuator limitado a `/actuator/health` sin detalles ni componentes, esperas finitas y `.env.example` sin contraseñas. Ajustes mínimos a `.gitignore` para excluir `.env`. Conservar trabajo previo del usuario.
  - **Dependencias:** T-03.
  - **Referencias:** RF-01, RF-04, RF-06; CA-01, CA-05, CA-07, CA-08.
  - **Validación:** revisar configuración e ignorados; verificar resultados HTTP y fallos de arranque con T-05. Health debe incluir conexión a la base sin afirmar vigencia del catálogo.

- [x] **T-05 · Implementar pruebas de integración del setup**
  - **Objetivo:** detectar fallos reales de migración, conexión, configuración y arranque.
  - **Alcance:** Testcontainers con MySQL 8.4.8, pruebas de arranque real y HTTP; migración SQL y datos ficticios exclusivos de test. Verificar Flyway aplicado, health correcto sin detalles, y fallo con configuración ausente/inválida o base inaccesible. Comprobar que los diagnósticos no contienen los secretos ficticios utilizados. No crear funcionalidad de producto para probar el setup.
  - **Dependencias:** T-03, T-04.
  - **Referencias:** RF-01, RF-02, RF-04, RF-06; CA-01, CA-02, CA-05, CA-07, CA-08.
  - **Validación:** ejecutar las pruebas mediante el wrapper, confirmar que se descubren y no se omiten. Los ensayos negativos deben terminar dentro de la espera prevista en el PLAN. Registrar fallos y bloqueos; no reemplazar MySQL por una base en memoria para declarar cumplimiento.

- [x] **T-06 · Incorporar Docker Compose y almacenamiento persistente**
  - **Objetivo:** disponer de ejecución completa en contenedores y de MySQL para ejecutar Java desde el IDE.
  - **Alcance:** `Dockerfile`, `compose.yaml`, `.dockerignore`, servicios `catalog`/`mysql`, healthcheck de base, red propia, usuario limitado a su base y volumen `catalog-mysql-data` en `/var/lib/mysql`. `.env` local sin versionar, interpolación de variables obligatorias y puertos configurables vinculados a localhost.
  - **Dependencias:** T-01, T-02, T-04.
  - **Referencias:** RF-02, RF-03, RF-04, RF-06; CA-03, CA-04, CA-05, CA-07.
  - **Validación:** validar Compose con variables ficticias suministradas externamente, sin publicar configuración que contenga secretos; comprobar que los secretos no ingresan al contexto de build. Ejecutar ambos modos en T-07.

- [x] **T-07 · Verificar ejecución, reinicios y criterios restantes**
  - **Objetivo:** demostrar los criterios que dependen del entorno real de ejecución.
  - **Alcance:** levantar Compose completo; ejecutar backend local con MySQL en Docker; consultar health; persistir un dato ficticio en una tabla exclusiva de comprobación y recrear contenedores conservando volumen. Limpiar únicamente el recurso de comprobación creado por esta tarea, sin eliminar almacenamiento del usuario.
  - **Dependencias:** T-05, T-06.
  - **Referencias:** RF-01, RF-02, RF-03, RF-04, RF-06; CA-01, CA-02, CA-03, CA-04, CA-05, CA-07, CA-08.
  - **Validación:** comprobar disponibilidad en ambos modos, dato confirmado conservado tras recreación, health sin información interna y diagnóstico de fallos sin secretos. Ejecutar `./mvnw verify`. Registrar comandos, resultados y criterios no demostrados si hay bloqueos.

- [x] **T-08 · Documentar y reproducir la ejecución**
  - **Objetivo:** entregar instrucciones que coincidan con la base implementada.
  - **Alcance:** README con requisitos, variables, carga de configuración en IDE/proceso Java, Compose completo, modo IDE, health, migraciones y comandos reales de build/tests. Explicar preservación del volumen y diferencia entre disponibilidad técnica y sincronización. Completar las evidencias de este documento.
  - **Dependencias:** T-07.
  - **Referencias:** RF-01, RF-04, RF-05; CA-01, CA-05, CA-06.
  - **Validación:** reproducir los pasos documentados, comprobar referencias y comparar comandos con archivos incorporados. Registrar resultados de CA-06; no documentar pruebas como exitosas si no se ejecutaron.

## Evidencias de ejecución

Verificado el 2026-10-06 en la rama del issue #4. Las credenciales utilizadas fueron ficticias, se suministraron mediante un archivo de permisos 0600 en `/tmp` y no se agregaron al repositorio.

### Ajuste de versión aprobado

`docker pull mysql:8.4.12` respondió `not found`. Se comprobó `mysql:8.4.8` y el usuario aprobó sustituir el parche en PLAN, Compose y Testcontainers. No se cambió de rama LTS ni de motor.

### Resultados

| Tarea / criterio | Comando o procedimiento ejecutado | Resultado | Evidencia |
| --- | --- | --- | --- |
| T-01 | Verificar Java, Docker/Compose, descargar Maven y las imágenes. | Correcto | Java 25.0.2, Maven 3.10.0, Docker 29.8.2, Compose v5.6.0, MySQL 8.4.8 y ambas imágenes Temurin disponibles. |
| T-02 · CA-01 | Generar wrapper con el plugin oficial 3.3.4; `./mvnw -B -ntp verify`. | Correcto | Compilación Java 25 y empaquetado Spring Boot 4.1.1. |
| T-03/T-05 · CA-02 | Testcontainers MySQL real; ejecutar migración SQL de test y consultar sus efectos e historial Flyway. | Correcto | Tabla y dato ficticio presentes, versión 1 aplicada una vez. |
| T-04/T-05 · CA-07 | Arranques con DB_NAME, DB_USER y DB_PASSWORD ausentes o en blanco; puerto inválido. | Correcto | Arranque rechazado y diagnósticos sin contraseña ficticia. |
| T-04/T-05 · CA-08 | Arranque contra puerto sin MySQL, con timeout de prueba de 30 segundos. | Correcto | Fallo de conexión identificado; contraseña ficticia ausente de logs capturados. |
| T-04/T-05 · CA-01/CA-05 | GET health, env y discovery. | Correcto | Health devuelve HTTP 200 y solo `{"status":"UP"}`; `/actuator/env` y `/actuator` devuelven 404. |
| T-06/T-07 · CA-04 | `docker compose --env-file /tmp/prog2-catalog-setup.env -p prog2-catalog-setup-check up --build -d`; consultar health. | Correcto | Imagen construida, backend y base disponibles. |
| T-06/T-07 · CA-03 | Crear `setup_volume_probe` y confirmar un dato ficticio mediante SQL; `up -d --force-recreate`; volver a consultarlo. | Correcto | Dato conservado tras recrear ambos contenedores con el volumen existente. Se eliminó exclusivamente la tabla de comprobación creada. |
| T-07 · CA-02/CA-05 | Consultar VERSION(), permisos del usuario de aplicación y ausencia de `setup_probe` en la base de Compose. | Correcto | MySQL 8.4.8, permisos sobre la base propia y ninguna tabla de migraciones exclusiva de test en producción. |
| T-07/T-08 · CA-06 | Detener solo el backend de Compose; ejecutar `./mvnw -B -ntp spring-boot:run` con variables externas, sin MYSQL_ROOT_PASSWORD. | Correcto | Java local conecta a MySQL Docker; health UP sin detalles. Proceso de comprobación detenido al terminar. |
| T-08 · CA-05/CA-06 | Revisar README, `.gitignore`, `.dockerignore`, rutas y JAR empaquetado. | Correcto | Configuración externa documentada, `.env` excluido; JAR contiene arranque/configuración y no incluye clases ni SQL de test. |

`./mvnw verify`: **10 pruebas ejecutadas, 0 fallos, 0 errores, 0 omitidas**.
Los reportes reproducibles se generan en `target/surefire-reports/`; `target/` está ignorado por Git.

Se corrigieron dos problemas encontrados al verificar: la respuesta de health exponía grupos de probes y una aserción esperaba un encabezado de diagnóstico diferente del emitido por Spring. La configuración final oculta probes/discovery y la prueba de conexión verifica el diagnóstico real de fallo de comunicaciones.

La comprobación Compose se ejecutó con un proyecto separado del entorno de desarrollo para no modificar datos del usuario. Se retiraron los recursos de esa comprobación al terminar. No se probó Windows ni integración central; no se requiere cátedra para ejecutar este setup.
