# PLAN: OpenAPI y Swagger UI

**SPEC de referencia:** [SPEC.md](SPEC.md)
**Versión de la spec revisada:** aprobada por el usuario el 2026-10-30.
**Estado:** Aprobado <!-- Borrador | En revisión | Aprobado -->

**Aprobación del usuario:** 2026-10-30.

<!-- PARA LA PERSONA
Copia esta plantilla como PLAN.md junto a la SPEC.md aprobada.
Este documento define la solución técnica. Una vez revisado, el agente puede
derivar TASKS.md con tareas, dependencias y comprobaciones.
-->

<!-- PARA EL AGENTE
- Lee la SPEC.md aprobada, las instrucciones del proyecto y los contratos pertinentes.
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
- Para cambios de significado posteriores a la aprobación, registra una nota
  breve de cambio, motivo e impacto según AGENTS.md, sin historial separado.
-->

## Contexto técnico verificado

<!-- Qué existe hoy y cómo participa en la funcionalidad. -->

Rutas Java relativas a `src/main/java/com/prog2/catalog/`.

| Componente o archivo existente | Ruta verificada | Responsabilidad y uso previsto |
| --- | --- | --- |
| Plataforma | `pom.xml` | Java 25, Boot 4.1.1 y servidor MVC; WebFlux es cliente saliente. |
| Controladores y DTO | `synchronization/infrastructure/web/`, `integration/infrastructure/web/` | Seis GET; tipos y contratos que se documentan. |
| Seguridad y errores | `infrastructure/security/`, `synchronization/infrastructure/web/exception/` | JWT, perfiles y ProblemDetail con propiedad `code`. |
| Configuración | `src/main/resources/application.yaml`, `compose.yaml`, `.env.example` | Habilitación opcional por entorno. |

**Convenciones y patrón de referencia:** skill `hexagonal-arch`; documentación y
seguridad en infraestructura, sin modificar dominio, puertos o casos de uso.

## Solución propuesta

<!-- Explica el enfoque y sus motivos. Describe las responsabilidades y el
recorrido desde la solicitud, evento o arranque hasta el resultado del servicio.
Usa un diagrama si aporta claridad. -->

Agregar el starter MVC de springdoc. Generar el contrato desde los controladores
y DTO existentes, completando descripciones con anotaciones OpenAPI.
Una clase `OpenApiConfiguration` proporciona título, esquema bearer y modelo
común de error. Swagger UI usa ese contrato en el mismo servidor. Consultar la
documentación no ejecuta métodos de negocio; Try it out sí llama a la API real.

## Módulos y componentes afectados

<!-- Si el proyecto está modularizado, identifica los módulos afectados, sus
responsabilidades y la dirección de sus dependencias. Respeta los límites
existentes y justifica cualquier módulo o dependencia nueva. Si no está
modularizado, describe las carpetas o componentes afectados sin introducir
modularización fuera del alcance; marca la tabla de módulos como No aplica. -->

| Módulo | Existe / nuevo | Responsabilidad y cambios | Dependencias afectadas |
| --- | --- | --- | --- |
| No aplica: un proyecto Maven | Existente | Conservar estructura. | Un starter de documentación. |

<!-- Distingue lo que se reutiliza, modifica o crea. Las rutas nuevas son propuestas.
Señala impacto sobre modelos, contratos o componentes compartidos. -->

| Componente o ruta | Acción | Cambio y responsabilidad | Requisito relacionado |
| --- | --- | --- | --- |
| `infrastructure/config/OpenApiConfiguration.java` | Crear | Metadatos OpenAPI y esquema común de error. | RF-01, RF-02, RF-03 |
| Tres controladores y DTO HTTP actuales | Modificar | Anotaciones de operaciones, respuestas y campos no inferibles. | RF-01, RF-02, RF-03 |
| `infrastructure/security/config/SecurityConfiguration.java` | Modificar | Permitir solo rutas documentales GET cuando estén habilitadas. | RF-03, RF-04 |
| POM, YAML, Compose, ejemplos y README | Modificar | Starter, interruptor y utilización. | RF-04, RF-05 |

## Datos y contratos

<!-- Completa solo lo aplicable. Si un punto no aplica, indica el motivo. -->

