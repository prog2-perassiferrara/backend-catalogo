# PLAN: Simplificación de configuración y boilerplate

**SPEC de referencia:** [SPEC.md](SPEC.md), pedido explícito del 2026-11-03.
**Estado:** Registro de la solución autorizada; sin aprobación documental nueva inferida.

## Contexto técnico verificado

Java 25, Spring Boot 4.1.1 y Maven Wrapper. No había Lombok. Baseline: 139 fuentes Java productivas y 5.516 líneas físicas.

## Solución propuesta

Component scanning sustituye seis fábricas triviales, conservando sus nombres. Constructor injection y puertos permanecen. Lombok genera exclusivamente miembros equivalentes: RequiredArgsConstructor, Getter y Slf4j. Sin nuevas abstracciones.

## Módulos y componentes afectados

| Componentes | Cambio |
| --- | --- |
| SynchronizationConfiguration | Retirar fábricas de cuatro consultas, guard y política. |
| Cuatro consultas, CatalogStatusPolicy y CatalogSynchronizationGuard | Componentes con los mismos nombres de bean. |
| Consultas, casos de integración, controladores y JpaCatalogSnapshotAdapter | Trece constructores triviales sustituidos, sin cambiar colaboradores. |
| ProfessionalCategoryEntity, ProfessionalEntity y WeeklyScheduleEntity | Diecinueve getters de campos concretos; conservar getEnabled(), constructores y mapping. |
| JpaCatalogQueryAdapter y CatalogStorageFailureMapper | Slf4j conserva clase de logger, nivel, texto y condiciones de emisión. |
| pom.xml | Lombok provided/optional, procesador explícito y exclusión del JAR ejecutable. |

## Datos y contratos

Sin cambios en dominio, DTO, SQL, rutas, códigos ni JWT. Permanecen los GET internos de token, profesional/agenda y estado; el token técnico se consulta sin renovar por petición.

## Estado, operaciones y errores

Sin cambios en fases, runId, verificación, exclusión, commits ambiguos, recovery o idempotencia.

## Dependencias y configuración

Lombok usa la versión administrada por Spring Boot y procesamiento Maven explícito. No es dependencia de ejecución. Permanecen las fábricas con parámetros derivados, clientes externos, ejecutores, reloj y cierre de recursos.

CatalogOperationalProperties, IntegrationProperties y CatalogConfiguration se conservan: el binding genérico no elimina las reglas de URI, límites, diagnósticos seguros ni la validación anterior a conexiones. No se añade otro modelo de propiedades para sustituirlas.

## Estrategia de validación

- CA-01: clean verify antes/después; contextos, SecurityHttpTests, OpenApiHttpTests/OpenApiDisabledHttpTests y suites de sincronización existentes.
- CA-02: CatalogOperationalPropertiesTests, IntegrationPropertiesTests, CatalogApplicationTests y pruebas de arranque existentes.
- CA-03: firmas javap públicas/protegidas y comparación de cuerpos, JPA y clientes HTTP de Turnos. No se modifica ni comparte implementación entre repositorios.
- Comando: `./mvnw -B -ntp clean verify`, MySQL/Kafka temporales y proveedores ficticios. No acredita nuevos incrementos reales de cátedra.
- Revisar Sonar local disponible, diff y exclusión de Lombok del JAR.

## Orden de implementación

Baseline, configuración Maven, sustituciones puntuales, comprobaciones y suite final, registro de evidencia y commits autorizados sin push.

## Riesgos y decisiones pendientes

Riesgos: nombres de bean, firmas generadas y getters booleanos. Se mantienen nombres explícitos y getEnabled() manual, y se comparan firmas compiladas. Sin decisiones funcionales pendientes.

### Ajuste de validación autorizado posteriormente

El diagnóstico JDBC reprodujo timeouts en las fuentes originales y commits locales pendientes dentro de MySQL. Se extiende a ocho contenedores de pruebas el montaje tmpfs ya usado por dos suites. Solo cambia el almacenamiento efímero: mismo MySQL, configuración de durabilidad, límites, cuerpos y aserciones. No se prueban aquí reinicios del contenedor MySQL ni pérdida eléctrica del host; las recreaciones de contextos conservan la misma base en ejecución. La comparación aislada pasó las tres clases afectadas, 44 pruebas. CA-01 se cierra únicamente después de clean verify completo. Sin cambios productivos ni bloques adicionales.
