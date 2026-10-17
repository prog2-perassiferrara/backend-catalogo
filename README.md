# Servicio de catálogo y sincronización

Base técnica del backend, con Java 25, Spring Boot 4.1.1, Maven 3.10.0,
MySQL 8.4.8, JPA/Hibernate y Flyway. Incluye health y pruebas de integración.
Incluye autenticación técnica ante cátedra y configuración de integración en memoria.
Valida JWT RS256 de Turnos y entrega el token técnico mediante un endpoint protegido.
La emisión de JWT y el registro/login de usuarios corresponden a Turnos; la sincronización del catálogo sigue pendiente.

## Requisitos

- Docker Engine disponible y Docker Compose.
- JDK 25 para ejecutar Java desde el IDE o usar el wrapper en la máquina.
- Acceso a Maven Central y Docker Hub en la primera ejecución.
- No hace falta instalar Maven: `mvnw` descarga la versión 3.10.0.
- En Windows, utilizar `mvnw.cmd` en lugar de `./mvnw`.

## Configuración local

Copiar `.env.example` a `.env` y completar las contraseñas y la configuración técnica:

```bash
cp .env.example .env
```

| Variable | Uso |
| --- | --- |
| `DB_NAME` | Base exclusiva de Catálogo; ejemplo `catalog`. |
| `DB_USER` | Usuario propio de esa base, distinto de root. |
| `DB_PASSWORD` | Contraseña obligatoria de ese usuario. |
| `DB_HOST` | `localhost` para Java local; Compose usa internamente `mysql`. |
| `DB_PORT` | Puerto local de MySQL; por defecto 3306. |
| `APP_PORT` | Puerto local del backend; por defecto 8080. |
| `MYSQL_ROOT_PASSWORD` | Contraseña obligatoria para inicializar MySQL, solo usada por su contenedor. |
| `CATEDRA_BASE_URL` | URL base REST de cátedra, HTTP/HTTPS, sin credenciales, query ni fragmento. |
| `CATEDRA_USERNAME` | Usuario de la cuenta técnica registrada manualmente (1 a 254 caracteres). |
| `CATEDRA_PASSWORD` | Contraseña técnica (4 a 100 caracteres). |
| `CATEDRA_GROUP_ID` | Identificador normalizado asignado a la cuenta, de 3 a 100 caracteres. |
| `JWT_PUBLIC_KEY_PATH` | Ruta del host al archivo PEM X.509 de pública RSA de Turnos, obligatoria, de al menos 2048 bits. Compose la monta como solo lectura. |
| `CORS_ALLOWED_ORIGINS` | Opcional: orígenes HTTP/HTTPS separados por comas, sin rutas, comodines ni credenciales. Vacío por defecto. |

`.env` no se versiona ni se incorpora al contexto de build Docker.
La cuenta técnica de cátedra es distinta de los usuarios finales. Sus credenciales
y JWT no se publican ni se entregan a KMP; utilizar únicamente datos ficticios del proyecto.
Si los puertos están ocupados, cambiarlos en `.env`.

Compose carga `.env` para interpolar variables. Spring Boot no lo carga automáticamente.

## Acceso técnico a cátedra

Registrar la cuenta técnica una sola vez mediante Postman, según el anexo de
integración. El backend no registra cuentas ni necesita cargar en `.env` el JWT
obtenido manualmente: hace login al arrancar con `rememberMe: true` y conserva
JWT/configuración solo en memoria.

La tarea de arranque trabaja en un hilo dedicado. Realiza hasta tres intentos
totales, separados por cinco segundos, con timeouts de conexión y lectura de
cinco segundos. Reintenta fallos de red, HTTP 500/503 y `PENDING`; durante
`PENDING` consulta configuración con el mismo JWT. Los errores terminales
detienen el ciclo. Solo `PROVISIONED` con acceso completo habilita la sesión.

Los logs indican `READY` o un diagnóstico sin secretos. Si cátedra falla o se
agotan los intentos, el proceso sigue activo, sin integración; la recuperación
requiere reiniciar Catálogo. Si el JWT vence según `exp`, cuando exista, o cátedra
lo rechaza con 401, se invalida el acceso sin renovar automáticamente. No se
comprueba la firma al leer `exp`; cátedra valida el token en sus APIs.

El JWT se comparte con Turnos mediante el endpoint protegido descrito abajo.
Redis, Kafka y la réplica de catálogo
se implementarán en sus propias funcionalidades; adquirir configuración no
demuestra que esas conexiones estén disponibles.

