# SPEC: Protección JWT de las APIs de Catálogo

**Estado:** Aprobada <!-- Borrador | En revisión | Aprobada -->
**Aprobación:** confirmada por el usuario el 2026-10-13. No autoriza implementación.

<!-- PARA LA PERSONA
SPEC.md define qué debe cumplirse; PLAN.md desarrolla cómo implementarlo;
TASKS.md organiza los pasos de ejecución. Esta funcionalidad es backend;
MOBILE_GUIDELINES.md corresponde exclusivamente a KMP.
-->

<!-- PARA EL AGENTE
- Leer instrucciones, contratos y documentos relacionados; verificar el estado real.
- Completar esta spec con la persona y consultar decisiones pendientes.
- No inventar requisitos ni exclusiones; distinguir acuerdos de propuestas.
- Mantener diseño de clases, mecanismos criptográficos y configuración en PLAN.
- Conservar las secciones y comentarios; mantener documentación proporcional.
- No aprobar ni implementar por inferencia. La aprobación requiere confirmación.
-->

## Qué construimos y para quién

<!-- Qué necesidad resolvemos, quién tiene esa necesidad y qué podrá hacer.
Describe el objetivo en lenguaje de producto. -->

Proteger las APIs de Catálogo consumidas por KMP y Turnos, diferenciando usuarios finales e identidad de servicio. Incorporar la entrega autorizada del JWT técnico de cátedra a Turnos para desbloquear su integración, sin exponerlo a KMP.

