# SPEC: Inicializar backend de catálogo y sincronización

**Estado:** Aprobada <!-- Borrador | En revisión | Aprobada -->

<!-- PARA LA PERSONA
Copia esta plantilla como SPEC.md en una carpeta de la funcionalidad.
Pide al agente que la complete contigo usando MOBILE_GUIDELINES.md.
SPEC.md define qué debe cumplirse; PLAN.md desarrolla cómo implementarlo;
TASKS.md organiza los pasos de ejecución.
-->

<!-- PARA EL AGENTE
- Lee las instrucciones del proyecto y MOBILE_GUIDELINES.md. Inspecciona el
  repositorio para comprobar el comportamiento actual. Si falta la guía, pide su ubicación.
- Completa esta spec con la persona: investiga lo comprobable y consulta las
  decisiones pendientes. Haz pocas preguntas por vez y actualiza las respuestas.
- No inventes requisitos ni exclusiones. Distingue propuestas de decisiones
  confirmadas y marca como PENDIENTE lo que aún no esté resuelto.
- Aplica las consideraciones mobile relevantes sin ampliar el alcance automáticamente.
- No incluyas diseño de clases, tablas, componentes, archivos o algoritmos:
  esos detalles pertenecen a PLAN.md. Sí registra restricciones explícitas del pedido.
- Mantén el documento breve y proporcional a la funcionalidad. Conserva los comentarios.
- Un documento completo no está aprobado automáticamente. Solicita aprobación
  antes de marcarlo como Aprobada. No implementes durante esta etapa.
-->

## Qué construimos y para quién

<!-- Qué necesidad resolvemos, quién tiene esa necesidad y qué podrá hacer.
Describe el objetivo en lenguaje de producto. -->

Preparar una base ejecutable y reproducible del backend de Catálogo para desarrollar los casos de uso revisados. El destinatario de esta etapa es quien desarrolla y verifica el servicio.

Fuentes: `../../../../PROJECT_STATEMENT-v1.md`, secciones 3.1, 3.2, seguridad y documentación; `../../use-cases/README.md`.

## Situación actual

<!-- Comportamiento actual relevante, limitación que queremos resolver y
comportamientos existentes que deben conservarse. No describas la arquitectura. -->

El repositorio contiene documentación, reglas de trabajo y un README mínimo. No tiene implementación, configuración de compilación ni infraestructura ejecutable.

El usuario cambió la decisión inicial: se partirá de Spring Boot sin el generador JHipster, con arquitectura hexagonal desde el inicio. La compatibilidad de usuarios exigida por el enunciado se mantiene.

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. -->

- **RF-01:** El backend debe poder compilarse y ejecutarse con pasos documentados y reproducibles.
- **RF-02:** El servicio debe disponer de persistencia principal en una base de datos servidor, con datos y migraciones propios.
- **RF-03:** El backend y su base de datos deben poder levantarse mediante Docker Compose.
- **RF-04:** La configuración necesaria debe estar documentada y los secretos deben suministrarse externamente, sin publicarlos.
- **RF-05:** El repositorio debe documentar requisitos de ejecución y los comandos reales de compilación y verificación disponibles.
- **RF-06:** Si falta configuración obligatoria, es inválida o la base de datos resulta inaccesible al iniciar, el backend no debe completar el arranque y debe mostrar un diagnóstico sin secretos.

**Decisión confirmada:** este issue prepara la base técnica. La adaptación y verificación de registro y autenticación se abordarán en un issue propio. Completar el setup no implica implementar ni verificar registro y autenticación.

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

- Adaptación y verificación funcional de registro y autenticación, acordadas para un issue propio.
- Implementación de la sincronización completa mediante snapshot (CU-01), acordada para un issue posterior al setup.

## Flujo de usuario

<!-- Cómo se inicia, qué hace el usuario y qué resultado obtiene.
Incluye pantallas afectadas, navegación y alternativas relevantes. -->

1. Quien desarrolla obtiene el repositorio y prepara los requisitos documentados.
2. Proporciona la configuración externa requerida.
3. Compila y levanta el servicio y su base de datos siguiendo el README.
4. Comprueba que el servicio arranca y puede utilizar su persistencia mediante la verificación documentada.

## Datos y reglas de negocio

<!-- Información que necesita el usuario, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de presentación. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

- Catálogo es propietario exclusivo de sus datos y migraciones. No accede a tablas ni repositorios internos de Turnos.
- H2, SQLite y bases en memoria no son almacenamiento principal válido para la entrega.
- Las credenciales técnicas de cátedra son distintas de las identidades de usuarios finales y no deben exponerse a KMP.
- Usar únicamente datos ficticios.
- El setup todavía no representa una sincronización ni una copia local vigente del catálogo.

## Comportamiento mobile y casos alternativos

