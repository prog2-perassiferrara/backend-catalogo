# PLAN: CI y calidad con GitHub Actions y SonarQube Cloud

**SPEC de referencia:** [SPEC.md](SPEC.md)
**Versión de la spec revisada:** 2026-10-09; Quality Gate informativo, aprobada en conversación.
**Estado:** Aprobado <!-- Borrador | En revisión | Aprobado -->

**Aprobación y autorización de implementación:** confirmadas por el usuario el 2026-10-09.

<!-- PARA LA PERSONA
Copia esta plantilla como PLAN.md junto a la SPEC.md aprobada.
Este documento define la solución técnica. Una vez revisado, el agente puede
derivar TASKS.md con tareas, dependencias y comprobaciones.
-->

<!-- PARA EL AGENTE
- Lee la SPEC.md aprobada, las instrucciones del proyecto. Mobile Guidelines no corresponde al backend.
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
| Build Java 25 / Spring Boot 4.1.1 | `pom.xml` | Agregar plugins de cobertura y análisis. |
| Maven Wrapper | `mvnw`, `.mvn/wrapper/maven-wrapper.properties` | Ejecutar el build reproducible. |
| Pruebas de setup | `src/test/java/com/prog2/catalog/CatalogApplicationTests.java` | Reutilizar MySQL 8.4.8 con Testcontainers. |
| Instrucciones de ejecución | `README.md` | Explicar CI y lectura de resultados. |

**Convenciones y patrón de referencia:** se respetan las reglas de `hexagonal-arch`; no se modifican capas ni se crean slices. Es configuración de build y automatización, sin patrones de diseño nuevos ni diagramas necesarios.


## Solución propuesta

<!-- Explica el enfoque y sus motivos. Describe las responsabilidades y el
recorrido de datos y eventos hasta la interfaz. Usa un diagrama si aporta claridad. -->

Un workflow con un job en `ubuntu-24.04`, checkout completo, Temurin 25 y caché Maven. Se dispara con pushes a `main` y PR hacia `main` (apertura, actualización y reapertura).

1. Ejecutar `./mvnw -B -ntp clean verify`: elimina resultados anteriores, compila, prueba, empaqueta y genera cobertura.
2. En otro paso del mismo job, ejecutar `./mvnw -B -ntp sonar:sonar`, usando las clases y el informe producidos. El token se expone solo a este paso.
3. Establecer `sonar.qualitygate.wait=false`: los problemas de calidad quedan visibles en Sonar y su check; no hacen fallar el job de Actions. No usar `continue-on-error`, porque ocultaría errores reales del scanner.

Testcontainers levanta MySQL; no se agrega un servicio MySQL ni se ejecuta Compose. El workflow tiene permiso `contents: read`, límite de 20 minutos y cancela ejecuciones anteriores de la misma referencia al llegar una nueva.


## Módulos y componentes afectados

<!-- Si el proyecto está modularizado, identifica los módulos afectados, sus
responsabilidades y la dirección de sus dependencias. Respeta los límites
existentes y justifica cualquier módulo o dependencia nueva. Si no está
modularizado, describe las carpetas o componentes afectados sin introducir
modularización fuera del alcance; marca la tabla de módulos como No aplica. -->

No aplica la división en módulos: se conserva el proyecto Maven existente.

| Componente o ruta | Acción | Cambio y responsabilidad | Requisito relacionado |
| --- | --- | --- | --- |
| `pom.xml` | Modificar | JaCoCo, SonarScanner e identificadores públicos de Sonar. | RF-03, RF-04 |
| `.github/workflows/ci.yml` | Crear | Eventos, entorno, pruebas y análisis. | RF-01, RF-02, RF-04, RF-05 |
| `README.md` | Modificar | Configuración y lectura de resultados. | RF-06 |
| Sonar / secretos de GitHub | Configurar con el usuario | Token y cambio de método de análisis. | RF-05 |
| Documentos de esta carpeta | Actualizar | Tareas y evidencia breve de validación. | RF-06 |

<!-- Distingue lo que se reutiliza, modifica o crea. Las rutas nuevas son propuestas.
Señala impacto sobre modelos, contratos o componentes compartidos. -->

## Datos y contratos

<!-- Completa solo lo aplicable. Si un punto no aplica, indica el motivo. -->

- **Entrada:** código del commit, metadata del evento de GitHub y secreto `SONAR_TOKEN`.
- **Salida:** reportes de pruebas, cobertura XML/HTML en `target/site/jacoco/` y análisis remoto asociado al commit/PR.
- **Identificadores públicos:** organización `prog2-perassiferrara`, proyecto `prog2-perassiferrara_backend-catalogo`, verificados previamente mediante la API pública de Sonar.
- **Persistencia y migraciones:** no cambia la base del backend. Los datos de prueba son temporales y el historial de análisis queda en Sonar.
- **Contratos del backend:** no se modifican APIs, modelos ni comunicación entre servicios.


## Estado, operaciones y errores

<!-- Cómo se implementan los comportamientos aprobados en la spec.
Referencia RF/CA y contempla los fallos propios de CI. -->

- GitHub registra el estado de ejecución; el job se detiene ante fallo de `verify` o del scanner (RF-04).
- El Quality Gate es informativo; no se agregan reglas de protección ni se alteran las aprobaciones del profesor.
- Sin token en un PR interno o push a `main`, el paso de análisis falla con diagnóstico sin mostrar el valor. No hay reintentos automáticos añadidos; puede relanzarse la ejecución.
- PR desde forks: ejecutar pruebas sin secretos y omitir explícitamente el análisis, porque GitHub no entrega el token. El flujo habitual del proyecto usa ramas del mismo repositorio; no se agrega infraestructura para analizar forks.
- Cancelaciones y timeouts no cuentan como verificaciones exitosas. No hay estado de UI ni ciclo de vida mobile.


