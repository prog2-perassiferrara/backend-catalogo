# PLAN: Inicializar backend de catálogo y sincronización

**SPEC de referencia:** [SPEC.md](SPEC.md)
**Versión de la spec revisada:** Aprobada por el usuario el 2026-10-06, con Spring Boot sin generador JHipster.
**Estado:** Aprobado <!-- Borrador | En revisión | Aprobado -->

<!-- PARA LA PERSONA
Copia esta plantilla como PLAN.md junto a la SPEC.md aprobada.
Este documento define la solución técnica. Una vez revisado, el agente puede
derivar TASKS.md con tareas, dependencias y comprobaciones.
-->

<!-- PARA EL AGENTE
- Lee la SPEC.md aprobada, las instrucciones del proyecto y MOBILE_GUIDELINES.md.
  Si falta un documento necesario o la spec no está aprobada, indícalo antes de avanzar.
- Inspecciona el repositorio. Referencia rutas verificadas y distingue las nuevas propuestas.
- Propón una solución proporcional al alcance y coherente con el proyecto.
  Reutiliza lo existente y justifica nuevas dependencias o cambios de arquitectura.
- Distingue hechos, decisiones confirmadas y propuestas. Consulta las decisiones
  no resueltas; haz pocas preguntas por vez y actualiza el plan con las respuestas.
- Referencia los requisitos y criterios por su ID, sin copiar toda la spec.
- Si una decisión cambia el comportamiento o alcance, vuelve a la spec y solicita
  confirmación. No resuelvas una duda de producto mediante una suposición técnica.
- Conserva estos comentarios. No implementes durante la planificación.
- Solicita aprobación antes de marcar el plan como Aprobado. La autorización
  para implementar debe ser explícita; no se deduce del estado de los documentos.
-->

## Contexto técnico verificado

<!-- Qué existe hoy y cómo participa en la funcionalidad. -->

| Componente o archivo existente | Ruta verificada | Responsabilidad y uso previsto |
| --- | --- | --- |
| Especificación aprobada | `SPEC.md` | Alcance y criterios CA-01 a CA-08. |
| Reglas del repositorio | `../../../AGENTS.md`, `../../GENERIC_RULES.md` | Restricciones de trabajo y planificación. |
| README | `../../../README.md` | Actualmente mínimo; documentar ejecución real. |
| Casos de uso | `../../use-cases/` | Referencia para funcionalidades posteriores; CU-01 no se implementa en el setup. |
| Material de clase y referencia de consulta | `../../../../toda_la_materia.md`, `../../../../MATERIA_REFERENCE.md` | Ejemplos y recomendaciones; no sustituyen enunciado ni anexo. |

Al aprobar este PLAN todavía no existían código, wrappers, build ni Docker Compose. La implementación y sus evidencias están registradas en TASKS.md.

**Convenciones y patrón de referencia:** skill `hexagonal-arch`, leída en `/home/valen/.agents/skills/hexagonal-arch/SKILL.md`. El usuario confirmó que es la skill renombrada desde `hexagonal-vertical-slicing`. Se utilizará la skill actual; no se requiere recuperar el directorio de ejemplo ausente. La revisión selectiva del material de clase refuerza el patrón por funcionalidad, la separación de representaciones y la creación de solo las partes necesarias. El POM didáctico usa Boot 4.0.6 y Java 25; no se presenta como versión obligatoria y se conserva Boot 4.1.1 acordado.

## Solución propuesta

<!-- Explica el enfoque y sus motivos. Describe las responsabilidades y el
recorrido de datos y eventos hasta la interfaz. Usa un diagrama si aporta claridad. -->

Crear la base de Java/Spring Boot sin utilizar el generador JHipster, con compilación reproducible, conexión a la base de datos propia, migraciones y ejecución mediante Docker Compose (RF-01 a RF-05).

La configuración obligatoria inválida o ausente y la base de datos inaccesible deben impedir completar el arranque, con diagnóstico sin secretos (RF-06).

Decisiones confirmadas: Maven y MySQL 8.4 LTS. La arquitectura seguirá la skill `hexagonal-arch`: organización por funcionalidad; dominio sin Spring/JPA/Jackson; aplicación dependiente del dominio; infraestructura con adaptadores de persistencia y web. Las interfaces de puertos emplearán tipos de dominio y los controladores futuros emplearán DTO. No se crearán slices, CRUD ni clases vacías en el setup.

