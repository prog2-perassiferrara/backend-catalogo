# TASKS: Simplificación de configuración y boilerplate

Autorización: pedido explícito del 2026-11-03, solo bloque 1. Chequeo RF→CA→PLAN/TASKS realizado: tres RF con aceptación y verificación, sin decisiones funcionales abiertas.

- [x] **T-01:** Baseline limpio de rama 26 sobre ba614ef; clean verify: 463 tests sin fallos, errores u omisiones. CA-01/02/03.
- [x] **T-02:** Configurar Lombok y sustituir únicamente wiring y miembros triviales descritos en PLAN. Depende de T-01; CA-01/03.
- [x] **T-03:** Comparar API/cuerpos, contratos, configuración y fuentes de Turnos; ejecutar clean verify final y calidad disponible. Depende de T-02; CA-01/02/03.
- [x] **T-04:** Registrar evidencia final y preparar commits autorizados, sin push/PR/merge. Depende de T-03.

## Cierre

**Cierre verificado después de corregir el almacenamiento temporal de pruebas.** El diagnóstico anterior se conserva debajo. Propiedades, transacciones, algoritmos, seguridad, migraciones y cuerpos/aserciones de tests sin cambios; la única adaptación de pruebas son ocho montajes tmpfs.

Comprobaciones realizadas: API pública/protegida de 139 fuentes equivalente mediante javap; comparación de cuerpos conserva todo salvo las sustituciones declaradas. Recursos y archivos registrados de Turnos idénticos; cuerpos/aserciones de tests conservados, con la adaptación de montaje descrita debajo. Se mantienen los seis nombres de bean. Lombok se procesa con javac release 25 y queda excluido del JAR ejecutable.

La primera corrida final ejecutó 463 pruebas con seis errores JDBC: cinco por un único contexto que falló al migrar V3 por timeout de lectura de 10 s y otro durante un UPDATE de preparación de CatalogSearchHttpTests. No hubo aserciones fallidas. Las dos clases pasaron después aisladas (24 pruebas, 0 fallos/errores/omisiones) sin cambiar fuentes, configuración, orden o timeouts. El episodio no demuestra una regresión de wiring, pero debe conservarse como intermitencia observada.

La segunda corrida completa tuvo un único error en el commit de preparación de CatalogSearchHttpTests, también por SocketTimeoutException. Otra repetición se detuvo al reaparecer el timeout de Flyway V3. Durante ese episodio se midió presión de I/O del host (full avg10 33,01%); no acredita por sí sola la causa exacta. La clase de búsquedas original anterior al refactor pasó aislada (19 pruebas). Se valida además la suite completa con `-DreuseForks=false`, que aísla JVM por clase sin cambiar límites ni aserciones; su resultado no sustituye silenciosamente el de la ejecución predeterminada.

El ensayo con JVM por clase también se detuvo al fallar Flyway V3; no resolvió la incidencia. En la copia original anterior al refactor se reprodujo SocketTimeoutException durante un commit de preparación de CatalogQueryConcurrencyTests: cinco pruebas, un error LOCAL_STORAGE_UNCERTAIN y cero aserciones fallidas. Se confirma así un fallo JDBC preexistente reproducible en este entorno, no su causa física exacta. No se ampliaron timeouts ni se alteraron migraciones, pruebas o recursos externos para obtener verde.

| CA | Estado | Evidencia |
| --- | --- | --- |
| CA-01 | Verificado tras la corrección | Baseline 463 correctas. Pruebas enfocadas finales: 44 correctas; dos clean verify estándar completos consecutivos con 463 correctas cada uno, sin omisiones. |
| CA-02 | Verificado en las pruebas ejecutadas | Defaults, variables, errores seguros y arranque conservan sus pruebas; configuración byte a byte idéntica. |
| CA-03 | Verificado localmente | API pública/protegida equivalente, cuerpos normalizados equivalentes y contratos/recursos/Turnos sin cambios. |

Producción: 139 archivos Java y 152 tipos declarados antes/después; 5.516→5.396 líneas físicas (-120). Sustituidos 13 constructores, 19 getters, dos loggers y seis fábricas. No hay puertos ni clases productivas nuevos. Sin trabajo de bloques posteriores.

### Diagnóstico y corrección del bloqueo JDBC

Autorizado posteriormente el diagnóstico y corrección de infraestructura de pruebas, sin ampliar la simplificación. Capturado MySQL temporal activo con una consulta en waiting for handler commit durante más de 11 s; otras consultas respondían en aproximadamente 60 ms, sin espera de bloqueo de filas. Hubo fallos tanto en Flyway como en commits de preparación. No se observó OOM o reinicio anormal; el servidor auxiliar inicial de la imagen y su parada forman parte del arranque normal.

La parada controlada de ambos Compose no bastó: una corrida completa tuvo un error equivalente en CatalogSearchPersistenceTests. Se restauraron los cuatro contenedores originales, con los mismos IDs, imágenes y volúmenes; no había clientes conectados, procesos pendientes ni teléfonos activos al detenerlos. No se atribuye la causa exclusivamente a esos servicios ni al porcentaje de presión de I/O.

Dos suites ya utilizaban tmpfs; las demás dependían de la capa de disco de Docker. Una copia aislada que solo cambió ese montaje pasó las tres clases afectadas (44 pruebas), con esperas de archivos del orden de milisegundos. Se aplicó el mismo montaje a ocho clases, sin nuevas abstracciones, cambios SQL, parámetros de durabilidad, límites o aserciones. Las mismas 44 pruebas pasan en el repositorio con ambos Compose activos.

**Regresión final:** dos ejecuciones consecutivas de `./mvnw -B -ntp clean verify`, sin opciones de aislamiento adicionales: 463 pruebas/35 suites, cero fallos, errores u omisiones en cada ejecución. BUILD SUCCESS en 3:41 y 3:29, con ambos Compose activos. Incluye contextos, entorno/configuración, seguridad/JWT, contratos HTTP internos, OpenAPI habilitado/deshabilitado, snapshot, incrementos, discontinuidades, commits ambiguos, recovery e idempotencia Kafka. Lombok excluido del JAR ejecutable. Contratos, configuración externa y código de Turnos conservados; no se acredita recepción nueva de eventos de cátedra.

**Desviación justificada:** ocho montajes tmpfs y una importación en ocho pruebas, más una nota de ejecución en README, para eliminar dependencia del almacenamiento local en bases descartables. La causa inmediata capturada fue una espera de commit de MySQL superior al timeout JDBC; no se identificó qué proceso o detalle del filesystem produjo cada demora. La parada de los Compose no bastó, por lo que no se atribuye exclusivamente a su carga. No se amplió ningún timeout ni se alteró lógica productiva. Los 463 tests permanecen. Se recomienda congelar el código tras revisar/publicar estos commits; feedback CI/Sonar remoto pendiente de publicación.

Calidad: revisión del diff sin nuevos secretos evidentes; análisis focal CLI sin hallazgos de secretos. Análisis de código CLI bloqueado por Vortex 403, puente IDE sin acceso a los archivos y escaneo amplio de secretos rechazado por revisión automática por posible envío externo. No se acredita análisis general local ni nuevo Gate remoto.
