# Guía para agentes: Catálogo y sincronización

## Lectura y alcance

Las rutas de este documento son relativas a la raíz de este repositorio.

- Antes de planificar, revisar o modificar, leer explícitamente [las instrucciones generales](../AGENTS.md) y [las reglas de trabajo](docs/GENERIC_RULES.md). No suponer que el archivo padre o los enlaces se cargan automáticamente. Si ya fueron leídos en la tarea y siguen vigentes, no repetir la lectura.
- Consultar las secciones pertinentes del [enunciado](../PROJECT_STATEMENT-v1.md) y del [contrato de integración](../INTEGRATION_REFERENCE-v2.md). Las reglas locales especializan las generales; no sustituyen los requisitos ni los contratos de cátedra.
- Si el repositorio se abre o clona por separado y faltan los documentos compartidos, o sus permisos impiden leerlos, informar qué falta y solicitar su ubicación para el trabajo que dependa de ellos. No inventar su contenido ni copiarlos como versiones independientes.
- Responder en español. Priorizar el aprendizaje: explicar decisiones y orientar las revisiones sin implementar soluciones no solicitadas.
- El estado actual es setup. Verificar el contenido real antes de trabajar; no generar código ni elegir dependencias por la sola presencia de estas instrucciones.
- Revisar `git status --short` en este repositorio antes de editar. Conservar cambios del usuario; una tarea local no autoriza a modificar los otros repositorios.

## Flujo por funcionalidad

1. Leer `docs/SPEC_TEMPLATE.md` para especificar y `docs/PLAN_TEMPLATE.md` para planificar. Usar `docs/PROMPTS.md` como ayuda, no como requisitos del producto.
2. Mantener los documentos en `docs/features/<nombre-de-funcionalidad>/`. Reutilizar la carpeta existente al continuar una funcionalidad. No crear documentos de funcionalidades no solicitadas.
3. Completar `SPEC.md` con comportamiento, alcance y criterios verificables, distinguiendo hechos, propuestas y pendientes. Mantener los IDs `RF-` y `CA-` de la plantilla; conservar su estructura y comentarios. No introducir diseño técnico en la SPEC.
4. Tras aprobación explícita de la SPEC, preparar `PLAN.md` con responsabilidades, contratos, decisiones técnicas y validación. Referenciar requisitos y criterios sin copiar la SPEC. No alterar las plantillas compartidas por una funcionalidad puntual.
5. Tras aprobación explícita del PLAN, derivar `TASKS.md`: tareas pequeñas y ordenadas, cada una con casilla de seguimiento, ID, objetivo, alcance, dependencias, criterios relacionados y método de validación.
6. Implementar únicamente ante autorización explícita del usuario. No deducirla de un documento completo o aprobado, ni solicitar de nuevo autorizaciones ya otorgadas.
7. Registrar resultados y evidencias reales en `TASKS.md` o en el informe de validación acordado. No marcar tareas completas con comprobaciones pendientes. Si cambia un requisito, actualizar y revisar los documentos afectados antes de implementar ese cambio.

Las funcionalidades que cruzan repositorios deben referenciar los contratos y documentos relacionados, identificando productor, consumidor y decisiones pendientes. No declarar acordado un contrato unilateralmente.

## Responsabilidad del servicio

- Mantener la única réplica local vigente de categorías, profesionales y horarios semanales, junto con la versión aplicada y el estado de sincronización.
- Resolver búsquedas y filtros obligatorios sobre datos locales; no consultar a cátedra por cada búsqueda.
- Proveer a turnos la información vigente mediante contratos explícitos. La construcción de slots con ocupaciones centrales y la gestión de reservas corresponden al servicio de turnos.
- En las especificaciones pertinentes, contemplar snapshot consistente, incrementos ordenados, notificaciones duplicadas o perdidas, discontinuidades, reinicios y fallos de persistencia. Los datos y su versión deben quedar coherentes.
- Definir los contratos de consulta y sus errores junto con sus consumidores, sin presuponer endpoints ni DTO aún no acordados.

## Arquitectura y planificación

- Usar Java y Spring Boot con la skill `hexagonal-arch`. Leer su `SKILL.md` y las referencias pertinentes antes de proponer o revisar decisiones arquitectónicas; no duplicar aquí sus instrucciones completas.
- En el PLAN, identificar slices, responsabilidades, puertos y adaptadores necesarios, respetando la dirección de dependencias. No generar un CRUD completo ni estructuras vacías por defecto.
- Mantener datos y migraciones propios y contratos internos protegidos con JWT. No acceder a la persistencia del otro backend.
- Al usar `docs/MOBILE_GUIDELINES.md` y las plantillas, aplicar los puntos pertinentes a contratos, conectividad, persistencia, concurrencia y recuperación. Marcar como no aplicables las secciones exclusivas de UI o dispositivo, sin asignar esas responsabilidades al servicio.
- No dar por elegidos motor de base de datos, versiones, build o ubicación de la gestión de usuarios. Distinguir restricciones del enunciado de decisiones aún pendientes.

## Verificación

- En el setup actual no hay comandos de compilación ni pruebas disponibles. Cuando se incorpore el build, verificar wrappers y tareas reales y documentar los comandos en el README; no inventarlos.
- Al implementar, verificar los escenarios afectados de sincronización, búsqueda, autorización de contratos y recuperación. Para incrementos, incluir idempotencia y consistencia entre datos y versión; para discontinuidades, recuperación mediante snapshot.
- Para cambios solo documentales, revisar coherencia, rutas y referencias; no ejecutar builds ajenos ni crear pruebas artificiales.
