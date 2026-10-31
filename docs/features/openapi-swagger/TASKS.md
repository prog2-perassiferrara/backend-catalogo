# TASKS: OpenAPI y Swagger UI

**SPEC, PLAN y tareas:** aprobados, 2026-10-30. **Estado:** completado; alcance de validación indicado abajo.

**Chequeo previo:** 5 RF cubiertos por 6 CA; validación y tareas asociadas, sin alcance adicional ni decisiones bloqueantes.

- [x] **T-01 · Dependencia, interruptor y seguridad**
  - **Alcance:** starter MVC, configuración documental y acceso GET condicionado.
  - **Referencias:** RF-01, RF-03, RF-04; CA-02, CA-03. **Dependencias:** ninguna.
  - **Verificación:** compatibilidad con Boot 4.1.1 y documentación habilitada/deshabilitada, sin ampliar acceso a APIs.
- [x] **T-02 · Contratos y esquemas**
  - **Alcance:** anotaciones de seis operaciones, parámetros/DTO y error común.
  - **Referencias:** RF-01, RF-02, RF-03, RF-05; CA-01, CA-02, CA-04. **Dependencias:** T-01.
  - **Verificación:** contrato generado, nombres de esquemas distintos y errores/perfiles reales.
- [x] **T-03 · Pruebas HTTP**
  - **Alcance:** generador, controladores, mappers y seguridad reales; puertos de negocio observados.
  - **Referencias:** RF-01 a RF-05; CA-01 a CA-05. **Dependencias:** T-02.
  - **Verificación:** esquema, UI, bearer, denegaciones y cero invocaciones de negocio al leer documentación.
- [x] **T-04 · Ejecución, documentación y cierre**
  - **Alcance:** README/ejemplos, JAR aislado y prueba funcional de UI; regresión y revisión local.
  - **Referencias:** RF-04, RF-05; CA-02 a CA-06. **Dependencias:** T-03.
  - **Verificación:** `clean verify`, Javadoc, Sonar CLI/IDE según disponibilidad; Compose resuelto y evidencia breve por CA.
- [x] **T-05 · Simplificar metadatos OpenAPI**
  - **Alcance:** constante privada para `status`, anotaciones `@ApiResponse` repetibles en las seis operaciones y mock `searchUseCase` distinguible de la ruta `SEARCH` en el test.
  - **Referencias:** RF-02; CA-01, CA-02. **Dependencias:** T-02, T-03.
  - **Verificación:** 54 pruebas focalizadas de OpenAPI y seguridad correctas; Sonar IDE sin hallazgos y escaneo de secretos correcto en los cuatro Java modificados (2026-10-30). Contrato y permisos conservados.
    Tras renombrar el mock, las siete pruebas de `OpenApiHttpTests` pasan; Sonar IDE y escaneo de secretos del test correctos.

## Cierre

| Criterio | Evidencia y resultado |
| --- | --- |
| CA-01 | Nueve pruebas nuevas verifican seis operaciones, filtros, límites, errores y proyecciones distintas. Correcto. |
| CA-02 | Verificado: el usuario confirma Authorize y consultas de usuario correctas en Swagger UI con JWT emitido por Turnos. Ambos perfiles tienen pruebas HTTP con seguridad real; el perfil de servicio no se probó manualmente desde la UI. |
| CA-03 | Tests y dos arranques del JAR comprueban documentación apagada/encendida, health y APIs protegidas. Correcto. |
| CA-04 | Ejemplos ficticios; lectura del documento sin token de ejecución y usuario rechazado en API interna. Correcto. |
| CA-05 | Cero invocaciones a los cinco puertos al leer documentación; JAR sin solicitudes centrales adicionales, con REST ficticio no disponible. Correcto. |
| CA-06 | README actualizado y Compose resuelto con interruptor ausente, `true` y `false`. Correcto. |

**Regresión inicial:** `./mvnw -B -ntp clean verify`, 463 pruebas correctas, sin fallos,
errores ni omisiones (2026-10-30). El primer intento tuvo 19 errores al iniciar
un contexto por timeout de lectura MySQL durante una migración; la repetición
completa pasó sin modificar código ni límites. Foco previo: 54 pruebas correctas.
JAR aislado, claves efímeras, MySQL y REST descartables; sin servicios reales.

**Calidad local:** Sonar for IDE sin hallazgos en los 14 Java afectados; escaneo
de secretos de Sonar CLI correcto. Javadoc de los 11 Java productivos sin errores
ni advertencias. Formato y enlaces comprobados.

**Desviaciones y límites:** ninguna funcional. La prueba manual de Swagger UI
cubre el perfil de usuario. El JWT de servicio es una credencial interna emitida
por Turnos y no se obtuvo para probar sus consultas manualmente desde Swagger;
su firma, permisos y separación de perfiles se verificaron automáticamente.
Esas pruebas no se presentan como un ensayo manual del perfil de servicio.

