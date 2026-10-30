# TASKS: Parametrización operativa

**SPEC y PLAN:** aprobados, 2026-10-29.
**Tareas:** aprobadas, 2026-10-29. **Estado:** completadas.

**Chequeo previo:** 5 RF cubiertos por 6 CA, todos con validación en PLAN y tareas siguientes; sin alcance adicional ni decisiones bloqueantes.

- [x] **T-01 · Propiedades y validación**
  - **Alcance:** grupos inmutables, valores YAML y validación temprana segura.
  - **Referencias:** RF-01, RF-02, RF-03; CA-01, CA-03. **Dependencias:** ninguna.
  - **Verificación:** defaults exactos, sobrescrituras y rechazo de valores inválidos sin filtraciones.
- [x] **T-02 · Cableado y consumo de valores**
  - **Alcance:** constructores, clientes, runner, monitor y presupuestos; conservar algoritmos y contratos.
  - **Referencias:** RF-01, RF-04; CA-02, CA-05. **Dependencias:** T-01.
  - **Verificación:** valores alternativos gobiernan intentos/pausas/timeouts; regresión de sincronización y recuperación.
- [x] **T-03 · Ejecución y documentación**
  - **Alcance:** Compose opcional, ejemplos y README; ensayo JAR/Compose con recursos descartables.
  - **Referencias:** RF-02, RF-05; CA-02, CA-04, CA-06. **Dependencias:** T-02.
  - **Verificación:** mismo artefacto con configuraciones distintas; nombres/unidades/documentación coincidentes.
- [x] **T-04 · Regresión y cierre**
  - **Alcance:** suite completa, revisión local de calidad y evidencia breve.
  - **Referencias:** RF-01 a RF-05; CA-01 a CA-06. **Dependencias:** T-01 a T-03.
  - **Verificación:** `./mvnw -B -ntp clean verify`, Sonar CLI/IDE según disponibilidad, Javadoc y `git diff --check`.

## Cierre

| CA | Estado | Evidencia |
| --- | --- | --- |
| CA-01 | Verificado | `CatalogOperationalPropertiesTests`: los 21 valores del YAML coinciden con el inventario. |
| CA-02 | Verificado | Sobrescrituras de las 21 variables; presupuestos alternativos en runner, snapshot, incrementos y recuperación; monitor en milisegundos y timeouts HTTP independientes. JAR/Compose aplican presupuestos y pausas distintos. |
| CA-03 | Verificado | Blancos, texto, fracciones, cero, negativos, overflow y ausencia de valores: error seguro; contexto rechaza antes de construir recursos. |
| CA-04 | Verificado | Dos arranques JAR y dos Compose con el mismo artefacto/imagen: presupuestos de 1 y 2 intentos, health UP y solicitudes centrales contadas. Sin reconstrucción entre casos. |
| CA-05 | Verificado | Regresión completa: sincronización, snapshots, recuperación JDBC, duplicados, offsets y consultas concurrentes conservados. |
| CA-06 | Verificado | 21 variables coherentes entre YAML, Compose y ejemplos; README documenta significado, unidades, restricciones y aplicación. |

**Regresión:** `./mvnw -B -ntp clean verify`, **454 pruebas, 0 fallos/errores/omisiones, BUILD SUCCESS**, terminado el 2026-10-29 a las 23:24:45 -03:00. Son 48 pruebas nuevas respecto de las 406 anteriores. Foco de configuración/ciclos: 125 correctas. El primer recorrido completo detectó 8 fixtures que aún esperaban segundos; corregida su equivalencia a milisegundos, el foco de seguimiento/persistencia/consumidor pasó 21 pruebas y la regresión limpia final pasó completa. Log: `/tmp/catalog-operations-verify-final.log`.

**Ensayo operativo:** cuatro casos correctos con MySQL propio y REST ficticio; con un intento se registra un login fallido, y con dos se registran dos logins y dos descargas fallidas de snapshot. Pausas configuradas de 40/60 ms respetadas y health UP. El JAR ensayado precede al build limpio final, sin cambios Java posteriores. Resultado: `/tmp/catalog-operations-runtime-result.json`. Se retiraron contenedores, redes, volumen, imagen y claves ficticias propios; no se usaron `.env` ni claves reales, ni servicios de cátedra. No acredita un incremento central ni reservas de Turnos.

**Calidad local:** Sonar for IDE, puentes 64120/64121, 26 Java sin hallazgos; CLI, escaneo de secretos de esos archivos correcto. Javadoc de 11 Java productivos, `-Xdoclint:all,-missing`, sin errores/avisos. Dominio, inyección por constructor, formato y ausencia de `@Data`/`@Autowired` revisados. Análisis remoto posterior no verificado.

**Desviaciones del PLAN:** ninguna de alcance, contratos o solución técnica.
