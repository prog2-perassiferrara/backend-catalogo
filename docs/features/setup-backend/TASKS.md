# TASKS: Inicializar backend de catálogo y sincronización

**Referencias:** [SPEC aprobada](SPEC.md), [PLAN aprobado](PLAN.md).
**Estado:** Pendiente de implementación.
**Aprobación del PLAN:** confirmada por el usuario el 2026-10-06.

La aprobación de los documentos no autoriza por sí sola a implementar. No se han ejecutado las tareas, builds ni pruebas descritas aquí. No incluye registro/autenticación ni CU-01.

## Tareas y dependencias

- [ ] **T-01 · Verificar herramientas y versiones disponibles**
  - **Objetivo:** confirmar que se pueden obtener y ejecutar las versiones acordadas antes de generar la base.
  - **Alcance:** Java 25, Maven 3.10.0, Spring Boot 4.1.1, imagen `mysql:8.4.12`, Docker/Compose y acceso al daemon; identificar imágenes Java 25 disponibles para build y runtime. Registrar tags concretos utilizados.
  - **Dependencias:** ninguna.
  - **Referencias:** RF-01, RF-03, RF-05; CA-01, CA-04, CA-06.
  - **Validación:** comprobar disponibilidad de versiones e imágenes y acceso al runtime. Las versiones de CLI ya observadas en el PLAN no demuestran acceso al daemon. Si una versión es inaccesible o incompatible, informar y consultar antes de sustituirla.

- [ ] **T-02 · Incorporar build y punto de arranque**
  - **Objetivo:** disponer de un módulo Maven compilable para el backend.
  - **Alcance:** `pom.xml`, wrapper Maven 3.10.0, Java 25, parent Spring Boot 4.1.1, paquete `com.prog2.catalog`, `CatalogApplication` y servidor Spring MVC. Incorporar dependencias cuando las tareas que las requieren configuren su uso; no generar slices vacíos ni CRUD.
  - **Dependencias:** T-01.
  - **Referencias:** RF-01, RF-05; CA-01, CA-06.
  - **Validación:** compilar mediante el wrapper y comprobar que resuelve las dependencias del build. El arranque con persistencia se verifica tras T-04.

- [ ] **T-03 · Configurar persistencia y migraciones**
  - **Objetivo:** integrar JPA/Hibernate, MySQL y Flyway manteniendo la propiedad de datos del servicio.
  - **Alcance:** dependencias acordadas, driver, módulo Flyway MySQL, `ddl-auto=validate`, `open-in-view=false`, inicialización SQL básica deshabilitada y Flyway habilitado. Usar versiones gestionadas por Spring Boot. No crear entidades ni migraciones ficticias de negocio.
  - **Dependencias:** T-02.
  - **Referencias:** RF-02, RF-06; CA-02, CA-07, CA-08.
  - **Validación:** comprobar resolución de artefactos y configuración. El comportamiento se verifica con las pruebas de T-05 y el arranque de T-07.

- [ ] **T-04 · Configurar variables externas y health**
  - **Objetivo:** ofrecer comprobación técnica de disponibilidad y fallar claramente ante configuración incompleta o persistencia inaccesible.
  - **Alcance:** `application.yaml`, variables obligatorias de conexión sin secretos por defecto, Actuator limitado a `/actuator/health` sin detalles ni componentes, esperas finitas y `.env.example` sin contraseñas. Ajustes mínimos a `.gitignore` para excluir `.env`. Conservar trabajo previo del usuario.
  - **Dependencias:** T-03.
  - **Referencias:** RF-01, RF-04, RF-06; CA-01, CA-05, CA-07, CA-08.
  - **Validación:** revisar configuración e ignorados; verificar resultados HTTP y fallos de arranque con T-05. Health debe incluir conexión a la base sin afirmar vigencia del catálogo.

