# SPEC: CI y calidad con GitHub Actions y SonarQube Cloud

**Estado:** Aprobada <!-- Borrador | En revisión | Aprobada -->

<!-- PARA LA PERSONA
Copia esta plantilla como SPEC.md en una carpeta de la funcionalidad.
Pide al agente que la complete contigo según los requisitos del backend.
SPEC.md define qué debe cumplirse; PLAN.md desarrolla cómo implementarlo;
TASKS.md organiza los pasos de ejecución.
-->

<!-- PARA EL AGENTE
- Lee las instrucciones del proyecto e inspecciona el repositorio para comprobar
  el comportamiento actual. Las Mobile Guidelines no corresponden a este backend.
- Completa esta spec con la persona: investiga lo comprobable y consulta las
  decisiones pendientes. Haz pocas preguntas por vez y actualiza las respuestas.
- No inventes requisitos ni exclusiones. Distingue propuestas de decisiones
  confirmadas y marca como PENDIENTE lo que aún no esté resuelto.
- Evalúa los casos alternativos propios de esta automatización.
- No incluyas diseño de clases, tablas, componentes, archivos o algoritmos:
  esos detalles pertenecen a PLAN.md. Sí registra restricciones explícitas del pedido.
- Mantén el documento breve y proporcional a la funcionalidad. Conserva los comentarios.
- Un documento completo no está aprobado automáticamente. Solicita aprobación
  antes de marcarlo como Aprobada. No implementes durante esta etapa.
-->

## Qué construimos y para quién

<!-- Qué necesidad resolvemos, quién tiene esa necesidad y qué podrá hacer.
Describe el objetivo en lenguaje de producto. -->

Una verificación automática para desarrolladores y revisores del backend de Catálogo: compilar, ejecutar pruebas y consultar calidad y cobertura desde cada PR y desde `main`.