Confirmadas también las versiones Maven 3.10.0 y MySQL 8.4.8, y las dos formas de ejecución: Compose completo o backend desde el IDE con MySQL en Docker. Los detalles que siguen desarrollan esas decisiones y fueron aprobados por el usuario junto con este PLAN.

## Módulos y componentes afectados

<!-- Si el proyecto está modularizado, identifica los módulos afectados, sus
responsabilidades y la dirección de sus dependencias. Respeta los límites
existentes y justifica cualquier módulo o dependencia nueva. Si no está
modularizado, describe las carpetas o componentes afectados sin introducir
modularización fuera del alcance; marca la tabla de módulos como No aplica. -->

<!-- Distingue lo que se reutiliza, modifica o crea. Las rutas nuevas son propuestas.
Señala impacto sobre modelos, contratos o componentes compartidos. -->

Confirmado por el usuario: un único módulo Maven y paquete base `com.prog2.catalog`. Los slices futuros seguirán `com.prog2.catalog.{slice}` con `domain`, `application` e `infrastructure`. Se omite el segmento `hexagonal` por indicación explícita del usuario, conservando las reglas de dependencias de la skill. No se generarán paquetes vacíos. La configuración se concreta en «Dependencias y configuración».

| Componente o ruta | Acción | Cambio y responsabilidad | Requisito relacionado |
| --- | --- | --- | --- |
| `README.md` (existente) | Modificar | Requisitos, configuración y comandos reproducibles. | RF-01, RF-04, RF-05 |
| `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/` (propuestos) | Crear | Maven y wrapper para compilación reproducible. | RF-01, RF-05 |
| `src/main/java/com/prog2/catalog/CatalogApplication.java` (propuesto) | Crear | Punto de arranque del backend. | RF-01 |
| `src/main/resources/` (propuesto) | Crear | Configuración externa e integración de Flyway. | RF-02, RF-04, RF-06 |
| `src/main/resources/db/migration/` (propuesto) | Crear cuando exista un cambio real de esquema | Migraciones SQL de almacenamiento propio; sin entidades de negocio ficticias. | RF-02 |
| `src/test/java/com/prog2/catalog/` y `src/test/resources/` (propuestos) | Crear | Pruebas de integración y recursos exclusivos de test. | RF-01, RF-02, RF-06 |
| `compose.yaml`, `Dockerfile`, `.dockerignore` (propuestos) | Crear | Backend Java 25, MySQL 8.4.8 y volumen propio. | RF-03 |
| `.env.example` (propuesto), `.gitignore` (existente) | Crear / modificar | Documentar variables y excluir `.env` y secretos locales. | RF-04 |

## Datos y contratos

<!-- Completa solo lo aplicable. Si un punto no aplica, indica el motivo. -->

- **Modelos y contratos de entrada y salida:** no se implementan CU-01 ni registro/autenticación. Confirmado: usar Spring Boot Actuator y exponer únicamente `/actuator/health`, sin detalles internos, para comprobar disponibilidad técnica y conexión a MySQL. Este resultado no informa vigencia ni sincronización del catálogo. La comprobación de persistencia de CA-03 se realizará sobre Compose, conservando su almacenamiento.
- **Identificadores, relaciones y restricciones:** no diseñar entidades de catálogo o usuarios en esta etapa.
- **Origen de los datos mostrados y transformaciones:** no aplica, no hay flujo de consultas de producto en el setup.
- **Persistencia, consultas y actualizaciones:** MySQL 8.4 LTS, Spring Data JPA/Hibernate y migraciones SQL propias gestionadas por Flyway (RF-02), confirmados por el usuario. Las entidades JPA y los repositorios Spring Data quedarán en infraestructura, separados de modelos y puertos del dominio. En el setup no se crearán entidades de negocio. La misma imagen `mysql:8.4.8` se utilizará en Compose y Testcontainers.
- **Convivencia entre datos locales y remotos:** no se incorpora sincronización central en este issue.
- **Compatibilidad y migraciones de datos existentes:** no existe persistencia previa en el repositorio; no introducir otra réplica de datos de Turnos.

## Estado, operaciones y errores

<!-- Cómo se implementan los comportamientos aprobados en la spec.
Referencia RF/CA y aplica las consideraciones relevantes de MOBILE_GUIDELINES.md. -->

