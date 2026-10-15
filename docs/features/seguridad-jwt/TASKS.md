# TASKS: Protección JWT de las APIs de Catálogo

**Referencias:** [SPEC aprobada](SPEC.md), [PLAN aprobado](PLAN.md).
**Estado:** Implementación y validación local completas; pendiente revisión remota de CI/Sonar tras publicación del usuario.
**Aprobación del PLAN:** confirmada por el usuario el 2026-10-14.
**Revisión de TASKS:** el usuario dio su visto bueno el 2026-10-14. Se comprobó el cumplimiento de IDs, objetivos, alcance, dependencias, referencias RF/CA y validaciones exigidos por el flujo, sin ampliar el desglose.

Cinco tareas para #13. Las pruebas se incorporan con el comportamiento que verifican. El contrato y las decisiones técnicas se mantienen en PLAN; las correcciones previas tienen evidencia en [TASKS del setup](../setup-backend/TASKS.md) y [TASKS de integración](../integracion-tecnica/TASKS.md).

## Tareas y dependencias

- [x] **T-01 · Configurar confianza RSA y validar JWT**
  - **Objetivo:** aceptar únicamente JWT propios compatibles con el contrato de Turnos.
  - **Alcance:** dependencias gestionadas por Boot, pública externa, decoder RS256 y validación de claims, perfiles y tiempos definidos en PLAN. Catálogo solo verifica; no posee la privada ni emite tokens. Documentar responsabilidades y contratos relevantes con Javadoc.
  - **Dependencias:** ninguna.
  - **Referencias:** RF-01, RF-03, RF-08; CA-01, CA-03, CA-08.
  - **Validación:** pares RSA ficticios y reloj controlado; firma o algoritmo incorrectos, emisor/audiencia inválidos, claims obligatorios ausentes, vencimiento y límites temporales. Probar pública ausente, ilegible, inválida o débil con diagnóstico seguro.

- [x] **T-02 · Proteger accesos HTTP y distinguir permisos**
  - **Objetivo:** autenticar y autorizar antes de ejecutar operaciones.
  - **Alcance:** cadena stateless, conversión de autoridades, separación usuario/servicio, health público mínimo y denegación del resto según PLAN. Errores 401/403 con ProblemDetail, CORS explícito y cerrado por defecto.
  - **Dependencias:** T-01.
  - **Referencias:** RF-01, RF-02, RF-03, RF-06, RF-07, RF-08; CA-01, CA-02, CA-03, CA-06, CA-07, CA-08.
  - **Validación:** cadena y decoder reales con JWT firmados; accesos válidos, 401/403, cruce de perfiles y preflight permitido/denegado. Verificar que el rechazo no consulta la sesión central. Usar un controlador exclusivamente de prueba para las futuras APIs de usuario.

- [x] **T-03 · Entregar el token técnico al servicio autorizado**
  - **Objetivo:** exponer el contrato interno confirmado sin alterar la adquisición de sesión de #6.
  - **Alcance:** controlador y DTO en infraestructura del slice `integration`, reutilizando `GetTechnicalTokenUseCase`. GET `/api/internal/catedra/token`, JSON mínimo, headers contra caché y 503 seguro cuando no existe sesión utilizable. Sin login ni renovación disparados por la solicitud.
  - **Dependencias:** T-02.
  - **Referencias:** RF-03, RF-04, RF-05, RF-06; CA-03, CA-04, CA-05, CA-06, CA-08.
  - **Validación:** controlador, caso de uso y almacenamiento reales; servicio autorizado con sesión disponible, pendiente, fallida, ausente o vencida. Comprobar 200/401/403/503, mismo token central, formato exacto, ausencia de otros secretos y no caché.

- [x] **T-04 · Integrar ejecución y documentar el contrato**
  - **Objetivo:** permitir ejecutar Catálogo y preparar el consumidor con la confianza acordada.
  - **Alcance:** configuración externa, `.env.example`, exclusión de claves locales, montaje de pública de solo lectura en Compose y documentación de ejecución local/Compose. Explicar generación y distribución de claves, contrato JWT/HTTP, CORS, transporte y rotación manual. Adaptar fixtures y expectativas de regresión sin modificar Turnos.
  - **Dependencias:** T-01, T-03.
  - **Referencias:** RF-03, RF-07, RF-08; CA-07, CA-08.
  - **Validación:** comprobar ambos modos con datos ficticios y ausencia de privada en Catálogo. Conservar health, arranque e integración existentes; las pruebas de configuración/MySQL deben mantener su causa prevista. Verificar que Compose falla si falta el archivo público.

