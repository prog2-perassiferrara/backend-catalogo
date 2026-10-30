# PLAN: Parametrizar la integración y sincronización

**SPEC de referencia:** [SPEC.md](SPEC.md)
**Versión de la spec revisada:** aprobada por el usuario el 2026-10-29.
**Estado:** Aprobado <!-- Borrador | En revisión | Aprobado -->

**Aprobación del usuario:** 2026-10-29.

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
| Configuración local | `infrastructure/config/IntegrationProperties.java` y `CatalogConfiguration.java` | Reutilizar lectura desde Environment y validación temprana. |
| Arranque y seguimiento | `infrastructure/startup/` | Intentos técnicos e intervalo periódico actualmente fijos. |
| Sincronización | `synchronization/application/usecases/` | Presupuestos y pausas de snapshot, incrementos y recuperación. |
| Clientes y composición | `infrastructure/config/CatalogRemoteClients.java`, `integration/infrastructure/config/`, `synchronization/infrastructure/config/` | Conectar parámetros con HTTP, Redis y Kafka. |

**Convenciones y patrón de referencia:** skill `hexagonal-arch`; configuración compartida fuera de slices, aplicación con JDK/dominio y dependencias por constructor. Se conserva el diseño existente.

## Solución propuesta

<!-- Explica el enfoque y sus motivos. Describe las responsabilidades y el
recorrido desde la solicitud, evento o arranque hasta el resultado del servicio.
Usa un diagrama si aporta claridad. -->

Crear `infrastructure/config/CatalogOperationalProperties.java`, con grupos
inmutables pequeños. Usar `from(Environment)` y validación temprana en el
postprocesador existente. El cableado `@Bean` entrega `int`/`Duration` por
constructor; aplicación no depende de esa clase ni de Spring. Sin nuevos puertos
o slices.

## Módulos y componentes afectados

<!-- Si el proyecto está modularizado, identifica los módulos afectados, sus
responsabilidades y la dirección de sus dependencias. Respeta los límites
existentes y justifica cualquier módulo o dependencia nueva. Si no está
modularizado, describe las carpetas o componentes afectados sin introducir
modularización fuera del alcance; marca la tabla de módulos como No aplica. -->

| Módulo | Existe / nuevo | Responsabilidad y cambios | Dependencias afectadas |
| --- | --- | --- | --- |
| No aplica: un proyecto Maven | Existente | Mantener módulos y dependencias actuales. | Ninguna nueva. |

<!-- Distingue lo que se reutiliza, modifica o crea. Las rutas nuevas son propuestas.
Señala impacto sobre modelos, contratos o componentes compartidos. -->

| Componente o ruta | Acción | Cambio y responsabilidad | Requisito relacionado |
| --- | --- | --- | --- |
| `infrastructure/config/CatalogOperationalProperties.java` | Crear | Lectura y validación segura. | RF-01, RF-02, RF-03 |
| Configuración raíz y de ambos slices | Modificar | Cablear valores. | RF-01, RF-02 |
| Runner, monitor y tres casos de sincronización | Modificar | Sustituir constantes; conservar algoritmos. | RF-01, RF-04 |
| Cliente HTTP técnico y `CatalogRemoteClients` | Modificar | Recibir timeouts; snapshot/Redis ya los aceptan. | RF-01, RF-04 |
| YAML, Compose, `.env.example`, README | Modificar | Valores predeterminados, entorno e instrucciones. | RF-02, RF-05 |

## Datos y contratos

<!-- Completa solo lo aplicable. Si un punto no aplica, indica el motivo. -->

- **Modelos y contratos:** HTTP, JWT y modelos de dominio permanecen iguales.
- **Restricciones:** enteros de 1 a `Integer.MAX_VALUE`; milisegundos convertidos a `Duration`. Rechazar blanco, texto, fracciones, cero, negativos y overflow, también al consumir contadores.
- **Origen:** valores efectivos de Environment sobre los predeterminados del YAML.
- **Persistencia, datos locales/remotos y migraciones:** sin cambios; tampoco guardar configuración operativa en MySQL ni reemplazar datos dinámicos de cátedra.

## Estado, operaciones y errores

<!-- Cómo se implementan los comportamientos aprobados en la spec.
Referencia RF/CA y completa solo los puntos pertinentes al backend. -->

RF-03, RF-04: conservar bloqueos, candidato/runId, reconciliación, ACK tras commit
e interrupciones. Presupuestos incluyen el primer intento; fallos terminales no
se reintentan. Valores inmutables por ejecución. Un error de creación identifica
solo la propiedad inválida, sin valor ni causa de conversión que lo reproduzca.

## Dependencias y configuración

<!-- Librerías, servicios, permisos o configuración afectados. Verifica compatibilidad
con el proyecto y justifica las incorporaciones. No agregues dependencias por defecto. -->

Sin dependencias nuevas. Claves propuestas: `catalog.operational.<grupo>.<clave>`;
variables explícitas `CATALOG_<GRUPO>_<CLAVE>` en mayúsculas, con `_` por `-`.
Todos los tiempos usan sufijo `-ms`. Inventario y valores actuales:

