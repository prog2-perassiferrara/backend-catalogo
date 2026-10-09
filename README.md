# Servicio de catálogo y sincronización

Base técnica del backend, con Java 25, Spring Boot 4.1.1, Maven 3.10.0,
MySQL 8.4.8, JPA/Hibernate y Flyway. Incluye health y pruebas de integración.
Registro/autenticación y sincronización del catálogo todavía no están implementados.

## Requisitos

- Docker Engine disponible y Docker Compose.
- JDK 25 para ejecutar Java desde el IDE o usar el wrapper en la máquina.
- Acceso a Maven Central y Docker Hub en la primera ejecución.
- No hace falta instalar Maven: `mvnw` descarga la versión 3.10.0.
- En Windows, utilizar `mvnw.cmd` en lugar de `./mvnw`.

## Configuración local

Copiar `.env.example` a `.env` y completar las dos contraseñas de desarrollo:

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

`.env` no se versiona ni se incorpora al contexto de build Docker.
No usar credenciales reales ni las credenciales de cátedra.
Si los puertos están ocupados, cambiarlos en `.env`.

Compose carga `.env` para interpolar variables. Spring Boot no lo carga automáticamente.

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
`DB_PORT` y `APP_PORT`. Ejecutar `com.prog2.catalog.CatalogApplication` con JDK 25.
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
o base inaccesible. Los resultados quedan en `target/surefire-reports/`.

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

El paquete base es `com.prog2.catalog`. Cada funcionalidad futura tendrá sus
paquetes `domain`, `application` e `infrastructure`; dominio, entidades JPA y DTO
permanecen separados. El setup solo incorpora arranque y configuración técnica.

Documentación: [SPEC](docs/features/setup-backend/SPEC.md),
[PLAN](docs/features/setup-backend/PLAN.md) y [TASKS](docs/features/setup-backend/TASKS.md).