- [ ] **T-05 · Verificar la entrega y registrar evidencias**
  - **Objetivo:** confirmar los criterios de aceptación y las regresiones antes de publicar.
  - **Alcance:** revisión de imports y responsabilidades, pruebas completas, JaCoCo, calidad y coherencia documental. Corregir hallazgos justificados dentro del alcance; registrar resultados y limitaciones reales.
  - **Dependencias:** T-01, T-02, T-03, T-04.
  - **Referencias:** RF-01 a RF-08; CA-01 a CA-08.
  - **Validación:** ejecutar `./mvnw -B -ntp clean verify` y `git diff --check`; revisar caminos relevantes sin cobertura y feedback de calidad disponible. Sonar CLI tiene la limitación Vortex 403 ya comprobada; no presentarlo como análisis exitoso. Actions/Sonar remotos se revisan después del push del usuario; la integración real con Turnos requiere su consumidor.

## Revisión del PR #17

Consulta remota del 2026-10-14: Actions `Pruebas y análisis` correcto; Quality Gate ERROR únicamente por `new_security_rating = 4` (D). S4502 en `SecurityConfiguration`, línea 92, aparece como VULNERABILITY CRITICAL por desactivar CSRF. La cobertura nueva es 97,1%, duplicación nueva 0% y las otras condiciones pasan. La clasificación remota prevalece sobre el reporte local vacío; este no garantiza ausencia de hallazgos de Cloud.

Se corrigieron los siete avisos adicionales de pruebas: dos S1612 mediante referencias a `PUBLIC_KEY_PATH::toString`, y cinco S5778 preparando `MockEnvironment` antes de la lambda para que solo `JwtSecurityProperties.from` pueda producir la excepción esperada. Se conservan escenarios, datos y aserciones. `./mvnw -o -B -ntp verify`, finalizado 20:26:47 -03:00: BUILD SUCCESS, 128 pruebas correctas.

El usuario autorizó probar CSRF predeterminado. La prueba reprodujo creación de sesión HTTP en un POST anónimo, incompatible con la política acordada; el POST bearer funcionaba sin CSRF ni sesión. Se restauró la configuración original, sin cambios finales en producción. El usuario confirmó conservarla y justificar el falso positivo: API exclusivamente bearer explícito, sin cookies de autenticación, HTTP Basic ni login de formulario. Si cambia ese contrato, revisar la decisión.

Se conservaron dos regresiones en `SecurityHttpTests`: POST bearer válido sin token CSRF devuelve 200 y POST anónimo devuelve 401; ambos sin sesión ni `Set-Cookie`, sin consultar el token central. El controlador adicional existe solo en pruebas. La configuración predeterminada hace fallar la aserción de sesión ausente, por lo que se verificó que la prueba detecta ese cambio.

SonarCloud confirmó el hallazgo `AaE8un9P7UwoqUY3bUsb` como FALSE_POSITIVE y guardó la justificación; Quality Gate OK y seguridad nueva A. GitHub muestra ambos checks SUCCESS. `./mvnw -o -B -ntp verify` finalizó 20:52:14 -03:00 con 130 pruebas correctas, sin fallos/errores/omisiones. SonarQube for IDE HTTP 200 para el archivo de pruebas modificado, sin hallazgos. Escaneo de secretos y `git diff --check` correctos. Pendiente publicar las correcciones de pruebas/evidencia y revisar su nuevo análisis remoto; T-05 conserva su casilla pendiente por esa comprobación.

## Evidencias de ejecución

Verificado el 2026-10-14 con datos, claves y cátedra ficticios. No se leyó `.env`, no se llamó a cátedra real ni se modificó Git. La publicación corresponde al usuario. T-05 conserva su casilla pendiente por la revisión remota; sus controles locales están completos.