## Seguridad JWT y entrega a Turnos

Turnos firma JWT propios con **RS256** y conserva exclusivamente la privada.
Catálogo carga únicamente su pública, sin descubrimiento remoto, y valida firma,
emisor, audiencia, tiempos, perfil y permisos. No registra usuarios ni emite JWT.
El JWT de cátedra pertenece a otra confianza: no autentica llamadas a Catálogo.

### Preparar la pública local

Con OpenSSL, generar el par una vez en el entorno de Turnos. Ejemplo fuera de los repositorios:

```bash
mkdir -p "$HOME/.local/share/prog2/turnos-keys"
chmod 700 "$HOME/.local/share/prog2/turnos-keys"
```

Si ya existe un par, reutilizarlo. Para un entorno nuevo, sin sobrescribir la privada:

```bash
test ! -e "$HOME/.local/share/prog2/turnos-keys/private.pem" && (
  umask 077
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
    -out "$HOME/.local/share/prog2/turnos-keys/private.pem"
)
openssl pkey -in "$HOME/.local/share/prog2/turnos-keys/private.pem" -pubout \
  -out "$HOME/.local/share/prog2/turnos-keys/public.pem"
chmod 644 "$HOME/.local/share/prog2/turnos-keys/public.pem"
```

En `.env`, definir `JWT_PUBLIC_KEY_PATH` con la **ruta absoluta a `public.pem`**.
La pública debe ser legible por el usuario del contenedor (UID 10001); no es secreta.
La privada permanece restringida al emisor y nunca se monta en Catálogo.
Compose monta la pública en `/run/keys/public.pem`, sin crearla si falta;
el backend rechaza claves privadas, PEM inválidos y RSA menores a 2048 bits.
Los archivos locales `keys/`, `*.pem` y `*.key` están excluidos de Git y del build Docker.

### Contrato de los JWT propios

| Claim | Usuario | Servicio Turnos |
| --- | --- | --- |
| `iss` | `backend-turnos` | `backend-turnos` |
| `aud` | Incluye `catalog`; puede incluir `turnos` | Incluye `catalog` |
| `sub` | Identificador estable del usuario | `backend-turnos` |
| `token_use` | `user` | `service` |
| `auth` | Cadena de roles separados por un espacio; acceso general `ROLE_USER` | `ROLE_CATALOG_INTEGRATION` |
| `iat`, `exp` | Obligatorios; duración de usuario definida en Turnos | Obligatorios; máximo 300 segundos entre emisión y vencimiento |

Se admite una tolerancia de reloj de 30 segundos al validar los JWT propios;
`nbf` se valida si existe. Emisión futura fuera de la tolerancia, tiempos incoherentes,
claims obligatorios ausentes o autoridades malformadas invalidan el token.
Una cadena de autoridades vacía no concede permisos. Solo se acepta `RS256`.
Turnos genera un JWT de servicio nuevo por solicitud, sin login remoto ni cierre
de sesión del usuario por inactividad. La vigencia del JWT central es independiente.

### Endpoint interno

`GET /api/internal/catedra/token`, sin cuerpo ni parámetros, con cabecera
`Authorization: Bearer <jwt-de-servicio>`. Exige conjuntamente perfil `service`,
sujeto `backend-turnos` y autoridad `ROLE_CATALOG_INTEGRATION`.

| HTTP | Respuesta |
| --- | --- |
| 200 | `application/json`: `{"idToken":"<jwt-de-catedra>"}`. Solo para Turnos autorizado. |
| 401 | `application/problem+json`, código `AUTHENTICATION_REQUIRED`, `WWW-Authenticate: Bearer`. |
| 403 | `application/problem+json`, código `ACCESS_DENIED`. |
| 503 | `application/problem+json`, código `TECHNICAL_INTEGRATION_UNAVAILABLE`. No inicia login ni renovación. |

Los problemas incluyen `type: about:blank`, `title`, `status` y `code`, sin mensajes
internos. Estas respuestas incluyen `Cache-Control: no-store` y `Pragma: no-cache`.
Un usuario final no obtiene el token técnico, aunque tenga otras autoridades.
La autorización ocurre antes de consultar la sesión central.

