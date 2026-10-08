# Servicio de catálogo y sincronización

Base técnica del backend, con Java 25, Spring Boot 4.1.1, Maven 3.10.0,
MySQL 8.4.8, JPA/Hibernate y Flyway. Incluye health y pruebas de integración.
Incluye autenticación técnica ante cátedra y configuración de integración en memoria.
Registro/autenticación de usuarios finales y sincronización del catálogo todavía no están implementados.

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

El JWT será compartido con Turnos mediante un endpoint protegido en el issue #13.
Ese endpoint todavía no está publicado. Redis, Kafka y la réplica de catálogo
se implementarán en sus propias funcionalidades; adquirir configuración no
demuestra que esas conexiones estén disponibles.

## Ejecutar todo con Compose

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
`DB_PORT`, `APP_PORT` y las cuatro variables `CATEDRA_*` de la tabla.
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

El volumen de Compose se verifica por separado, recreando sus contenedores y
comprobando que un dato ficticio confirmado permanece disponible; ver las
evidencias de [TASKS.md](docs/features/setup-backend/TASKS.md).

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