| Tarea / criterios | Evidencia ejecutada | Resultado |
| --- | --- | --- |
| T-01 · CA-01/03/08 | `JwtSecurityPropertiesTests` (21 ejecuciones) y firma/decoder reales en `SecurityHttpTests`. PEM privado, inválido, débil, EC, ruta ausente/inválida y directorio; claims y tiempos malformados; otra firma, `none`, RS512 y confusión con HS512. | Rechazo seguro. Algoritmo RS256 fijo; pública local únicamente; tiempos de servicio limitados y tolerancia de 30 s comprobada dentro/fuera del límite. |
| T-02 · CA-01/02/03/06/07/08 | Cadena real: usuario admitido, roles múltiples separados por espacios, servicio/usuario cruzados, permisos/sujeto incorrectos y siguiente solicitud anónima. Spy del caso de consulta conserva comportamiento real y verifica ausencia de consulta ante rechazo. CORS permitido/denegado y preflight sin bearer. | 401/403 antes de leer la sesión; usuario sin cuentas locales; sin sesión HTTP/cookies. Health público mínimo en contexto completo. |
| T-03 · CA-03/04/05/06/08 | `SecurityHttpTests` (43 ejecuciones en total): controlador, caso de uso y almacenamiento reales, con sesión disponible, pendiente, fallida, sin token, vencida y sin vencimiento conocido. DTO redactado y logs capturados. | 200 con JSON mínimo y no caché; 503 seguro ante indisponibilidad; vencimiento central exacto conservado. ProblemDetail explícito con `about:blank`; sin causas ni secretos adicionales. |
| T-04 · CA-07/08 | Prueba aislada de Compose y JAR local: MySQL 8.4.8 temporal en tmpfs, servidor central ficticio y par RSA efímero. Inspección del montaje; intento con archivo ausente. Regresión de arranque y Javadoc. | Ambos modos: health 200, anónimo 401 y servicio autorizado 200. Solo pública montada, `RW=false`; falta de archivo rechazada sin crearlo. Sin secretos ficticios en logs. Contenedores/red/proceso temporales cerrados, sin tocar volúmenes de desarrollo ni eliminar imágenes. |
| T-05 · CA-01 a CA-08, controles locales | `./mvnw -o -B -ntp clean verify`, finalizado 11:15:14 -03:00: 128 pruebas, 0 fallos/errores/omitidas. Tras añadir la aserción de ausencia de login adicional, prueba de arranque específica finalizada 11:18:46: 1 prueba correcta. JaCoCo, imports de dominio, exclusión de claves, enlaces y `git diff --check`. Javadoc con `-private -Xdoclint:all,-missing`. | BUILD SUCCESS; líneas globales 95,9% (354/369), ramas 88,2% (194/220). Javadoc sin errores/advertencias. Dependencias gestionadas por Boot: Security 7.1.1 y Nimbus 10.9.1. Sin cambios a dominio ni tablas. |

**Calidad y límites:** el reintento solicitado de Sonar CLI confirmó conexión, pero Vortex devolvió 403 y no analizó archivos. No constituye un análisis exitoso. La consulta de alertas Dependabot del repositorio devolvió lista vacía; refleja la información remota disponible, no un escaneo de CVEs de esta rama sin publicar. Pendientes revisar Actions, importación JaCoCo y hallazgos/Quality Gate de Sonar tras el push. El consumidor y la prueba entre servicios reales corresponden a Turnos; aquí se verificó el contrato proveedor con firmas RSA reales ficticias.

**Revisión local posterior con SonarQube for IDE, 2026-10-14:** escaneo determinista previo de `src/main` y `src/test` sin secretos. API oficial del plugin local: HTTP 200 para solicitud de 38 archivos Java; devolvió seis hallazgos. Dos S1192 en `JwtSecurityProperties` (nombres de variables repetidos) y cuatro de pruebas: S5443 en archivo temporal de pública ficticia, S1845 en `TOKEN`/`token`, S2160 en `MutableClock` y S112 en el helper HTTP que declara `Exception`. Se atendieron posteriormente con autorización del usuario, según la evidencia siguiente. El acceso local del IDE funciona independientemente del rechazo Vortex.

### Corrección de los seis hallazgos locales

Autorizada y verificada el 2026-10-14. Se extrajeron constantes para los dos nombres de propiedades, sin cambiar valores ni diagnósticos. La pública ficticia se crea con nombre único bajo `target/test-keys`, excluido por `target/`, y se elimina al salir del JVM; la privada sigue únicamente en memoria. El helper central pasó a llamarse `fictionalToken`, actualizando sus usos. El helper HTTP declara `IOException` e `InterruptedException`.

