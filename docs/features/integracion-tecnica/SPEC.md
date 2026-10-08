# SPEC: Autenticación técnica y configuración de cátedra

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

Permitir que Catálogo acceda a la integración técnica asignada por cátedra, con una cuenta ya registrada manualmente. El destinatario es el servicio de Catálogo y quien configura su ejecución; no el usuario final de KMP.

Issue: [#6](https://github.com/prog2-perassiferrara/backend-catalogo/issues/6).
Fuentes: [enunciado](../../../../PROJECT_STATEMENT-v1.md), secciones 5 y 9; [contrato](../../../../INTEGRATION_REFERENCE-v2.md), secciones 2 a 5 y 13.

## Situación actual

<!-- Comportamiento actual relevante, limitación que queremos resolver y
comportamientos existentes que deben conservarse. No describas la arquitectura. -->

El setup está implementado y mergeado mediante el PR #5. El backend arranca con MySQL, dispone de health técnico y comprueba la configuración obligatoria local. Todavía no realiza autenticación técnica ni obtiene configuración de cátedra.

Se conservan las garantías del setup y las políticas de consulta acordadas en los CU. Registro/login de usuarios finales corresponde a Turnos; esta funcionalidad utiliza otra identidad.

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. -->

- **RF-01:** Utilizar la cuenta técnica registrada manualmente, sin crear otra cuenta automáticamente.
- **RF-02:** Autenticar esa cuenta mediante el contrato de login técnico y obtener el JWT y la configuración de integración. Permitir recuperar la configuración vigente mediante el contrato de integración actual.
- **RF-03:** Distinguir `PENDING`, `PROVISIONED`, `FAILED` y `REVOKED`. Habilitar la integración únicamente con `PROVISIONED`, JWT utilizable y configuración necesaria completa; los demás estados mantienen la integración no disponible.
- **RF-04:** Externalizar configuración y secretos, separarlos de usuarios finales y evitar su exposición a KMP, logs y archivos versionados.
- **RF-05:** Distinguir rechazo de autenticación, ausencia de integración, respuesta no utilizable y fallo de comunicación mediante estado HTTP y `code`, cuando exista, sin decidir por `detail`.
- **RF-06:** Al arrancar, intentar obtener acceso técnico. Ante fallos temporales o aprovisionamiento pendiente, reintentar automáticamente hasta un límite. Si se agota, mantener el proceso activo con integración no disponible, sin más reintentos automáticos hasta reiniciar. No habilitar operaciones dependientes del acceso mientras no se confirme RF-03. La configuración local y MySQL deben ser válidos.
- **Aclaración confirmada de RF-03/RF-06:** si el JWT técnico vence o cátedra lo rechaza con 401 después de obtenerlo, la integración queda no disponible y no se vuelve a hacer login automáticamente. La recuperación comienza tras reiniciar Catálogo.
- **RF-07:** Documentar preparación manual, configuración y verificación, delimitando qué información consume Catálogo y cómo se mantiene coherencia con Turnos.

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

- Registro/login de usuarios finales, acordados para Turnos.
- Protección de las APIs propias mediante JWT de usuario o entre servicios, cubierta por el issue #13.
- Descarga y aplicación de snapshot, incrementos y recuperación del catálogo, cubiertos por CU-01 a CU-03.
- Operaciones de disponibilidad, holds y reservas, responsabilidad de Turnos.
- Despliegue local del servicio central o registro automático de la cuenta técnica.

## Flujo de usuario

<!-- Cómo se inicia, qué hace el usuario y qué resultado obtiene.
Incluye pantallas afectadas, navegación y alternativas relevantes. -->

No hay pantallas afectadas: es un flujo de integración del backend.

1. Quien configura el proyecto registra una sola vez la cuenta técnica mediante una herramienta HTTP y proporciona la configuración externa necesaria.
2. Al arrancar, Catálogo inicia la autenticación técnica.
3. La cátedra responde con JWT, integración y estado de aprovisionamiento. Catálogo habilita el acceso únicamente si recibe `PROVISIONED` y toda la información necesaria; en caso contrario, mantiene la integración no disponible.
4. Cuando necesita recuperar la configuración vigente, utiliza el contrato autenticado de integración actual. El momento concreto se definirá en el PLAN.
5. Si ocurre un rechazo, respuesta incompleta o problema de red, Catálogo comunica un diagnóstico sin secretos y no declara disponible la integración afectada. El proceso permanece activo si la configuración local y MySQL son válidos.
6. Ante fallos recuperables, reintenta automáticamente hasta el límite acordado. Si obtiene acceso utilizable antes del límite, habilita la integración. Si agota los intentos, deja de reintentar y permanece activo con integración no disponible; un reinicio comienza un nuevo ciclo.

## Datos y reglas de negocio

<!-- Información que necesita el usuario, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de presentación. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

- Cuenta técnica y usuario final son identidades distintas. `groupId` identifica la integración técnica y no al usuario final.
- Login técnico: `username` de 1 a 254 caracteres y `password` de 4 a 100. Para vigencia larga, el contrato exige `rememberMe: true`.
- El login devuelve `id_token` e `integration`; la consulta de integración actual devuelve el objeto de integración sin envoltorio.
- Ambos backends usan la misma cuenta técnica y el mismo JWT de cátedra, según el contrato. La coordinación de ese JWT se resolverá en el PLAN, sin usar una base compartida.
- Catálogo necesita los datos REST y, para futuras sincronizaciones, conexión/credenciales Redis, namespace de lectura, servidores Kafka, topic de catálogo y consumer group asignados.
- Los topics de acciones y teléfono de turnos corresponden a Turnos; recibirlos en la configuración no autoriza a Catálogo a consumirlos.
- `redisPassword` solo se entrega con `PROVISIONED`. Por decisión del usuario, no se habilitan operaciones de negocio dependientes de cátedra antes de `PROVISIONED` y configuración completa. Las llamadas de autenticación y consulta de configuración siguen permitidas para intentar alcanzar ese estado.
- Reintentar no significa registrar otra cuenta. Los rechazos de credenciales o estados no recuperables no deben tratarse como éxito ni producir un bucle de intentos. La clasificación y los límites concretos se definen en el PLAN.
- Los rechazos HTTP 401/403 pueden carecer de `code`; los errores de infraestructura/servidor también pueden carecer de él.
- No registrar contraseñas, JWT, respuestas completas con secretos ni credenciales Redis. Los ejemplos y pruebas utilizan información ficticia.

## Comportamiento del backend y casos alternativos

<!-- Adapta la tabla usando MOBILE_GUIDELINES.md. Añade escenarios relevantes.
Marca No aplica con su motivo cuando corresponda. No presupongas soporte offline
ni conservación de todo el estado. Expresa resultados, no mecanismos técnicos. -->

| Situación | Comportamiento esperado |
| --- | --- |
| Carga o acción en curso | No declarar disponible el acceso todavía no confirmado. |
| Sin datos | No aceptar una respuesta sin JWT o sin configuración necesaria como integración utilizable. |
| Entrada inválida | Conservar el rechazo de configuración obligatoria inválida definido en el setup; validar datos de autenticación antes de enviarlos. |
| Error o espera excesiva | Diagnóstico sin secretos. Proceso activo con integración no disponible; reintentos automáticos limitados para fallos recuperables. |
| Sin conexión o conexión interrumpida | No declarar autenticación ni configuración recuperadas; mantener el proceso activo en las condiciones acordadas. |
| Reiniciar el backend | Intentar adquirir acceso técnico nuevamente al arrancar, con un nuevo ciclo limitado y sin registrar otra cuenta. |
| Aprovisionamiento no completo | Integración no disponible hasta recibir `PROVISIONED` y configuración completa. Consultas para recuperar configuración sujetas al ciclo limitado. |
| Límite de intentos agotado | Mantener el proceso activo sin más reintentos automáticos; el siguiente ciclo comienza tras reiniciar. |
| JWT técnico vencido o rechazado | Integración no disponible y backend activo; recuperar acceso solo tras reiniciar Catálogo. |

De la guía compartida se aplican conectividad, consistencia, recuperación y privacidad. Los aspectos de interfaz y dispositivo no corresponden a esta funcionalidad backend.

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de accesibilidad o rendimiento medibles, o una tecnología expresamente exigida.
Ejemplo: Usar Room puede ser una restricción; el diseño de entidades va en PLAN.md.
No conviertas una preferencia del agente en una restricción. -->

- Respetar el contrato v1 identificado dentro del anexo, sus rutas, propiedades y errores; no inferir versión por el nombre del archivo.
- Cada backend conserva su independencia; no distribuir secretos a través de KMP ni de una base de datos compartida.
- El JWT técnico no sirve como identidad del usuario final.
- Las direcciones y credenciales reales serán proporcionadas por cátedra; no utilizar los ejemplos como valores definitivos.
- Mantener garantías del setup. Esta SPEC no redefine el health técnico como estado de sincronización.
- Consultar decisiones pendientes y aprobar SPEC/PLAN antes de implementación autorizada.

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados. No uses Funciona correctamente.
Repite el formato según sea necesario. -->

- **CA-01 · RF-01:** Dada una cuenta técnica previamente registrada, cuando se prepara o reinicia Catálogo, entonces no se envía una solicitud de registro automático.
- **CA-02 · RF-02:** Dadas credenciales aceptadas, cuando cátedra devuelve un login correcto con información utilizable, entonces Catálogo dispone del JWT y configuración necesarios para el acceso técnico.
- **CA-03 · RF-02, RF-03:** Dado acceso técnico válido, cuando se recupera la integración actual, entonces se interpreta su objeto sin envoltorio y el aprovisionamiento recibido.
- **CA-04 · RF-03:** Dado un estado distinto de `PROVISIONED`, JWT no utilizable o configuración necesaria incompleta, cuando se evalúa acceso técnico, entonces la integración permanece no disponible y se distingue la condición recibida; no se habilitan operaciones de negocio dependientes de cátedra.
- **CA-05 · RF-04:** Dada una operación correcta o fallida, cuando se inspeccionan logs, respuestas a KMP y archivos versionados, entonces no contienen JWT ni secretos técnicos.
- **CA-06 · RF-05:** Dado un rechazo 401/403 sin `code`, integración ausente u otro error contractual, cuando se procesa la respuesta, entonces se distingue el fallo sin depender de `detail` ni declarar éxito.
- **CA-07 · RF-05, RF-06:** Dada una respuesta incompleta o fallo de comunicación, cuando se intenta adquirir acceso técnico, entonces no se utiliza información incompleta ni se declara disponible la integración; el proceso permanece activo si la configuración local y MySQL son válidos.
- **CA-08 · RF-07:** Dada la documentación del proyecto, cuando se prepara la integración, entonces pueden identificarse el registro manual único, la configuración requerida y las responsabilidades separadas de Catálogo y Turnos sin publicar secretos.
- **CA-09 · RF-06:** Dado un fallo temporal o aprovisionamiento pendiente, cuando un intento posterior obtiene acceso completo antes de agotar el límite, entonces la integración pasa a disponible sin reiniciar el backend.
- **CA-10 · RF-06:** Dado un ciclo que agota los intentos, cuando el proceso sigue activo, entonces la integración permanece no disponible y no se emiten más intentos automáticos; al reiniciar comienza un nuevo ciclo limitado.
- **CA-11 · RF-03, RF-06:** Dada una sesión técnica adquirida, cuando el JWT vence o cátedra lo rechaza con 401, entonces no se entrega como utilizable ni se inicia otro login automáticamente; el backend sigue activo y reiniciar Catálogo comienza la recuperación limitada.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Preparar/reiniciar con cuenta existente e inspeccionar solicitudes. | No se registra otra cuenta. |
| CA-02 | Login técnico exitoso con respuesta contractual ficticia. | JWT y configuración utilizables para integración. |
| CA-03 | Recuperar integración actual con acceso válido. | Objeto y aprovisionamiento interpretados correctamente. |
| CA-04 | Respuestas con los cuatro estados, JWT no utilizable y configuración incompleta. | Solo `PROVISIONED` con acceso completo habilita la integración. |
| CA-05 | Inspeccionar salidas en éxito y error y contenido versionado. | Secretos ficticios de prueba no expuestos. |
| CA-06 | Simular rechazo sin código, ausencia de integración y error contractual. | Fallos distinguidos por estado/código, sin interpretar texto. |
| CA-07 | Interrumpir conexión, agotar espera o devolver información incompleta. | Integración no disponible y proceso activo con configuración local y MySQL válidos. |
| CA-08 | Revisar instrucciones con valores ficticios. | Preparación reproducible y responsabilidades claras. |
| CA-09 | Fallo recuperable seguido de respuesta `PROVISIONED` completa antes del límite. | Integración disponible sin reiniciar. |
| CA-10 | Agotar el límite, observar el proceso y después reiniciar. | Proceso activo sin más intentos; reinicio inicia otro ciclo limitado. |
| CA-11 | Vencer un token adquirido o recibir 401 de cátedra, consultar acceso y después reiniciar. | Acceso no disponible sin renovación automática; reinicio inicia recuperación. |

Resultados de implementación y pruebas automatizadas con datos ficticios en [TASKS.md](TASKS.md), junto con la evidencia de login real aportada por el usuario.

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

Ninguna decisión funcional pendiente en esta revisión.

Las decisiones técnicas de esta funcionalidad se acordaron en [PLAN.md](PLAN.md). Los detalles de entrega protegida a Turnos corresponden a #13 y a su funcionalidad consumidora. La aprobación documental no autoriza implementación.

<!-- ANTES DE SOLICITAR APROBACIÓN
Comprueba que el alcance está acordado, los flujos son coherentes, los puntos
mobile relevantes están cubiertos y cada requisito tiene criterios comprobables.
Resuelve las dudas y los marcadores pendientes. Mantén el diseño técnico en PLAN.md.
-->