Referencia: [issue #15](https://github.com/prog2-perassiferrara/backend-catalogo/issues/15).


## Situación actual

<!-- Comportamiento actual relevante, limitación que queremos resolver y
comportamientos existentes que deben conservarse. No describas la arquitectura. -->

La rama de esta funcionalidad contiene el setup Maven y las pruebas de #4, sin los cambios del PR #14. No tiene workflow de CI ni configuración de JaCoCo o SonarScanner en Maven. Las pruebas actuales usan MySQL temporal con Testcontainers y no necesitan `.env`.

Sonar ya publicó resultados para el PR #14. El método administrativo activo debe comprobarse al configurar CI; el análisis automático se desactivará al pasar a CI.


## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. -->

- **RF-01:** Verificar automáticamente los PR dirigidos a `main` y los pushes a `main` mediante compilación y ejecución de las pruebas existentes.
- **RF-02:** Ejecutar las pruebas con recursos aislados, sin depender de servicios reales ni credenciales de cátedra o de otros backends.
- **RF-03:** Publicar el análisis de calidad y la cobertura en el proyecto existente de SonarQube Cloud, asociados al commit y al PR correspondientes.
- **RF-04:** Hacer fallar el job afectado ante errores de compilación, pruebas o ejecución/publicación del análisis. El Quality Gate es informativo: su incumplimiento se muestra en Sonar y su check, sin hacer fallar por ese motivo el job de análisis de GitHub Actions.
- **RF-05:** Proteger los secretos y mantener un único método de análisis activo al migrar a CI.
- **RF-06:** Documentar cómo configurar y consultar las verificaciones, sus límites y la evidencia de una ejecución real.


## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

- Corregir hallazgos existentes, implementar funcionalidades o cambiar la arquitectura.
- Modificar Turnos o KMP, desplegar el backend o verificar manualmente la persistencia del volumen de Compose.
- Establecer porcentajes adicionales de cobertura, cambiar el Quality Gate para ocultar fallos o configurar reglas de protección de ramas.


## Flujo de usuario

<!-- Cómo se inicia, qué hace el usuario y qué resultado obtiene.
Incluye pantallas afectadas, navegación y alternativas relevantes. -->

1. El desarrollador abre o actualiza un PR hacia `main`, o integra cambios en `main`.
2. GitHub Actions ejecuta las verificaciones y envía el análisis y la cobertura a SonarQube Cloud.
3. El desarrollador o revisor consulta los resultados; distingue pruebas fallidas, problemas de infraestructura y hallazgos de calidad. El éxito de las pruebas no garantiza el éxito del Quality Gate.


## Datos y reglas de negocio

<!-- Información que necesita el usuario, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de presentación. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

- Cada resultado corresponde al código de un commit; el análisis de PR debe identificarse como tal.
- La cobertura procede de las pruebas ejecutadas, sin inventar valores ni exigir un porcentaje no acordado.
- Un fallo de análisis o de comunicación no equivale a un resultado de calidad aprobado.
- El token de Sonar es exclusivo de la automatización y se configura como secreto de GitHub. No se versiona ni se publica en logs.
- Que un job falle no impide por sí solo el merge: eso depende de las reglas del repositorio, fuera de este alcance.
- El check de Sonar puede indicar un Quality Gate fallido aunque el job de análisis termine correctamente. No se configura como requisito de merge en esta funcionalidad ni se modifican las reglas existentes de aprobación del profesor.


## Casos alternativos

<!-- Incluye escenarios relevantes para la automatización de CI.
Expresa resultados, no mecanismos técnicos. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Ejecución en curso | GitHub muestra la ejecución pendiente o en curso. |
| Informes ausentes | No presentar cobertura o análisis ausentes como resultados aprobados. |
| Configuración inválida | Una configuración o credencial inválida hace fallar el análisis con diagnóstico sin secretos. |
| Error o espera excesiva | La ejecución termina con un estado identificable; no queda marcada como exitosa. Los límites técnicos se definen en el PLAN. |
| Sin conexión o conexión interrumpida | Si no pueden obtenerse dependencias o publicarse resultados, la ejecución afectada falla; puede reintentarse tras resolver la causa. |
| Ejecución cancelada | No cuenta como validación exitosa. |
| Consulta posterior | El revisor puede consultar el estado registrado de la ejecución en GitHub. |


## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de accesibilidad o rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: Usar Room puede ser una restricción; el diseño de entidades va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

- Usar GitHub Actions, SonarQube Cloud, Maven Wrapper y Java 25, conservando el setup existente.
- Usar JaCoCo para generar cobertura y SonarScanner for Maven para enviarla con el análisis, según el alcance del issue #15.
- Mantener Docker disponible para las pruebas actuales con Testcontainers y MySQL.
- Trabajar desde `main`, independientemente del PR #14. Tras integrar CI, la rama de ese PR podrá incorporar `main` para ejecutar las nuevas verificaciones.


## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-01:** Dado un PR hacia `main` o un push a `main`, cuando se publica el cambio, entonces se ejecutan compilación y pruebas automáticamente.
- **CA-02 · RF-02:** Dado un entorno de CI sin `.env` ni acceso a servicios reales de cátedra o Catálogo, cuando se ejecutan las pruebas, entonces estas usan recursos aislados y pueden completarse.
- **CA-03 · RF-03:** Dada una ejecución con pruebas y análisis completados, cuando se consultan los resultados, entonces Sonar muestra el commit y PR correspondientes y recibe el informe de cobertura generado.
- **CA-04 · RF-04:** Dado un error de compilación, pruebas o ejecución/publicación del análisis, cuando finaliza la ejecución afectada, entonces su job no figura como exitoso. Dado un análisis completado cuyo Quality Gate falla, entonces ese resultado queda visible en Sonar y su check, sin hacer fallar por ese motivo el job de análisis.
- **CA-05 · RF-05:** Dado el paso a CI, cuando queda habilitado el nuevo análisis, entonces Automatic Analysis está desactivado y el token solo está configurado como secreto, sin aparecer en archivos versionados ni logs.
- **CA-06 · RF-06:** Dada una ejecución real, cuando un revisor consulta la documentación, entonces encuentra cómo configurar y leer los resultados, evidencia de la ejecución y los límites de lo comprobado.


## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Publicar el PR de CI y comprobar un push posterior a `main`. | Ejecuciones asociadas a ambos eventos. |
| CA-02 | Ejecutar las pruebas en CI sin secretos ni servicios reales de integración. | Pruebas con recursos temporales y aislados. |
| CA-03 | Consultar el análisis del commit y del PR y el informe de cobertura. | Identificación correcta e importación del informe, sin umbral adicional. |
| CA-04 | Comprobar errores de ejecución y un Quality Gate incumplido con la validación definida en el PLAN. | Los errores hacen fallar su job; el Quality Gate incumplido se muestra como informativo sin hacer fallar el job de análisis. |
| CA-05 | Revisar el método activo y el uso del secreto, sin mostrar su valor. | Solo análisis por CI y ausencia de secretos publicados. |
| CA-06 | Revisar README y evidencia de ejecución. | Instrucciones reproducibles y límites explícitos. |


## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

Ninguna funcional. Los identificadores de Sonar, la configuración concreta del workflow y su validación se desarrollarán en el PLAN. Esta SPEC permanece en borrador hasta su revisión y aprobación.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el alcance está acordado, los flujos son coherentes, los casos
alternativos están cubiertos y cada requisito tiene criterios comprobables.
Resuelve las dudas y los marcadores pendientes. Mantén el diseño técnico en PLAN.md.
-->