La prueba de vencimiento usa ahora `Clock.fixed` y `Clock.offset`, sin la subclase `MutableClock`. Conserva el mismo almacenamiento y comprueba acceso en el segundo 59, rechazo exacto en el 60 y sesión invalidada al consultar nuevamente con el reloj inicial. No se generaron `equals`/`hashCode` sobre estado mutable, no se añadieron supresiones ni se debilitaron aserciones.

`./mvnw -o -B -ntp clean verify` finalizó 12:00:55 -03:00: BUILD SUCCESS, 128 pruebas, 0 fallos/errores/omitidas. JaCoCo mantuvo líneas 95,9% (354/369) y ramas 88,2% (194/220). Escaneo de secretos posterior sin hallazgos. Reanálisis IDE HTTP 200 de los 38 archivos Java solicitados: **cero hallazgos**, tras sincronización del archivo por el usuario; inicialmente el plugin conservaba el nombre anterior del helper. `git diff --check` correcto. Sin cambios de contrato, arquitectura o dependencias; Actions/Sonar Cloud siguen pendientes de publicación por el usuario.

### Revisión adicional de calidad

Revisados el 2026-10-14 los seis avisos posteriores. La instancia actual del IDE, puerto 64121, respondió HTTP 200 sin hallazgos para los 38 archivos Java solicitados, antes y después de los ajustes; coincide con el reporte del usuario. No se verificó la causa de la diferencia respecto del análisis anterior.

La revisión manual justificó cinco ajustes: `JwtTestTokens.signed` declara `JOSEException` y usa la privada sin cast redundante; `IntegrationBehaviorTests.runCycle` declara las tres excepciones concretas; `securityFilterChain` no declara `Exception`, pues `build()` en Spring Security 7.1.1 no la exige; y la validación de pública elimina una comprobación de nulo inalcanzable. Se conserva CSRF desactivado conforme al contrato bearer explícito, sin autenticación por cookies ni HTTP Basic. No se añadió una supresión ni se cambió el estado de un hotspot remoto.

`./mvnw -o -B -ntp verify` finalizó 20:12:15 -03:00: BUILD SUCCESS, 128 pruebas, 0 fallos/errores/omisiones. Escaneo de secretos correcto y `git diff --check` sin errores. Esta evidencia sustituye el estado pendiente de los seis avisos indicado más abajo. Actions/Sonar Cloud siguen pendientes del push del usuario.

### Inyección por constructor en pruebas

El usuario autorizó el 2026-10-14 sustituir los campos `@Autowired`. `CatalogApplicationTests` y `SecurityHttpTests` reciben sus colaboradores por constructor y los conservan en campos `final`. `@TestConstructor(autowireMode = ALL)` habilita esa resolución en Spring Test; no inyecta campos. El spy de `GetTechnicalTokenUseCase` se declara en la clase y también se recibe por constructor, manteniendo las verificaciones de ausencia de consulta. No se modificaron los controles `instanceof` de seguridad.

`./mvnw -o -B -ntp verify` finalizó 19:54:25 -03:00: BUILD SUCCESS, 128 pruebas correctas, sin fallos/errores/omisiones. Escaneo de secretos sin hallazgos y `git diff --check` correcto. Reanálisis IDE HTTP 200 de 38 archivos solicitados: seis avisos adicionales en líneas ajenas al ajuste de DI, pendientes de revisión: S112 en el firmante de prueba, la construcción de cadena Spring y una prueba de integración; S1905 por cast innecesario del firmante; S4502 sobre CSRF desactivado; S2589 por comprobación de pública nula. El resultado local actual no es cero hallazgos y no se atribuye el cambio de reporte a una causa no verificada. La política CSRF ya está fundamentada en PLAN y Javadoc; revisar el aviso sin alterar el contrato bearer por omisión.

**Coherencia del diseño:** las seis clases previstas se mantuvieron en infraestructura. El controlador adapta al puerto existente; `JwtContractValidator` implementa la estrategia de validación de Spring. Filtros, decoder y handlers reutilizan mecanismos del framework. El mixin ProblemDetail se aplica también al serializador del handler porque este actúa fuera del converter MVC. La decisión RS256 se conserva en PLAN, sin ADR separado ni nuevas capas. La aclaración posterior sobre configuración compartida fuera de slices está registrada en el contexto de agentes: seguridad ya cumple esa ubicación; la revisión de configuración anterior de #6 no alteró esta implementación.
