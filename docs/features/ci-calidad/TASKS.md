# TASKS: CI y calidad

**Referencias:** [SPEC aprobada](SPEC.md), [PLAN aprobado](PLAN.md).
**Estado:** Implementado localmente; validación remota pendiente.
**Aprobación y autorización:** confirmadas el 2026-10-09.

## Tareas y dependencias

- [x] **T-01 · Configurar y comprobar Maven**
  - **Objetivo y alcance:** JaCoCo y SonarScanner en `pom.xml`, sin cambios de código Java.
  - **Dependencias:** ninguna.
  - **Referencias:** RF-02, RF-03, RF-04; CA-02, CA-03, CA-04.
  - **Validación:** `clean verify`, resultados de pruebas, informes XML/HTML y resolución del plugin Sonar.

- [x] **T-02 · Crear y revisar workflow e instrucciones**
  - **Objetivo y alcance:** `.github/workflows/ci.yml` y README, con eventos, permisos, secreto y Quality Gate informativo.
  - **Dependencias:** T-01 para ejecutar el build; revisión de configuración independiente.
  - **Referencias:** RF-01, RF-04, RF-05, RF-06; CA-01, CA-04, CA-05, CA-06.
  - **Validación:** sintaxis y revisión de eventos, propagación de errores, permisos, SHA de acciones y enlaces de documentación.

- [ ] **T-03 · Configurar Sonar y verificar el PR**
  - **Objetivo y alcance:** confirmar secreto y método activo; ejecutar el workflow al publicar el PR y revisar análisis/cobertura.
  - **Dependencias:** T-01, T-02 y publicación del usuario.
  - **Referencias:** RF-01, RF-03, RF-04, RF-05, RF-06; CA-01, CA-03, CA-04, CA-05, CA-06.
  - **Validación:** enlace a ejecución y análisis del commit/PR, importación de cobertura y estados reales disponibles.

- [ ] **T-04 · Confirmar ejecución en main**
  - **Objetivo y alcance:** comprobar el evento push a `main` tras integrar el PR.
  - **Dependencias:** T-03 y merge autorizado por el usuario.
  - **Referencias:** RF-01, RF-03, RF-06; CA-01, CA-03, CA-06.
  - **Validación:** enlace a ejecución y análisis de `main`.

## Evidencia

- El usuario confirmó que desactivó Automatic Analysis el 2026-10-09. No se inspeccionó la configuración administrativa de Sonar.
- SHA de `checkout` v7.0.1 y `setup-java` v6.0.1 verificados mediante la API oficial de GitHub.
- `./mvnw -B -ntp clean verify`: BUILD SUCCESS; 10 pruebas, 0 fallos, 0 errores y 0 omitidas, con MySQL 8.4.8 temporal. Se ejecutó con acceso autorizado a Docker.
- JaCoCo 0.8.15 generó XML válido con 2 clases e informe HTML en `target/site/jacoco/`. Esto no demuestra aún su importación en Sonar.
- Workflow: YAML parseado y bloques `run` aceptados por `bash -n`; revisión de eventos, permisos, condición para forks y Quality Gate informativo. No se ejecutó GitHub Actions localmente.
- Se ejecutó el bloque de análisis con `SONAR_TOKEN` vacío: terminó con código 1 y diagnóstico, sin ejecutar el scanner. No se probó una credencial real ni se publicó un análisis.
- `git diff --check` sin errores; enlaces locales de la funcionalidad comprobados.
- `./mvnw -B -ntp help:describe -Dplugin=org.sonarsource.scanner.maven:sonar-maven-plugin:5.8.0.7211 -Dgoal=sonar`: BUILD SUCCESS; plugin resuelto sin enviar un análisis.
- El usuario indicó que todavía necesita generar/cargar el token; se proporcionaron las instrucciones. Pendientes: secreto, análisis del PR e importación de cobertura, y ejecución en `main` tras el merge. No se han hecho commits, push ni merge.