## Dependencias y configuración

<!-- Librerías, servicios, permisos o configuración afectados. Verifica compatibilidad
con el proyecto y justifica las incorporaciones. No agregues dependencias por defecto. -->

- **JaCoCo 0.8.15:** plugin de build con `prepare-agent` y `report` en `verify`; generar XML y HTML sin exclusiones ni umbrales adicionales. Java 25 está soportado desde 0.8.14. [Historial oficial](https://www.jacoco.org/jacoco/trunk/doc/changes.html).
- **SonarScanner for Maven 5.8.0.7211:** fijar el plugin en `pom.xml`, URL `https://sonarcloud.io` e informe `target/site/jacoco/jacoco.xml`. [Release oficial](https://github.com/SonarSource/sonar-scanner-maven/releases/tag/5.8.0.7211).
- **Actions:** `checkout` v7.0.1 y `setup-java` v6.0.1, releases verificadas; fijar sus SHA al implementar, indicando versión en comentario. Checkout con `fetch-depth: 0`; caché Maven mediante `setup-java`.
- El usuario genera el token de Sonar con acceso de análisis al proyecto y lo carga directamente en GitHub como `SONAR_TOKEN`. No se solicita su valor por chat ni se guarda en `.env`.
- Antes del primer análisis por CI, desactivar Automatic Analysis en Sonar. [Guía oficial de Actions y Quality Gate](https://docs.sonarsource.com/sonarqube-cloud/analyzing-source-code/ci-based-analysis/github-actions-for-sonarcloud), [método automático](https://docs.sonarsource.com/sonarqube-cloud/analyzing-source-code/automatic-analysis).


## Estrategia de validación

<!-- Una fila por criterio de la spec. Selecciona el método capaz de demostrarlo:
test unitario, integración, UI o prueba manual. No todos requieren todos los métodos.
Identifica tests existentes y separa los nuevos propuestos. Incluye regresiones relevantes.
Una captura aislada no demuestra persistencia ni ausencia de peticiones de red. -->

| Criterio | Método | Entorno | Evidencia prevista |
| --- | --- | --- | --- |
| CA-01 | Revisar eventos y ejecutar el PR; comprobar push a `main` tras el merge. | GitHub Actions | Enlaces a ejecuciones. |
| CA-02 | Ejecutar las pruebas existentes con `verify`. | Java 25 y Docker, sin `.env` | Reporte de pruebas. |
| CA-03 | Revisar XML local y su importación en Sonar, incluyendo commit/PR. | Local y CI | Informe y análisis remoto. |
| CA-04 | Revisar propagación de códigos de salida y `qualitygate.wait=false`; comprobar estados reales disponibles. | Workflow y logs | No ocultar errores del scanner; registrar si no hay un Gate fallido disponible. |
| CA-05 | Revisar uso del secreto y método activo sin mostrar valores. | GitHub y Sonar | Confirmación de configuración. |
| CA-06 | Revisar README y registrar resultados en TASKS. | Repositorio | Instrucciones y evidencia breve. |

**Regresión y comando existente:** `./mvnw -B -ntp verify`. Después del cambio debe generar `target/site/jacoco/jacoco.xml` y el HTML. No agregar tests Java para comprobar configuración YAML ni provocar defectos en el código para hacer fallar Sonar.

**Dispositivos:** no aplica.

**Limitaciones:** la validación remota necesita token, cambio de método y publicación del PR por el usuario. El evento push a `main` se comprueba después del merge; no se marcará esa evidencia como completada antes. El Quality Gate puede seguir rojo por hallazgos fuera de este issue.

<!-- Esta sección planifica la validación. Durante la implementación, registra
en TASKS.md o en el informe de validación acordado los resultados y evidencias
reales. Distingue pruebas ejecutadas, fallidas, no ejecutadas y bloqueadas.
Compilar o tener tests en verde no sustituye revisar los criterios de la spec. -->

## Orden de implementación

<!-- Etapas y dependencias principales. El desglose ejecutable se escribe en TASKS.md.
Incluye puntos de comprobación para avanzar con cambios pequeños. -->

1. Configurar Maven → verificar pruebas e informes de cobertura.
2. Crear workflow y documentar configuración → revisar eventos, permisos y tratamiento de errores.
3. Configurar Sonar con el usuario y ejecutar CI → registrar evidencia disponible y dejar pendiente el push a `main` hasta el merge.


## Riesgos y decisiones pendientes

<!-- Riesgos concretos de esta solución y cómo se resolverán, sin listas genéricas.
Escribe Ninguna en las decisiones pendientes cuando estén resueltas. -->

- **Riesgos:** Automatic Analysis activo rechaza el análisis por CI; el cambio de método debe coordinarse antes de ejecutar el scanner. La primera ejecución puede tardar por descargas; se limita a 20 minutos. Un check de Sonar requerido por reglas existentes podría bloquear el merge aunque el job de Actions esté verde; este issue no cambia esas reglas.
- **Decisiones pendientes:** ninguna de diseño. El usuario confirmó que desactivó Automatic Analysis el 2026-10-09. Queda confirmar la configuración del token y validar la ejecución remota.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el plan cubre los requisitos, respeta las exclusiones, reutiliza
componentes verificados y permite demostrar todos los criterios de aceptación.
Resuelve dudas y marcadores pendientes. Si la spec cambió, revisa su impacto.
Tras aprobar el plan, deriva TASKS.md con IDs, dependencias, referencias a RF/CA
y comprobaciones. No marques una tarea terminada sin realizar su validación;
si está bloqueada, registra el motivo.
-->