- **Gestión del estado de interfaz y navegación:** no aplica a este backend.
- **Conservación y restauración del estado:** volumen nombrado propio `catalog-mysql-data` montado en `/var/lib/mysql`. Recrear contenedores sin borrar el volumen debe conservar datos; el README distinguirá detener/recrear de eliminar deliberadamente el almacenamiento (CA-03).
- **Ejecución, concurrencia y cancelación de operaciones:** no se implementan procesos de negocio en este setup.
- **Errores, reintentos y prevención de duplicados:** variables obligatorias sin credenciales por defecto; Compose rechazará valores ausentes mediante interpolación requerida y Spring fallará al resolver o validar configuración incompleta. Flyway conectará y migrará durante la inicialización: una conexión rechazada o migración fallida impedirá completar el arranque. Configurar esperas finitas de conexión y sin reintentos ilimitados; pruebas negativas acotadas verificarán el fallo. Los diagnósticos deben identificar el tipo de problema sin imprimir contraseñas ni configuración completa (CA-07 y CA-08).
- **Otras consideraciones mobile aplicables y su solución:** configuración externa y protección de secretos; sin UI ni ciclo de vida Android.

## Dependencias y configuración

<!-- Librerías, servicios, permisos o configuración afectados. Verifica compatibilidad
con el proyecto y justifica las incorporaciones. No agregues dependencias por defecto. -->

### Versiones y dependencias

- Confirmado: Java 25, Spring Boot 4.1.1, Maven 3.10.0 mediante wrapper y MySQL 8.4.8.
- Ajuste aprobado durante la implementación: Docker Hub rechazó `mysql:8.4.12` como imagen inexistente; el usuario confirmó usar `mysql:8.4.8`, cuya disponibilidad se verificó en el registro oficial.
- Un único módulo Maven; propuesta de coordenadas: `com.prog2:catalog`, empaquetado JAR. Usar `spring-boot-starter-parent:4.1.1` y compilación para Java 25.
- Dependencias de runtime y test con versiones gestionadas por Spring Boot, sin overrides innecesarios. Los starters siguientes son de `org.springframework.boot`.

| Dependencia propuesta | Finalidad | Requisitos |
| --- | --- | --- |
| `spring-boot-starter-webmvc` | Servidor HTTP para health y futuras APIs. | RF-01 |
| `spring-boot-starter-data-jpa` | JPA/Hibernate, conexión y repositorios futuros. | RF-02 |
| `com.mysql:mysql-connector-j` (runtime) | Driver JDBC de MySQL. | RF-02 |
| `spring-boot-starter-flyway` y `org.flywaydb:flyway-mysql` | Migraciones SQL propias. | RF-02, RF-06 |
| `spring-boot-starter-actuator` | Estado técnico de salud. | RF-01, RF-03 |
| `spring-boot-starter-test`, `spring-boot-starter-webmvc-test` (test) | Pruebas de arranque, integración y HTTP. | RF-01, RF-02, RF-06 |
| `org.testcontainers:testcontainers-mysql`, `org.testcontainers:testcontainers-junit-jupiter`, `spring-boot-testcontainers` (test) | MySQL aislado y conexión dinámica para pruebas. | RF-02 |

Artefactos contrastados con [dependencias gestionadas de Spring Boot 4.1.1](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html). El BOM gestiona, entre otras, Flyway 12.4.0, Connector/J 9.7.0 y Testcontainers 2.0.5. El build de implementación debe confirmar resolución y compatibilidad efectiva. No se incorporan Redis, Kafka, clientes de cátedra ni JWT en este setup. La ubicación de usuarios finales se decidirá en el issue de registro/autenticación; no condiciona estas dependencias.

### Configuración de aplicación y base