Issue: [#13](https://github.com/prog2-perassiferrara/backend-catalogo/issues/13).
Fuentes: [enunciado](../../../../PROJECT_STATEMENT-v1.md), secciones 3.2, 9 y 10; [anexo](../../../../INTEGRATION_REFERENCE-v2.md), secciones 2 y 4.
Relacionados: [Catálogo #6](../integracion-tecnica/SPEC.md), [propuesta de contrato interno](../integracion-tecnica/PLAN.md), [Turnos #4](../../../../backend-turnos/docs/features/integracion-tecnica/SPEC.md) y [Turnos #7](https://github.com/prog2-perassiferrara/backend-turnos/issues/7).

## Situación actual

<!-- Comportamiento actual relevante, limitación que queremos resolver y
comportamientos existentes que deben conservarse. No describas la arquitectura. -->

Catálogo ya autentica la cuenta técnica al arrancar y conserva su JWT/configuración solo en memoria. Puede consultar internamente el token utilizable, pero no tiene APIs funcionales propias ni entrega HTTP protegida a Turnos. Health es público y devuelve únicamente el estado técnico.

Turnos será propietario de las cuentas finales y emitirá el JWT que KMP usará ante ambos backends, según la decisión compartida. Catálogo no duplicará cuentas ni contraseñas. La entrega del token central y la confianza JWT entre servicios todavía no están implementadas.

## Dentro del alcance

<!-- Requisitos concretos, con identificadores estables para vincularlos a
criterios, decisiones del plan y tareas. -->

- **RF-01:** Exigir autenticación JWT en las APIs no declaradas públicas y validar firma, vigencia y pertenencia al contrato de confianza acordado con Turnos. Un token ausente, inválido, vencido o de una identidad no admitida no permite acceder.
- **RF-02:** Autorizar cada operación según la identidad autenticada y sus permisos. Un JWT válido no concede acceso a todas las operaciones. Preparar la validación de los JWT de usuario emitidos por Turnos sin incorporar los casos de búsqueda/agenda/estado pendientes.
- **RF-03:** Diferenciar JWT de usuario, credencial JWT entre servicios y JWT técnico de cátedra. No aceptar el token de cátedra como identidad para acceder a las APIs propias ni permitir que un usuario final obtenga secretos técnicos.
- **RF-04:** Entregar a Turnos el JWT técnico compartido solo cuando su identidad y permisos lo autoricen y la sesión de Catálogo sea utilizable. Exigir un JWT propio de servicio, exclusivo de Turnos, distinto del JWT de usuario y del JWT de cátedra; el JWT de usuario no autoriza esta operación.
- **RF-05:** Conservar la política de #6: ante sesión pendiente, fallida, vencida o no utilizable, no entregar el token ni iniciar otro login por la solicitud. La recuperación del login central requiere reiniciar Catálogo.
- **RF-06:** Distinguir falta de autenticación, falta de permisos e indisponibilidad de integración, sin divulgar tokens, credenciales ni detalles internos. La respuesta exitosa de entrega solo contiene el token necesario; impedir su almacenamiento en cachés HTTP.
- **RF-07:** Conservar únicamente el health técnico existente como público, con su respuesta mínima; las APIs funcionales y la entrega interna requieren autenticación y autorización.
- **RF-08:** Externalizar parámetros de confianza y secretos; configurar CORS conforme a los clientes permitidos cuando corresponda. Documentar contrato, protección del transporte, límites y preparación de ambos consumidores sin acceder a datos internos de Turnos.

## Fuera de alcance

<!-- Exclusiones acordadas, no deducidas por el agente. Si no hay exclusiones
adicionales, indícalo tras revisarlo con la persona. -->

- Registro/login, almacenamiento de usuarios y emisión de sus JWT en Catálogo: responsabilidad acordada de Turnos.
- Implementación de búsquedas, agendas, estado de sincronización, snapshot e incrementos: sus CU conservan alcance propio. Aquí se establece su protección, sin crear operaciones ficticias.
- Implementar el consumidor de Turnos, sus reservas o su ciclo de recuperación: se coordina su contrato, sin modificar ese repositorio.
- Renovación automática del JWT de cátedra o persistencia de la sesión técnica: se conservan las decisiones de #6.
- Roles administrativos y gestión de reservas ajenas: no forman parte de este issue.

## Flujo de usuario

<!-- Cómo se inicia, qué hace el usuario y qué resultado obtiene.
Incluye pantallas afectadas, navegación y alternativas relevantes. -->

1. KMP obtiene de Turnos un JWT de usuario y lo presenta ante una API protegida de Catálogo. Catálogo valida identidad, vigencia y permisos antes de ejecutar la operación implementada.
2. Turnos se autentica con su JWT propio de servicio para solicitar el token central. El mecanismo de obtención de esa credencial se concretará en PLAN.
3. Catálogo verifica autenticación y autorización antes de consultar su sesión técnica. Si está utilizable, entrega el JWT compartido; si no, informa indisponibilidad sin autenticar otra vez ante cátedra.
4. Un acceso no autorizado se rechaza sin ejecutar la operación ni divulgar el token central. Las solicitudes autorizadas de usuarios no adquieren por ello permisos de servicio.

## Datos y reglas de negocio

<!-- Información que necesita el usuario, campos obligatorios, validaciones,
límites y reglas como duplicados u orden de presentación. Describe significado
y comportamiento, sin diseñar tablas, DTO, DAO ni almacenamiento. -->

- La identidad y los permisos proceden del JWT validado; no se aceptan autoridades, usuario ni integración elegidos libremente en la solicitud.
- `groupId` identifica la integración técnica central, no un usuario final ni la identidad propia de Turnos.
- La entrega interna devuelve el mismo JWT de cátedra que conserva Catálogo. No entrega la contraseña central ni credenciales Redis.
- La autorización de entrega debe preceder a la consulta de disponibilidad del token, evitando revelar el estado interno a quien no tiene acceso.
- Token y permisos de usuario no sustituyen a la identidad autorizada para recibir secretos técnicos. Los nombres concretos de permisos y claims se acordarán con Turnos en PLAN.
- La disponibilidad de sesión técnica no demuestra vigencia de catálogo ni conectividad Kafka/Redis. Health conserva su significado técnico.
- Los contratos de consulta de CU-04/05/06 incorporarán la política de seguridad de este issue al implementarse; no se declaran terminados aquí.

## Comportamiento del backend y casos alternativos

<!-- Escenarios propios del servicio. MOBILE_GUIDELINES.md y los escenarios
de UI o ciclo de vida del dispositivo no aplican a este backend. -->

| Situación | Comportamiento esperado |
| --- | --- |
| JWT ausente, malformado, con firma inválida, vencido o ajeno a la confianza acordada | Rechazar la API protegida con HTTP 401, sin ejecutar su operación. |
| JWT admitido sin permisos para la operación | HTTP 403, sin efectos ni secretos. |
| Usuario final solicita el token central | No entregar el token; HTTP 403 si su JWT es válido y admitido. |
| Servicio autorizado y sesión técnica utilizable | Entregar el JWT central compartido y evitar almacenamiento en cachés HTTP. |
| Servicio autorizado y sesión técnica no disponible | HTTP 503, diagnóstico estable sin secretos y ningún login adicional. |
| Solicitante no autorizado y sesión técnica no disponible | Responder 401/403 según corresponda, sin revelar el estado de integración. |
| Reinicio de Catálogo | Conservar la política de #6: la entrega depende de adquirir nuevamente una sesión utilizable. |
| Consulta de health | Acceso público y respuesta mínima, sin estado funcional ni secretos. |
| Solicitud desde navegador | Aplicar la política CORS acordada; CORS no sustituye autenticación ni autorización. |

## Restricciones del pedido

<!-- Condiciones ya impuestas: compatibilidad, límites de alcance, requisitos
de accesibilidad o rendimiento medibles, o una tecnología expresamente exigida.
No conviertas una preferencia del agente en una restricción. -->

JWT obligatorio en la comunicación protegida entre servicios. Catálogo no accede a tablas de usuarios de Turnos. Conservar ejecución con Compose, protección de secretos y políticas aprobadas de #6. Priorizar herramientas vistas en clase y mantener la implementación mínima. El algoritmo, las claves, los claims, el aprovisionamiento de la credencial de servicio y los detalles HTTP se deciden en PLAN.

El enunciado, sección 9, permite propagar el JWT de usuario o utilizar uno técnico entre servicios. El usuario confirmó el 2026-10-13 un JWT propio exclusivo de Turnos para la entrega interna, separando ese permiso del acceso de usuarios. También confirmó health público y APIs protegidas. Son decisiones del proyecto; no se atribuye al enunciado la obligación de elegir JWT de servicio.

## Criterios de aceptación

<!-- Resultados observables que permitan decidir si se cumple cada requisito.
Incluye los casos alternativos acordados. No uses Funciona correctamente. -->

- **CA-01 · RF-01:** Una API protegida rechaza con 401 las solicitudes sin JWT, con token malformado, firma inválida, vencimiento o identidad fuera de la confianza acordada; no ejecuta la operación.
- **CA-02 · RF-01, RF-02:** Un JWT de usuario válido emitido por Turnos puede autenticarse en Catálogo; una operación que exige permisos adicionales devuelve 403 si faltan. No se requiere duplicar cuentas ni acceder a la base de Turnos.
- **CA-03 · RF-03, RF-04:** Solo la identidad de servicio Turnos, autenticada con su JWT propio y autorizada, obtiene el JWT de cátedra. Un JWT de usuario admitido recibe 403; un JWT central no autentica al solicitante en Catálogo.
- **CA-04 · RF-04, RF-06:** Con servicio autorizado y sesión utilizable, la entrega devuelve el JWT central conservado en memoria, sin contraseña ni configuración Redis, e impide caché HTTP.
- **CA-05 · RF-05, RF-06:** Con servicio autorizado y sesión pendiente, fallida, vencida o ausente, la entrega devuelve 503 sin secretos ni llamadas adicionales a registro/login central.
- **CA-06 · RF-02, RF-06:** Con solicitante no autorizado, la respuesta es 401/403 aunque la sesión central no esté disponible; no se consulta ni divulga el token ni su estado.
- **CA-07 · RF-07:** Health continúa público y mínimo; no se habilitan otras operaciones públicas por omisión.
- **CA-08 · RF-03, RF-06, RF-08:** Logs, errores y archivos versionados no contienen secretos reales. La documentación identifica confianza, permisos, transporte, CORS, contrato interno y preparación de consumidores; una política CORS permisiva no concede acceso a operaciones protegidas.

## Cómo se comprueba el comportamiento

<!-- Una fila por criterio: escenario y resultado que debemos comprobar.
La selección de tests, herramientas, comandos y evidencias se desarrolla en PLAN.md.
No marques los criterios como superados durante la especificación. -->

| Criterio | Condiciones y pasos | Resultado esperado |
| --- | --- | --- |
| CA-01 | Variar ausencia, formato, firma, vencimiento e identidad del JWT ante una operación protegida. | 401 y operación no ejecutada. |
| CA-02 | Presentar JWT de usuario compatible con Turnos, con y sin el permiso necesario. | Identidad aceptada; operación permitida o 403 según sus permisos. |
| CA-03 | Solicitar entrega como servicio autorizado, usuario final y cuenta central. | Solo el servicio autorizado recibe el token; ningún usuario lo obtiene. |
| CA-04 | Solicitar entrega autorizada con sesión utilizable. | Mismo JWT central, sin otros secretos y con protección frente a caché HTTP. |
| CA-05 | Variar estado, presencia y vencimiento de la sesión central. | 503, sin secretos ni autenticación central adicional. |
| CA-06 | Repetir solicitudes no autorizadas con sesión central disponible e indisponible. | Rechazo de seguridad sin acceder al caso de entrega ni revelar la sesión. |
| CA-07 | Consultar health y revisar accesos públicos. | Aplicar exclusivamente la política pública confirmada. |
| CA-08 | Revisar respuestas, logs, configuración, documentación y escenarios CORS. | Sin secretos publicados; contrato reproducible y autorización independiente de CORS. |

## Decisiones pendientes

<!-- Al resolverlas, actualiza las secciones afectadas. Escribe Ninguna cuando
no queden pendientes funcionales ni restricciones por decidir. -->

Ninguna decisión funcional pendiente. Las dos consultas y la SPEC completa fueron confirmadas por el usuario el 2026-10-13.

En PLAN se cerrarán con Turnos los permisos, algoritmo/claves/claims, adquisición y vigencia de la credencial de servicio, contrato HTTP de entrega, transporte y configuración CORS. La propuesta HTTP de #6 sigue siendo una referencia pendiente de cierre, no un contrato aprobado por este documento. La recuperación del consumidor pertenece a Turnos #4 y no se decide aquí.

<!-- ANTES DE SOLICITAR APROBACIÓN
Resolver pendientes, comprobar RF/CA y coherencia entre repositorios.
Mantener diseño técnico en PLAN; no trasladar aprobaciones anteriores.
-->
