# Contratos vigentes

Rutas relativas a Catálogo. Todos los endpoints de negocio son GET y requieren
`Authorization: Bearer <JWT propio de Turnos>`. Catálogo no registra usuarios ni
emite sus JWT. Las respuestas de negocio y errores se entregan sin caché.

## Autenticación y autorización

| Identidad | Claims y validación | Acceso |
| --- | --- | --- |
| Usuario final KMP | Firma RS256 de Turnos, `iss=backend-turnos`, audiencia que incluya `catalog`, `sub` no vacío, `token_use=user`, `auth` con `ROLE_USER`. | Consultas públicas de catálogo protegidas. |
| Servicio Turnos | Misma firma/emisor/audiencia; `sub=backend-turnos`, `token_use=service`, `auth` con `ROLE_CATALOG_INTEGRATION`. | Los tres GET internos publicados. |
| Cuenta técnica central | JWT adquirido por Catálogo ante cátedra, distinto de los JWT propios anteriores. | REST/configuración externa; nunca se entrega a KMP. |

`auth` es una cadena de roles separados por espacios. `iat` y `exp` son obligatorios;
se comprueban vigencia, emisión futura y tiempos coherentes, con tolerancia de 30 s.
Se valida `nbf` si existe; el JWT de servicio admite como máximo 300 s entre emisión
y vencimiento. Catálogo utiliza la clave pública externa de Turnos, sin consultar
su base. Sesiones HTTP, cookies y login de formulario no participan.