- [ ] **T-05 · Implementar pruebas de integración del setup**
  - **Objetivo:** detectar fallos reales de migración, conexión, configuración y arranque.
  - **Alcance:** Testcontainers con MySQL 8.4.12, pruebas de arranque real y HTTP; migración SQL y datos ficticios exclusivos de test. Verificar Flyway aplicado, health correcto sin detalles, y fallo con configuración ausente/inválida o base inaccesible. Comprobar que los diagnósticos no contienen los secretos ficticios utilizados. No crear funcionalidad de producto para probar el setup.
  - **Dependencias:** T-03, T-04.
  - **Referencias:** RF-01, RF-02, RF-04, RF-06; CA-01, CA-02, CA-05, CA-07, CA-08.
  - **Validación:** ejecutar las pruebas mediante el wrapper, confirmar que se descubren y no se omiten. Los ensayos negativos deben terminar dentro de la espera prevista en el PLAN. Registrar fallos y bloqueos; no reemplazar MySQL por una base en memoria para declarar cumplimiento.

- [ ] **T-06 · Incorporar Docker Compose y almacenamiento persistente**
  - **Objetivo:** disponer de ejecución completa en contenedores y de MySQL para ejecutar Java desde el IDE.
  - **Alcance:** `Dockerfile`, `compose.yaml`, `.dockerignore`, servicios `catalog`/`mysql`, healthcheck de base, red propia, usuario limitado a su base y volumen `catalog-mysql-data` en `/var/lib/mysql`. `.env` local sin versionar, interpolación de variables obligatorias y puertos configurables vinculados a localhost.
  - **Dependencias:** T-01, T-02, T-04.
  - **Referencias:** RF-02, RF-03, RF-04, RF-06; CA-03, CA-04, CA-05, CA-07.
  - **Validación:** validar Compose con variables ficticias suministradas externamente, sin publicar configuración que contenga secretos; comprobar que los secretos no ingresan al contexto de build. Ejecutar ambos modos en T-07.

- [ ] **T-07 · Verificar ejecución, reinicios y criterios restantes**
  - **Objetivo:** demostrar los criterios que dependen del entorno real de ejecución.
  - **Alcance:** levantar Compose completo; ejecutar backend local con MySQL en Docker; consultar health; persistir un dato ficticio en una tabla exclusiva de comprobación y recrear contenedores conservando volumen. Limpiar únicamente el recurso de comprobación creado por esta tarea, sin eliminar almacenamiento del usuario.
  - **Dependencias:** T-05, T-06.
  - **Referencias:** RF-01, RF-02, RF-03, RF-04, RF-06; CA-01, CA-02, CA-03, CA-04, CA-05, CA-07, CA-08.
  - **Validación:** comprobar disponibilidad en ambos modos, dato confirmado conservado tras recreación, health sin información interna y diagnóstico de fallos sin secretos. Ejecutar `./mvnw verify`. Registrar comandos, resultados y criterios no demostrados si hay bloqueos.

- [ ] **T-08 · Documentar y reproducir la ejecución**
  - **Objetivo:** entregar instrucciones que coincidan con la base implementada.
  - **Alcance:** README con requisitos, variables, carga de configuración en IDE/proceso Java, Compose completo, modo IDE, health, migraciones y comandos reales de build/tests. Explicar preservación del volumen y diferencia entre disponibilidad técnica y sincronización. Completar las evidencias de este documento.
  - **Dependencias:** T-07.
  - **Referencias:** RF-01, RF-04, RF-05; CA-01, CA-05, CA-06.
  - **Validación:** reproducir los pasos documentados, comprobar referencias y comparar comandos con archivos incorporados. Registrar resultados de CA-06; no documentar pruebas como exitosas si no se ejecutaron.

## Evidencias de ejecución

Completar al implementar. Todas las tareas y criterios están pendientes.

| Tarea / criterio | Comando o procedimiento ejecutado | Resultado | Evidencia o bloqueo |
| --- | --- | --- | --- |
| Ninguno ejecutado | No aplica | Pendiente | Documentación de planificación únicamente. |