<!-- Adapta la tabla usando MOBILE_GUIDELINES.md. Añade escenarios relevantes.
Marca No aplica con su motivo cuando corresponda. No presupongas soporte offline
ni conservación de todo el estado. Expresa resultados, no mecanismos técnicos. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Inicio del servicio | Puede verificarse el resultado del arranque con los pasos documentados. |
| Sin datos de catálogo | No se declara que el catálogo esté sincronizado por el solo hecho de arrancar. |
| Configuración obligatoria incompleta o inválida | No completa el arranque y muestra un diagnóstico sin secretos. |
| Base de datos inaccesible al iniciar | No completa el arranque y muestra un diagnóstico sin secretos. |
| Reinicio | La persistencia principal conserva los datos ya confirmados. |
| UI, navegación y ciclo de vida Android | No aplica: esta etapa prepara un backend. |

**Puntos de la guía no aplicables y motivo:** pantallas, accesibilidad visual, permisos del dispositivo, navegación y recreación de pantallas corresponden a KMP. Se consideran configuración, persistencia, conectividad y seguridad relevantes para este backend. Guía consultada: `../../../../frontend-kmp/docs/MOBILE_GUIDELINES.md`.

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de accesibilidad o rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: Usar Room puede ser una restricción; el diseño de entidades va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

- Java y Spring Boot, exigidos por el enunciado.
- Partir de Spring Boot sin el generador JHipster y adoptar arquitectura hexagonal desde el inicio, decisión del usuario. El diseño concreto corresponde al PLAN.
- Mantener exactamente dos backends independientes en la solución.
- Mantener compatibilidad con el modelo y contrato de usuarios de JHipster exigidos por el enunciado. Su adaptación y verificación funcional corresponden a un issue propio.
- El servicio central lo administra la cátedra; no se despliega como infraestructura propia.
- Las decisiones técnicas de versiones, persistencia y build se registran en PLAN.md, separado de los requisitos de esta SPEC.

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-01:** Dado un checkout y los requisitos documentados, cuando se ejecutan los pasos de compilación y arranque, entonces el backend compila y arranca.
- **CA-02 · RF-02:** Dada la base de datos servidor configurada para Catálogo, cuando el backend inicia, entonces puede utilizar su almacenamiento y aplicar sus migraciones propias.
- **CA-03 · RF-02:** Dado un dato ficticio persistido y confirmado, cuando se reinicia el servicio y su infraestructura conservando el almacenamiento, entonces el dato permanece disponible.
- **CA-04 · RF-03:** Dada la configuración documentada, cuando se levanta la infraestructura con Docker Compose, entonces el backend y su base de datos arrancan.
- **CA-05 · RF-04:** Dado el repositorio y sus archivos de ejecución, cuando se revisa cómo se suministran credenciales y secretos, entonces se encuentran externalizados y no publicados.
- **CA-06 · RF-05:** Dado el README, cuando se siguen sus instrucciones, entonces se identifican los requisitos y se reproducen los comandos de compilación, arranque y verificación existentes.

- **CA-07 · RF-06:** Dada una configuración obligatoria ausente o inválida, cuando se intenta iniciar el backend, entonces no completa el arranque y muestra un diagnóstico sin revelar secretos.
- **CA-08 · RF-06:** Dada una base de datos inaccesible, cuando se intenta iniciar el backend, entonces no completa el arranque y muestra un diagnóstico sin revelar secretos.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Seguir los requisitos y comandos documentados desde un checkout. | Compilación y arranque correctos. |
| CA-02 | Iniciar contra la base de datos propia configurada. | Persistencia disponible y migraciones aplicadas. |
| CA-03 | Confirmar un dato ficticio y reiniciar sin eliminar el almacenamiento. | El dato confirmado se conserva. |
| CA-04 | Levantar la infraestructura propia mediante Compose. | Backend y base de datos arrancan. |
| CA-05 | Revisar configuración, documentación y archivos versionados. | Secretos externalizados, sin credenciales reales publicadas. |
| CA-06 | Reproducir las instrucciones del README. | Comandos y requisitos coinciden con los incorporados. |
| CA-07 | Intentar iniciar con configuración obligatoria ausente o inválida. | Arranque no completado y diagnóstico sin secretos. |
| CA-08 | Intentar iniciar sin acceso a la base de datos. | Arranque no completado y diagnóstico sin secretos. |

Estos criterios son objetivos pendientes de implementación y verificación; no se han ejecutado.

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

Ninguna pendiente de alcance o comportamiento para este setup.

Las decisiones técnicas se desarrollan en PLAN.md. La ubicación de la gestión de usuarios finales se resolverá en el issue de registro/autenticación, fuera del alcance de este setup.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el alcance está acordado, los flujos son coherentes, los puntos
mobile relevantes están cubiertos y cada requisito tiene criterios comprobables.
Resuelve las dudas y los marcadores pendientes. Mantén el diseño técnico en PLAN.md.
-->