Solo `GET /actuator/health` es público. `/api/**` requiere perfil `user` y
`ROLE_USER`, salvo la entrega interna con su regla específica; las demás rutas
se deniegan. Las futuras APIs incorporarán sus permisos propios.
La seguridad es stateless: no hay cookies de autenticación, sesión HTTP ni login
de formulario. CSRF está desactivado para bearer explícito en cabecera; revisar
esa decisión si se incorporan cookies. CORS no sustituye autenticación:
Android nativo y Turnos no necesitan habilitar un origen web. Para clientes web,
configurar orígenes explícitos; se permiten GET/OPTIONS y Authorization/Content-Type,
sin credenciales de navegador ni comodines.

HTTP se admite únicamente en desarrollo local y red privada de Compose.
Exponer estos contratos fuera de ese entorno requiere **HTTPS**; este proyecto
no instala certificados ni proxy en esta funcionalidad. Rotación inicial manual:
reemplazar el par en Turnos y la pública en Catálogo de forma coordinada y reiniciar.
Los JWT propios anteriores dejan de aceptarse; no cambia el JWT de cátedra.
La implementación y prueba del consumidor real corresponden a Turnos.

## Ejecutar todo con Compose

Una vez por máquina, crear la red compartida con Turnos. Si ya existe, omitirlo:

```bash
docker network create prog2-services
```

Los dos Compose declaran esta red como externa. Al hacer `up`, cada aplicación
se conecta automáticamente a ella y a su red propia con MySQL; las bases quedan
solo en las redes de sus respectivos proyectos. `down` no elimina la red externa.
Turnos utiliza `http://catalog:8080` entre contenedores; desde el host Catálogo
sigue disponible por el puerto publicado, por defecto `localhost:8080`.

```bash
docker compose up --build -d
docker compose ps
curl --fail http://localhost:8080/actuator/health
```

Usar el puerto configurado en `APP_PORT` si se modificó. La respuesta esperada
es HTTP 200 con `{"status":"UP"}`; indica que el servicio y su conexión a MySQL
están disponibles. **No indica que el catálogo esté sincronizado.**
No se exponen otros endpoints de Actuator ni detalles internos.

```bash
docker compose logs catalog
docker compose down
```

El volumen nombrado de MySQL conserva los datos al detener o recrear los contenedores.
Eliminar el volumen elimina esos datos. Cambiar contraseñas en `.env` no modifica
las cuentas de una base ya inicializada; esas cuentas deben actualizarse en MySQL.

## Ejecutar desde el IDE o con Maven

Levantar solo la base:

```bash
docker compose up -d mysql
```