Falta de bearer o JWT inválido: 401 `AUTHENTICATION_REQUIRED`; identidad autenticada
sin permiso: 403 `ACCESS_DENIED`. Los usuarios no acceden a rutas internas aunque
posean roles adicionales. El resto de `/api/internal/**` está denegado. Los detalles
de claves, CORS y HTTPS están en [seguridad del README](../README.md#seguridad-jwt-y-entrega-a-turnos).

## HTTP entre Turnos y Catálogo

| Método y ruta | Respuesta 200 | Errores específicos |
| --- | --- | --- |
| `GET /api/internal/catedra/token` | `{"idToken":"<JWT central>"}`. DTO `TechnicalTokenResponse`. | 503 `TECHNICAL_INTEGRATION_UNAVAILABLE`. |
| `GET /api/internal/catalog/professionals/{professionalId}` | `ProfessionalAgendaResponse`: `professional`, `weeklySchedules`, `catalog`. | 400 `INVALID_PROFESSIONAL_ID`; 404 `PROFESSIONAL_NOT_FOUND`; 503 `CATALOG_NOT_CURRENT` o `CATALOG_UNAVAILABLE`. |
| `GET /api/internal/catalog/synchronization` | `CatalogStatusResponse`, incluso sin copia o con estado no vigente. | 503 `CATALOG_UNAVAILABLE`. |

El token se consulta de la sesión existente en memoria, sin login ni renovación
por petición. Si la sesión no es utilizable, se responde 503; la readquisición
requiere reiniciar Catálogo. [Ciclo técnico](../README.md#acceso-técnico-a-cátedra).

La consulta de profesional exige ID positivo representable como `long` y una copia
vigente. Ausencia solo significa 404 después de comprobar esa vigencia; deshabilitado
no equivale a inexistente. No consulta cátedra en cada GET.

### DTO de agenda y estado

| Objeto | Campos JSON |
| --- | --- |
| `professional` | `id`, `categoryId`, `firstName`, `lastName`, `enabled`. |
| Cada elemento de `weeklySchedules` | `id`, `professionalId`, `dayOfWeek`, `startTime`, `endTime`, `slotDurationMinutes`, `enabled`. |
| `catalog` o respuesta de estado interno | `state`, `copyAvailable`, `appliedVersion`, `observedVersion`, `lastCheckedAt`, `evaluatedAt`. |

Profesional, horarios y metadata pertenecen a una misma instantánea SQL. Se conservan
flags deshabilitados y se devuelve `[]` si no hay horarios. Estos se ordenan por día
de semana de lunes a domingo, hora inicial e ID. Las horas son locales `HH:mm:ss`;
las marcas de estado son instantes UTC. Versiones y última comprobación pueden ser
nulas cuando se desconocen.

`state` admite `NO_COPY`, `CURRENT`, `SYNCHRONIZING`, `RECOVERING`, `STALE`, `FAILED`
y `UNVERIFIABLE`. `copyAvailable` solo acredita copia confirmada, incluida una vacía.
`CURRENT` exige comprobación de esta ejecución, hasta 60 s inclusive, versiones
iguales y acceso técnico utilizable. Durante exclusividad la agenda devuelve 503;
el GET de estado puede informar `SYNCHRONIZING`. No se expone el diagnóstico privado
de sincronización. [Reglas y ejemplo JSON](../README.md#consultar-profesional-agenda-y-estado).

Turnos usa estos contratos por HTTP y su propio JWT de servicio; no comparte
entidades, repositorios ni credenciales SQL con Catálogo. Calcula disponibilidad
combinando agenda vigente y ocupaciones centrales. El hostname de Compose es
`catalog:8080` en `prog2-services`; [red y ejecución](../README.md#ejecutar-todo-con-compose).

## Consultas para KMP

| Método y ruta | Parámetros y respuesta |
| --- | --- |
| `GET /api/catalog/professionals` | Filtros opcionales `categoryId`, `name`, `enabled`, `availableOn`; `page=0`, `size=20` (1 a 100). Respuesta: `content`, `page`, `size`, `totalElements`, `totalPages`, `catalog`. |
| `GET /api/catalog/categories` | Sin filtros ni paginación. Respuesta: `content` y `catalog`. |
| `GET /api/catalog/synchronization` | Resumen: `state` y `copyAvailable`. |

Cada profesional resumido tiene `id`, `firstName`, `lastName`, `enabled` y `category`;
la categoría tiene `id`, `name`, `enabled`, también en la lista de categorías.
El campo `catalog` de ambas listas es el resumen sin versiones ni fechas internas.

Las búsquedas usan exclusivamente MySQL. `name` es un fragmento normalizado de
nombre/apellido, máximo 100 caracteres; se ignoran mayúsculas/tildes y se buscan
comodines literalmente. `categoryId` es positivo; uno inexistente devuelve cero
coincidencias. `availableOn` es fecha ISO y filtra agenda habilitada para su día de
semana, sin afirmar slots libres. Orden fijo: apellido, nombre e ID; categorías por
nombre e ID. Una copia anterior legible permite listas con estado no vigente; una
copia vacía confirmada devuelve listas vacías. No existe snapshot compartido entre
peticiones de páginas sucesivas. [Detalles de filtros](../README.md#buscar-profesionales-y-consultar-categorías).

Errores de listas: 400 `INVALID_SEARCH_CRITERIA`, 503 `CATALOG_COPY_UNAVAILABLE`
o `CATALOG_UNAVAILABLE`. El GET público de estado usa 503 `CATALOG_UNAVAILABLE`
si no puede leer metadata. También aplican 401/403 anteriores.

## Formato de errores y documentación ejecutable

Errores en `application/problem+json`, con `type`, `title`, `status` y `code`;
las consultas incluyen `instance` con la ruta sin query. No se copian excepciones,
credenciales o valores recibidos. Interpretar HTTP y `code`, no texto de diagnóstico.
Las consultas usan `Cache-Control: no-store`; token y errores de seguridad añaden
`Pragma: no-cache`.

`GET /actuator/health` es público y devuelve solo salud técnica. OpenAPI está
deshabilitado por defecto. Con `API_DOCS_ENABLED=true`, sus GET de documentación
son públicos, sin relajar permisos de negocio: `/v3/api-docs`, `/v3/api-docs.yaml`
y `/swagger-ui.html`. [Instrucciones y ejemplos](../README.md#openapi-y-swagger-ui).
Los DTO y las anotaciones del código alimentan ese contrato ejecutable.

## Contratos externos con cátedra

Fuente normativa externa: `INTEGRATION_REFERENCE-v2.md`, secciones 2 a 7, 13 a 16
y 18. El documento identifica el protocolo como v1; su nombre de archivo no cambia
la versión del protocolo. No se incluye una copia del anexo en este repositorio.

| Transporte | Uso de Catálogo |
| --- | --- |
| REST con JWT técnico | `POST /api/authenticate` devuelve JWT y configuración; `GET /api/student/integration` consulta la asignación; `GET /api/synchronization/snapshot` entrega `snapshotVersion`, `generatedAt`, `professionalCategories`, `professionals`, `weeklySchedules`. |
| Redis asignado | Bajo el namespace recibido: hash `metadata`, claves `current-version`, `oldest-available-version`, `changes:{version}` y hashes `professional-categories`, `professionals`, `weekly-schedules`. Cambios contienen IDs; hashes contienen valores actuales y bajas lógicas. No es la base de búsquedas por petición. |
| Kafka asignado | Topic `kafkaCatalogTopic` y grupo recibidos en `integration`; evento `CatalogUpdated` con `eventId`, `eventType`, `schemaVersion=1`, `newVersion`, `occurredAt`, `source`; key contractual `newVersion` como texto. Entrega al menos una vez, campos adicionales compatibles ignorados. |

El anexo define el registro manual de la cuenta técnica; Catálogo no lo automatiza.
Hosts, credenciales y nombres efectivos de Redis/Kafka proceden de la configuración
asignada, no de constantes de este documento. Por ejemplo, bajo `catedra:sync`,
la metadata se lee en `catedra:sync:metadata`. Catálogo no accede a holds ni a la
base de Turnos. La gestión REST/Kafka de reservas corresponde a Turnos.
Atomicidad, deduplicación, mensajes perdidos y recuperación:
[ARQUITECTURA](ARQUITECTURA.md#sincronización-idempotencia-y-recuperación).
Decisiones históricas: [integración](features/integracion-tecnica/PLAN.md),
[incrementos](features/sincronizacion-incremental/PLAN.md) y
[consultas internas](features/consulta-agenda-estado/PLAN.md).