- **Entrada/salida:** conservar los seis GET de SPEC. Añadir metadatos `@Operation`, `@ApiResponse`, `@Schema` y `@ParameterObject` para desplegar los seis filtros/parámetros de búsqueda individualmente.
- **Restricciones:** reflejar paginación 0/20, máximo 100, nombre normalizado hasta 100 y fecha ISO; describir agenda semanal sin prometer slots libres. No modificar validadores ni DTO serializados.
- **Esquemas:** diferenciar los dos `ProfessionalResponse` anidados como `CatalogProfessionalSummary` y `CatalogProfessionalDetails`; conservar sus nombres JSON. Horas locales `HH:mm:ss`, instantes UTC y estados según sus contratos actuales.
- **Errores:** componente documental `ApiProblem` con `type`, `title`, `status`, `code` e `instance` opcional. Declarar por operación sus respuestas/códigos reales; incluir 401/403 de los filtros. Desactivar incorporación automática de respuestas genéricas del advice para no inventar errores aplicables a otras operaciones.
- **Origen, persistencia y migraciones:** metadatos de clases; sin consultas centrales, escrituras o cambios de tablas. Ejemplos ficticios, sin tokens/configuración reales.

## Estado, operaciones y errores

<!-- Cómo se implementan los comportamientos aprobados en la spec.
Referencia RF/CA y completa solo los puntos pertinentes al backend. -->

RF-03/04: un esquema HTTP bearer `bearerAuth`, sin emisión de tokens. Describir
perfil de usuario y servicio/ROLE por operación; cambiar el bearer en la UI según
la prueba. Mantener filtros, sesiones, CORS y errores de negocio existentes.
La lectura anónima de documentación no autoriza APIs. No persistir autorización
en el navegador (`persistAuthorization=false`) ni incrustarla en ejemplos.
Con el interruptor apagado, no registrar el documento/UI ni permitir sus rutas;
no se promete 404, pues la denegación existente puede responder 401/403.
Reiniciar o recrear el contenedor para cambiar el interruptor.

## Dependencias y configuración

<!-- Librerías, servicios, permisos o configuración afectados. Verifica compatibilidad
con el proyecto y justifica las incorporaciones. No agregues dependencias por defecto. -->