Configurar en el IDE las variables `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `DB_HOST`,
`DB_PORT`, `APP_PORT`, las cuatro variables `CATEDRA_*`, `JWT_PUBLIC_KEY_PATH`
y, si corresponde, `CORS_ALLOWED_ORIGINS` de la tabla.
Ejecutar `com.prog2.catalog.CatalogApplication` con JDK 25.
No pasar `MYSQL_ROOT_PASSWORD` al backend. Si el servicio `catalog` de Compose ya
está activo, detenerlo para liberar el puerto o elegir otro `APP_PORT` para Java local.

En una terminal POSIX, se puede cargar el archivo local propio y ejecutar el wrapper:

```bash
set -a
. ./.env
set +a
unset MYSQL_ROOT_PASSWORD
./mvnw spring-boot:run
```

El backend no completa el arranque si falta configuración obligatoria, es inválida
o no puede conectar a la base. El diagnóstico identifica el problema sin mostrar contraseñas.

## Compilar y verificar

```bash
./mvnw verify
```

Compila, ejecuta las pruebas y empaqueta el JAR en `target/`. Las pruebas usan
Testcontainers y un MySQL 8.4.8 temporal: Docker debe estar accesible. No requieren
`.env` ni reutilizan la base de desarrollo. Se comprueba migración Flyway, datos
persistidos, health sin detalles y rechazo de arranque con configuración inválida
o base inaccesible. La integración utiliza un servidor HTTP ficticio: comprueba
login, estados de aprovisionamiento, errores, límites de reintento, interrupción
y vencimiento del JWT, sin conectarse a cátedra real.
Los resultados quedan en `target/surefire-reports/`.
Las pruebas de seguridad generan pares RSA efímeros y tokens ficticios; ejecutan
el decoder y los filtros reales, verificando firma, claims, tiempos, separación
de perfiles, CORS y respuestas 200/401/403/503. No requieren la privada de Turnos.

El volumen de Compose se verifica por separado, recreando sus contenedores y
comprobando que un dato ficticio confirmado permanece disponible; ver las
evidencias de [TASKS.md](docs/features/setup-backend/TASKS.md).

## CI y calidad con SonarQube Cloud

El [workflow](.github/workflows/ci.yml) se ejecuta al abrir, actualizar o reabrir
un PR hacia `main` y con cada push a `main`. Usa Java 25 y el Maven Wrapper:

1. `./mvnw -B -ntp clean verify`: compila, prueba y genera cobertura con JaCoCo.
2. `./mvnw -B -ntp sonar:sonar`: envía el análisis y la cobertura a SonarQube Cloud.

JaCoCo genera `target/site/jacoco/jacoco.xml` para Sonar y
`target/site/jacoco/index.html` para consulta local. Sonar importa la cobertura;
no ejecuta las pruebas. Testcontainers levanta MySQL temporal en el runner:
no se necesita Compose, `.env` ni credenciales de cátedra.

### Configuración inicial

1. En SonarQube Cloud: avatar > **My account > Access Tokens > Personal Tokens**.
   Generar un token con acceso de análisis al proyecto, nombre y caducidad;
   copiar el valor antes de salir, porque solo se muestra una vez.
2. En GitHub: **Settings > Secrets and variables > Actions > New repository secret**.
   Crear `SONAR_TOKEN` con ese valor. No guardarlo en archivos ni compartirlo por chat.
3. En el proyecto de Sonar: **Administration > Analysis Method**, desactivar
   **Automatic Analysis** antes de ejecutar el análisis por CI.

La organización y la clave pública del proyecto están en `pom.xml`. El secreto
se entrega solo al paso de análisis. En PR desde forks se ejecutan las pruebas,
pero se omite el análisis porque GitHub no entrega secretos a esos workflows.

### Consultar resultados

- **GitHub > Actions** y checks del PR: logs de compilación, pruebas y análisis.
- [Proyecto en SonarQube Cloud](https://sonarcloud.io/dashboard?id=prog2-perassiferrara_backend-catalogo):
  hallazgos, cobertura y Quality Gate del commit o PR analizado.
- **Quality Gate informativo:** puede aparecer rojo en el check de Sonar aunque
  Actions termine correctamente. `sonar.qualitygate.wait=false` evita hacer
  fallar el job por ese resultado. Los errores de compilación, pruebas o del
  scanner sí hacen fallar el job; no se ocultan con `continue-on-error`.

Un check rojo solo impide el merge si las reglas del repositorio lo exigen.
Este workflow no modifica esas reglas ni la aprobación del profesor. Tampoco
despliega el backend ni verifica el volumen persistente de Compose.

Documentación y evidencia de CI: [SPEC](docs/features/ci-calidad/SPEC.md),
[PLAN](docs/features/ci-calidad/PLAN.md) y [TASKS](docs/features/ci-calidad/TASKS.md).

## Persistencia y arquitectura

Flyway es el único responsable de crear o modificar el esquema. Hibernate usa
`ddl-auto=validate`; no actualiza tablas automáticamente. Las migraciones futuras
se ubican en `src/main/resources/db/migration/`, con nombres como
`V1__create_catalog_tables.sql`. Agregar una migración nueva para cada cambio;
no editar una migración ya aplicada.

No hay entidades ni tablas de negocio en este setup. La migración `setup_probe`
de `src/test/resources/` sirve únicamente para probar Flyway y no se incluye en
el JAR ni en la imagen del backend.

El paquete base es `com.prog2.catalog`. Cada funcionalidad tiene sus
paquetes `domain`, `application` e `infrastructure`; dominio, entidades JPA y DTO
permanecen separados. El slice `integration` separa modelos/puertos, casos de uso
y adaptadores HTTP/memoria/arranque. Su sesión inmutable se reemplaza atómicamente;
no agrega tablas ni migraciones.

Documentación: [SPEC](docs/features/setup-backend/SPEC.md),
[PLAN](docs/features/setup-backend/PLAN.md) y [TASKS](docs/features/setup-backend/TASKS.md).

Integración técnica: [SPEC](docs/features/integracion-tecnica/SPEC.md),
[PLAN](docs/features/integracion-tecnica/PLAN.md) y [TASKS](docs/features/integracion-tecnica/TASKS.md).

Seguridad JWT: [SPEC](docs/features/seguridad-jwt/SPEC.md),
[PLAN](docs/features/seguridad-jwt/PLAN.md) y [TASKS](docs/features/seguridad-jwt/TASKS.md).