| Grupo | Claves y valores predeterminados |
| --- | --- |
| `integration` | `max-attempts=3`, `retry-delay-ms=5000`, `connect-timeout-ms=5000`, `read-timeout-ms=5000`, `request-timeout-ms=10000` |
| `snapshot` | `max-attempts=3`, `retry-delay-ms=5000`, `connect-timeout-ms=5000`, `read-timeout-ms=10000`, `request-timeout-ms=20000` |
| `incremental` | `max-attempts=3`, `retry-delay-ms=5000`, `retry-cooldown-ms=30000` |
| `recovery` | `max-cycles=3`, `cycle-delay-ms=30000`, `block-delay-ms=300000` |
| `redis` | `connect-timeout-ms=5000`, `command-timeout-ms=5000`, `read-timeout-ms=20000` (límite global del lote) |
| `kafka` | `timeout-ms=10000`, común a request, API y commit síncrono, hoy iguales. |
| `monitor` | `interval-ms=30000` (demora tras completar cada comprobación). |

HTTP técnico recibe tiempos independientes, conservando 5/5/10 s. La espera por
notificación no cubierta también usa `incremental.retry-delay-ms`. Parámetros
internos restantes de Kafka, apagado, MySQL, bytes, vigencia y JWT quedan fijos.

Ejemplo YAML: `read-timeout-ms: ${CATALOG_SNAPSHOT_READ_TIMEOUT_MS:10000}`.
Compose declara opcionales sin valor (`CATALOG_SNAPSHOT_READ_TIMEOUT_MS:`):
ausente se omite; definida se transmite, incluso vacía para rechazarla. No duplica
defaults; comprobado con fixture y [documentado por Docker](https://docs.docker.com/reference/compose-file/services/#environment).
`.env.example` contiene ejemplos comentados; JAR/IDE usa variables exportadas.
Aplicar cambios requiere reiniciar el proceso o recrear el contenedor, sin rebuild.

## Estrategia de validación

<!-- Una fila por criterio de la spec. Selecciona el método capaz de demostrarlo:
test unitario, integración, contrato o prueba manual. No todos requieren todos los métodos.
Identifica tests existentes y separa los nuevos propuestos. Incluye regresiones relevantes.
Una captura aislada no demuestra persistencia ni ausencia de peticiones de red. -->

| Criterio | Método y test existente o propuesto | Entorno y datos necesarios | Evidencia prevista |
| --- | --- | --- | --- |
| CA-01 | Nuevo `CatalogOperationalPropertiesTests`: cargar YAML real y verificar inventario. | Valores predeterminados. | Límites actuales exactos. |
| CA-02 | Sobrescrituras; extender tests de runner, clientes y sincronización. | Valores distintos, reloj/scheduler controlados y proveedores ficticios. | Intentos, pausas, intervalos y timeouts configurados aplicados. |
| CA-03 | Lectura parametrizada y contexto de arranque con valores inválidos. | Blanco, texto, cero, negativos y overflow. | Rechazo temprano, sin valor sensible ni fallback. |
| CA-04 | JAR y Compose aislados, dos arranques con el mismo artefacto/imagen. | MySQL/REST ficticios y variables distintas. | Nueva configuración sin recompilar. |
| CA-05 | Regresión de sincronización, recuperación JDBC, consumidor y consultas. | Fixtures existentes MySQL/Redis/Kafka. | Datos/versión, duplicados, límites y offsets preservados. |
| CA-06 | Contrastar README y ejemplos con inventario y Compose resuelto. | Solo configuración ficticia. | Nombres, unidades y pasos coincidentes. |

**Comprobaciones de regresión:** suite existente y Sonar CLI/IDE según disponibilidad;
sin nuevos secretos, imports externos en dominio o inyección por campos.

**Comandos verificados para compilar y ejecutar tests:** `./mvnw -B -ntp test`
y `./mvnw -B -ntp clean verify`; foco mediante `-Dtest=<clases afectadas>`.

**Pruebas operativas del backend:** JAR/Compose anteriores, sobre recursos
descartables y credenciales ficticias; no usar `.env`, claves ni servicios reales.

**Limitaciones del entorno:** sin evidencia nueva de Kafka central/Turnos.
Solo se comprobó resolución Compose; las pruebas de feature están planificadas.

<!-- Esta sección planifica la validación. Durante la implementación, registra
en TASKS.md o en el informe de validación acordado los resultados y evidencias
reales. Usar el cierre de TASKS.md definido en AGENTS.md: CA/evidencia,
regresión y desviaciones del PLAN. Distingue pruebas ejecutadas, fallidas,
no ejecutadas y bloqueadas.
Compilar o tener tests en verde no sustituye revisar los criterios de la spec. -->

## Orden de implementación

<!-- Etapas y dependencias principales. El desglose ejecutable se escribe en TASKS.md.
Incluye puntos de comprobación para avanzar con cambios pequeños. -->

1. Propiedades, YAML y validación; comprobar defaults y entradas inválidas.
2. Cableado y sustitución de constantes; comprobar valores alternativos y regresión.
3. Compose/README, ensayo operativo y cierre con evidencia de CA.

## Riesgos y decisiones pendientes

<!-- Riesgos concretos de esta solución y cómo se resolverán, sin listas genéricas.
Escribe Ninguna en las decisiones pendientes cuando estén resueltas. -->

- **Riesgos y medidas acordadas:** confundir unidades u omitir constantes: revisar usos y valores alternativos. Un intervalo mayor que la vigencia produce STALE entre comprobaciones; documentarlo sin cambiar la política.
- **Decisiones pendientes:** ninguna.

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