- Propuesta: `src/main/resources/application.yaml` para configuración común mediante variables externas. Definir URL JDBC, usuario y contraseña de la base como obligatorios, sin valores secretos de fallback. Los parámetros de conexión deben usar host/puerto/base separados y nunca incluir contraseñas en la URL.
- Usar `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false` y `spring.sql.init.mode=never`. Flyway será el único mecanismo de cambios de esquema; Hibernate validará las entidades cuando se incorporen. En este setup no existen entidades de negocio que validar.
- Flyway habilitado al arrancar, scripts en `classpath:db/migration`, sin migraciones de negocio ficticias. Los recursos de comprobación se ubicarán solo en tests. Una base vacía puede iniciar sin tablas de negocio; no representa catálogo vigente.
- Actuator: exponer solo `health`, `show-details=never`, `show-components=never`, probes y discovery deshabilitados para mostrar únicamente el estado global. Mantener la comprobación de base en el estado global. Esperar HTTP 200 con `status=UP` en el caso válido. No equivale a sincronización del catálogo.
- Propuesta de esperas de conexión: 5 segundos para establecer conexión JDBC y 10 segundos para adquirir conexión del pool, sin reintentos Flyway adicionales. Verificar que los ensayos de arranque fallido terminen dentro de 30 segundos; ajustar el mecanismo si una dependencia ignora esas esperas, sin ocultar el fallo.

### Ejecución local confirmada