- Propuesta: `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.0`, publicado en Maven Central. La [matriz oficial](https://springdoc.org/faq.html) admite Boot 4.1.x; su POM usa Boot 4.1.2. Conservar Boot 4.1.1 del proyecto y comprobar la combinación exacta al implementar.
- `catalog.api-docs.enabled: ${API_DOCS_ENABLED:false}` como única opción documentada. Referenciarla desde `springdoc.api-docs.enabled`, `springdoc.swagger-ui.enabled` y la configuración de seguridad; configuración documental condicional en la raíz de infraestructura.
- Mantener `springdoc.paths-to-match=/api/**`, `show-actuator=false`, `override-with-generic-response=false` y `disable-swagger-default-url=true`. Un documento, sin grupos adicionales ni especificación generada versionada. [Propiedades oficiales](https://springdoc.org/properties.html).
- Rutas: JSON `/v3/api-docs`, YAML `/v3/api-docs.yaml`, entrada `/swagger-ui.html`, recursos `/swagger-ui/**` y configuración `/v3/api-docs/swagger-config`. Permitir GET solo a esas rutas/recursos cuando el interruptor esté activo, antes de la denegación general; health permanece igual.
- Compose transmite `API_DOCS_ENABLED` opcional, sin valor predeterminado duplicado; `.env.example` muestra `# API_DOCS_ENABLED=true`.

## Estrategia de validación

<!-- Una fila por criterio de la spec. Selecciona el método capaz de demostrarlo:
test unitario, integración, contrato o prueba manual. No todos requieren todos los métodos.
Identifica tests existentes y separa los nuevos propuestos. Incluye regresiones relevantes.
Una captura aislada no demuestra persistencia ni ausencia de peticiones de red. -->

Proponer tests HTTP con generador springdoc, controladores, mappers y seguridad
reales; sustituir solo puertos de negocio y observar que cargar documentación no
los invoque. Reutilizar `JwtTestTokens`; evitar MySQL para comprobar metadatos.

| Criterio | Método y test existente o propuesto | Entorno y datos necesarios | Evidencia prevista |
| --- | --- | --- | --- |
| CA-01 | Nuevo `OpenApiHttpTests`: inspeccionar documento generado. | Contexto MVC y DTO reales. | Seis rutas, parámetros, límites, respuestas y esquemas profesionales distintos. |
| CA-02 | Contrato bearer, recursos UI y peticiones autenticadas; ensayo desde UI. | JWT ficticios de usuario/servicio; seguridad real. | Bearer enviado y permisos/errores conservados. |
| CA-03 | Contextos habilitado/deshabilitado; peticiones anónimas y otros métodos/rutas. | Mismo interruptor y cadena de filtros. | Solo documentación habilitada es pública; health sin cambios. |
| CA-04 | Inspección de esquemas/ejemplos y regresión de autorización técnica. | Sesión/token ficticios conocidos. | Sin valores secretos incrustados ni acceso de usuario al token técnico. |
| CA-05 | Repetir consultas de documento/UI con puertos observados. | Sin dependencia central funcional. | Cero llamadas de negocio durante lectura documental. |
| CA-06 | Seguir README y resolver Compose con fixture. | Configuración ficticia. | Habilitación, rutas y perfiles reproducibles. |

**Comprobaciones de regresión:** `SecurityHttpTests`, `CatalogSearchHttpTests`,
`CatalogQueryHttpTests` y suite completa; revisar Sonar CLI/IDE según disponibilidad.

**Comandos verificados para compilar y ejecutar tests:** `./mvnw -B -ntp test`
(foco con `-Dtest=<clases>`), seguido de `./mvnw -B -ntp clean verify`.

**Pruebas operativas del backend:** JAR aislado deshabilitado/habilitado, recursos
UI y bearer ficticio; Compose resuelto con ambos valores. Usar MySQL y REST
descartables, sin servicios/credenciales reales. Registrar prueba visual/funcional
de UI separadamente de la comprobación HTTP.

**Limitaciones del entorno:** compatibilidad declarada y artefacto verificados;
todavía sin pruebas de la feature o de la combinación exacta. No prueba integración
real con Turnos/KMP ni nuevos eventos centrales.

<!-- Esta sección planifica la validación. Durante la implementación, registra
en TASKS.md o en el informe de validación acordado los resultados y evidencias
reales. Usar el cierre de TASKS.md definido en AGENTS.md: CA/evidencia,
regresión y desviaciones del PLAN. Distingue pruebas ejecutadas, fallidas,
no ejecutadas y bloqueadas.
Compilar o tener tests en verde no sustituye revisar los criterios de la spec. -->

## Orden de implementación

<!-- Etapas y dependencias principales. El desglose ejecutable se escribe en TASKS.md.
Incluye puntos de comprobación para avanzar con cambios pequeños. -->

1. Starter, interruptor y acceso; comprobar habilitado/deshabilitado.
2. Metadatos y errores; comprobar contrato generado y JWT/permisos.
3. README, ensayo empaquetado y regresión; registrar evidencia y límites.

## Riesgos y decisiones pendientes

<!-- Riesgos concretos de esta solución y cómo se resolverán, sin listas genéricas.
Escribe Ninguna en las decisiones pendientes cuando estén resueltas. -->

- **Riesgos y medidas acordadas:** nombres de DTO repetidos y errores inferidos incorrectamente: nombres de esquema explícitos y tests del documento. Apertura excesiva de rutas: habilitación condicional y regresión de denegación por defecto.
- **Decisiones pendientes:** ninguna funcional; propuesta técnica completa para revisión.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el plan cubre los requisitos, respeta las exclusiones, reutiliza
componentes verificados y permite demostrar todos los criterios de aceptación.
Resuelve dudas y marcadores pendientes. Si la spec cambió, revisa su impacto.
Tras aprobar el plan, deriva TASKS.md con IDs, dependencias, referencias a RF/CA
y comprobaciones. Antes de implementar, realizar el chequeo de coherencia de
AGENTS.md con esos documentos, sin otro informe ni aprobación adicional.
No marques una tarea terminada sin realizar su validación;
si está bloqueada, registra el motivo.
-->