- **Compose completo:** servicios `catalog` y `mysql`, red propia, MySQL 8.4.8 y volumen nombrado `catalog-mysql-data`. Imagen Java 25 con build Maven y ejecución del JAR; fijar tags disponibles y registrar los usados al implementar. El backend conectará al host interno `mysql` y esperará al healthcheck de la base antes de arrancar.
- **IDE o wrapper:** iniciar solo MySQL con Compose y ejecutar Java en la máquina. Propuesta de puertos locales configurables: backend 8080, MySQL 3306, vinculados a localhost para uso de desarrollo. La aplicación conectará a `localhost`, no al nombre de servicio Docker.
- `.env` fuera de Git y `.env.example` con nombres de variables y valores no secretos de ejemplo; las contraseñas quedarán vacías para completar localmente. Propuesta: `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `DB_HOST`, `DB_PORT`, `APP_PORT` y `MYSQL_ROOT_PASSWORD` para inicialización de MySQL. La cuenta del backend tendrá permisos únicamente sobre su base y no será root.
- Compose carga `.env` para interpolar su configuración. Spring Boot no carga ese archivo automáticamente: documentar cómo proporcionar las mismas variables al IDE o al proceso iniciado con Maven.
- `.dockerignore` excluirá `.env` y configuración sensible del contexto de build. No incluir credenciales de cátedra ni conectar con sus servicios en esta etapa.

Referencias: [requisitos de Spring Boot](https://docs.spring.io/spring-boot/system-requirements.html), [inicialización y Flyway](https://docs.spring.io/spring-boot/how-to/data-initialization.html), [Maven](https://maven.apache.org/download.cgi), [MySQL 8.4](https://dev.mysql.com/doc/relnotes/mysql/8.4/en/) y [Testcontainers MySQL](https://java.testcontainers.org/modules/databases/mysql/).

## Estrategia de validación

<!-- Una fila por criterio de la spec. Selecciona el método capaz de demostrarlo:
test unitario, integración, UI o prueba manual. No todos requieren todos los métodos.
Identifica tests existentes y separa los nuevos propuestos. Incluye regresiones relevantes.
Una captura aislada no demuestra persistencia ni ausencia de peticiones de red. -->

<!-- Esta sección planifica la validación. Durante la implementación, registra
en TASKS.md o en el informe de validación acordado los resultados y evidencias
reales. Distingue pruebas ejecutadas, fallidas, no ejecutadas y bloqueadas.
Compilar o tener tests en verde no sustituye revisar los criterios de la spec. -->

| Criterio | Método propuesto | Entorno y datos necesarios | Evidencia prevista |
| --- | --- | --- | --- |
| CA-01 | Compilar y arrancar siguiendo README; consultar `/actuator/health`. | Requisitos acordados y configuración válida. | Compilación correcta, arranque completo y health disponible. |
| CA-02 | Test de integración con Testcontainers, arranque real y health. Aplicar migración SQL exclusiva de test si no hay esquema de negocio aún. | MySQL temporal y recursos de test separados de producción. | Tabla y dato ficticio de test disponibles, migración registrada por Flyway y health correcto. Detecta Flyway deshabilitado o conexión mal configurada. |
| CA-03 | Verificación de Compose: crear tabla temporal de comprobación mediante SQL, confirmar un dato ficticio y recrear contenedores conservando el volumen; consultar el dato y limpiar solo el recurso de comprobación. | Base local propia y volumen de Compose; sin tablas de negocio. | Dato confirmado disponible tras recrear contenedores. Detecta almacenamiento no persistente o volumen mal configurado. |
| CA-04 | Levantar mediante Docker Compose y consultar health. | Docker y configuración documentada. | Backend y base disponibles; health sin detalles internos. |
| CA-05 | Revisar configuración y archivos versionados. | Repositorio y configuración externa ficticia. | Secretos externalizados y sin valores reales publicados. |
| CA-06 | Reproducir README. | Entorno documentado. | Comandos coinciden con los disponibles. |
| CA-07 | Pruebas de arranque con configuración obligatoria ausente o inválida. | Configuración aislada, credenciales ficticias. | Falla de arranque y diagnóstico sin valores secretos; detecta defaults que oculten errores. |
| CA-08 | Prueba de arranque contra una dirección de base inaccesible, con espera limitada. | Puerto inaccesible y credenciales ficticias. | Falla de arranque y diagnóstico sin secretos; detecta inicio aparentemente exitoso sin persistencia. |

**Comprobaciones de regresión:** no hay implementación previa ni tests existentes. Conservar documentación y requisitos ya revisados.

**Comandos implementados y verificados:** `./mvnw verify` (10 pruebas, sin fallos ni omisiones), `./mvnw spring-boot:run` (Java local con MySQL Docker), Compose completo con `up --build -d` y consulta de `/actuator/health`. La verificación Compose utilizó proyecto y configuración ficticia aislados; ver TASKS.md para comandos, recreación de contenedores y resultados. README documenta ambos modos de ejecución.

**Pruebas en dispositivo, emulador o simulador:** no aplican al setup backend.

**Entorno verificado:** Java Temurin 25.0.2, Docker 29.8.2 y Docker Compose v5.6.0; Maven 3.10.0 mediante wrapper. Se verificó acceso al daemon, resolución de dependencias, descarga de imágenes, Testcontainers y ambos modos de ejecución. Docker usa `eclipse-temurin:25.0.2_10-jdk-noble` para build y `eclipse-temurin:25.0.2_10-jre-noble` para runtime. No se verificó ejecución en Windows ni integración con cátedra, fuera de este setup.

## Orden de implementación

<!-- Etapas y dependencias principales. El desglose ejecutable se escribe en TASKS.md.
Incluye puntos de comprobación para avanzar con cambios pequeños. -->

1. Revisar y aprobar este PLAN; después derivar TASKS.md. La ejecución requiere autorización explícita.
2. Incorporar build, wrapper y base de Spring Boot; comprobar compilación y arranque.
3. Incorporar JPA, MySQL, Flyway y pruebas Testcontainers; verificar CA-02 y las pruebas de arranque negativo, sin crear tablas de negocio.
4. Configurar Compose, volumen, configuración externa y diagnósticos; verificar CA-03, CA-04, CA-05, CA-07 y CA-08.
5. Documentar y reproducir la ejecución; verificar CA-06.

La implementación requiere autorización explícita posterior. TASKS.md se deriva solo tras aprobar este PLAN.

## Riesgos y decisiones pendientes

<!-- Riesgos concretos de esta solución y cómo se resolverán, sin listas genéricas.
Escribe Ninguna en las decisiones pendientes cuando estén resueltas. -->

- **Riesgos y medidas:** dependencias e imágenes resueltas y verificadas con build e integración; confusión entre hosts Docker/IDE, documentar ambos; exposición de secretos en fallos de arranque, verificar con valores ficticios; pérdida de datos por volumen ausente, probar recreación de contenedores; conexión sin límite de espera, probar fallo con tiempo acotado.
- **Decisiones pendientes:** ninguna para el alcance de setup. Los detalles técnicos propuestos en este documento fueron aprobados con el PLAN; la implementación autorizada posteriormente se registra en TASKS.md. La identidad de usuarios y protección JWT de APIs de negocio corresponden al issue posterior de autenticación.
- No agregar funcionalidades de sincronización o autenticación para comprobar la base técnica.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el plan cubre los requisitos, respeta las exclusiones, reutiliza
componentes verificados y permite demostrar todos los criterios de aceptación.
Resuelve dudas y marcadores pendientes. Si la spec cambió, revisa su impacto.
Tras aprobar el plan, deriva TASKS.md con IDs, dependencias, referencias a RF/CA
y comprobaciones. No marques una tarea terminada sin realizar su validación;
si está bloqueada, registra el motivo.
-->
