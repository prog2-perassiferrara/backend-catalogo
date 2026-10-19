<!-- evaluacion:inicio -->
# Evaluación — prog2-perassiferrara/backend-catalogo

> Documento **generado automáticamente** por la cátedra de Programación II (UM): RepoSense + análisis de calidad de **7 dimensiones**. **No editar a mano**: se regenera en cada evaluación.
> Rama evaluada: `main`. Análisis **heurístico** (regex + métricas) + build: es una criba para orientar la revisión, **no una nota**. La **documentación** del repositorio no se evalúa.
> Generado: 2026-10-19.

## 1. Veredicto

| Aspecto | Resultado |
|---|---|
| Índice de calidad | **72/100** |
| Tipo de repositorio | `backend` |
| Build (`mvnw verify` / `gradlew check`) | OK |
| Código (LOC) | 4,569 |
| Archivos de código | 80 |
| Clases | 88 |
| Código dentro de clases (%POO) | 80% |
| Funciones fuera de clases | 0 |
| Duplicación (puerta) | 1.5% |
| Duplicación lógica | 1.5% |
| Duplicación de boilerplate | 1.4% |
| Cobertura | 94% |

> Este informe **describe** lo que el análisis midió con los criterios ya establecidos de la materia. No es una nota.

## 2. Semáforo de aprobación

Criterios de la cátedra (`CRITERIOS-CALIDAD.md`): **Aprobar** se juega con el perfil `estandar`; **destacar**, con el `exigente` (meta de excelencia).

| Semáforo | Resultado |
|---|---|
| Aprobar (`estandar`) | **no cumple** — 7/11 |
| Destacar (`exigente`) | **no cumple** — 6/11 |

**Para aprobar, falta cumplir:** SOLID

**Para destacar, falta cumplir:** SOLID; Duplicación

## 3. Autoría (RepoSense)

| Autor | Commits | Código (LOC) | Tests (LOC) | Docs (LOC) | Días activos | % del código |
|---|---:|---:|---:|---:|---:|---:|
| Valentino Perassi Ferrara | 16 | 2,593 | 2,525 | 3,887 | 11 | 100% |

> _El LOC de autoría lo calcula RepoSense sobre el historial y puede diferir del LOC de código del análisis (que mira el estado de `main`)._

## 4. Dimensiones evaluadas

Cada dimensión indica **qué mide**, **por qué importa** y **tu resultado**. Cuando el puntaje queda por debajo de la referencia, se agregan preguntas para **investigar la causa** (no hay recetas).

| Dimensión | Peso | Puntaje |
|:--|--:|--:|
| **D1** Diseño OO, SOLID y patrones (20%) | 20% | 3 |
| **D2** Arquitectura hexagonal — backend (15%) | 15% | 100 |
| **D3** Frontend KMP: MVVM y buenas prácticas (10%) | 10% | N/A |
| **D4** Integración y contrato (10%) | 10% | 40 |
| **D5** Seguridad y robustez (10%) | 10% | 100 |
| **D6** Pruebas y cobertura (20%) | 20% | 100 |
| **D7** Proceso y entrega (15%) | 15% | 100 |

### D1. Diseño OO, SOLID y patrones (20%)

- **Qué mide:** Métricas orientadas a objetos (clases, métodos, campos, cohesión, acoplamiento, herencia) y proxies de SOLID, más patrones de diseño.
- **Por qué importa:** El eje de la materia es el buen uso de la POO y su arquitectura: un diseño con clases claras y poco acopladas es más fácil de entender, probar y cambiar.
- **Tu resultado:** **3/100**
- **Para investigar la causa:**
    - ¿Se pensó el modelo de clases antes de codificar o se agrupó después?
    - ¿Qué clases concentran muchas responsabilidades a la vez?
    - ¿Los colaboradores llegan como parámetros o se crean por dentro?

### D2. Arquitectura hexagonal — backend (15%)

- **Qué mide:** Separación en capas (dominio, puertos de entrada/salida, adaptadores) y dirección de las dependencias del backend.
- **Por qué importa:** Aislar el dominio de la infraestructura permite cambiar la base de datos, el framework o el transporte sin tocar las reglas de negocio.
- **Tu resultado:** **100/100**

### D4. Integración y contrato (10%)

- **Qué mide:** Cumplimiento del contrato de integración de la cátedra (formato de errores, paginación, autenticación, endpoints esperados).
- **Por qué importa:** Los servicios deben poder integrarse entre sí y con la app sin sorpresas; el contrato es lo que hacen cumplir las pruebas de integración.
- **Tu resultado:** **40/100**
- **Para investigar la causa:**
    - ¿Qué endpoints difieren del contrato acordado?
    - ¿Los errores se devuelven con un formato uniforme?
    - ¿La autenticación es consistente entre servicios?

### D5. Seguridad y robustez (10%)

- **Qué mide:** Credenciales embebidas, consultas SQL por concatenación, y presencia de config de entorno; aislamiento de secretos.
- **Por qué importa:** Un secreto en el repositorio o una consulta construida por concatenación son riesgos concretos, no hipotéticos.
- **Tu resultado:** **100/100**

### D6. Pruebas y cobertura (20%)

- **Qué mide:** Cantidad y resultado de pruebas y cobertura de línea (JaCoCo/Kover, Surefire/Failsafe) cuando hay build.
- **Por qué importa:** Las pruebas son la red de seguridad para refactorizar e integrar; la cobertura acota las zonas sin verificar.
- **Tu resultado:** **100/100**

### D7. Proceso y entrega (15%)

- **Qué mide:** Artefactos verificables de proceso/entrega: `compose`, `.env.example`, CI (`.github/workflows/`), `CODEOWNERS` y wrapper de build (`mvnw`/`gradlew`). **No** puntúa documentación.
- **Por qué importa:** Un proyecto que compila, se levanta y se valida solo es más fácil de revisar y de integrar en equipo.
- **Tu resultado:** **100/100**

## 5. Señales detectadas

El análisis encontró las siguientes señales (archivo, sin número de línea a propósito):

**SRP/responsabilidad** (53)
- clase 'TransactionalCatalogSnapshotWriter' con baja cohesión (33%) — `src/main/java/com/prog2/catalog/synchronization/infrastructure/persistence/service/TransactionalCatalogSnapshotWriter.java`
- clase 'CatalogSyncStateEntity' grande (LOC=49, métodos=12, campos=9) — `src/main/java/com/prog2/catalog/synchronization/infrastructure/persistence/entity/CatalogSyncStateEntity.java`
- clase 'CatalogSyncStateEntity' con baja cohesión (18%) — `src/main/java/com/prog2/catalog/synchronization/infrastructure/persistence/entity/CatalogSyncStateEntity.java`
- clase 'ProfessionalCategoryEntity' con baja cohesión (21%) — `src/main/java/com/prog2/catalog/synchronization/infrastructure/persistence/entity/ProfessionalCategoryEntity.java`
- método 'ProfessionalCategoryEntity.ProfessionalCategoryEntity' con 6 parámetros — `src/main/java/com/prog2/catalog/synchronization/infrastructure/persistence/entity/ProfessionalCategoryEntity.java`
- clase 'WeeklyScheduleEntity' grande (LOC=62, métodos=11, campos=9) — `src/main/java/com/prog2/catalog/synchronization/infrastructure/persistence/entity/WeeklyScheduleEntity.java`
- clase 'WeeklyScheduleEntity' con baja cohesión (16%) — `src/main/java/com/prog2/catalog/synchronization/infrastructure/persistence/entity/WeeklyScheduleEntity.java`
- clase 'ProfessionalEntity' con baja cohesión (19%) — `src/main/java/com/prog2/catalog/synchronization/infrastructure/persistence/entity/ProfessionalEntity.java`
- método 'ProfessionalEntity.ProfessionalEntity' con 7 parámetros — `src/main/java/com/prog2/catalog/synchronization/infrastructure/persistence/entity/ProfessionalEntity.java`
- clase 'CatalogSnapshotResponseMapper' grande (LOC=57, métodos=10, campos=1) — `src/main/java/com/prog2/catalog/synchronization/infrastructure/client/mapper/CatalogSnapshotResponseMapper.java`
- clase 'WebClientCatalogSnapshotAdapter' grande (LOC=91, métodos=10, campos=5) — `src/main/java/com/prog2/catalog/synchronization/infrastructure/client/adapter/WebClientCatalogSnapshotAdapter.java`
- clase 'WebClientCatalogSnapshotAdapter' con baja cohesión (7%) — `src/main/java/com/prog2/catalog/synchronization/infrastructure/client/adapter/WebClientCatalogSnapshotAdapter.java`
- método 'WebClientCatalogSnapshotAdapter.fetch' de 34 líneas — `src/main/java/com/prog2/catalog/synchronization/infrastructure/client/adapter/WebClientCatalogSnapshotAdapter.java`
- método 'WebClientCatalogSnapshotAdapter.fetch' con complejidad 7 — `src/main/java/com/prog2/catalog/synchronization/infrastructure/client/adapter/WebClientCatalogSnapshotAdapter.java`
- método 'WebClientCatalogSnapshotAdapter.failure' de 30 líneas — `src/main/java/com/prog2/catalog/synchronization/infrastructure/client/adapter/WebClientCatalogSnapshotAdapter.java`
- método 'WebClientCatalogSnapshotAdapter.failure' con complejidad 9 — `src/main/java/com/prog2/catalog/synchronization/infrastructure/client/adapter/WebClientCatalogSnapshotAdapter.java`
- método 'WebClientCatalogSnapshotAdapter.httpFailure' con complejidad 6 — `src/main/java/com/prog2/catalog/synchronization/infrastructure/client/adapter/WebClientCatalogSnapshotAdapter.java`
- clase 'SynchronizeCatalogUseCaseImpl' grande (LOC=150, métodos=13, campos=14) — `src/main/java/com/prog2/catalog/synchronization/application/usecases/SynchronizeCatalogUseCaseImpl.java`
- clase 'SynchronizeCatalogUseCaseImpl' con baja cohesión (18%) — `src/main/java/com/prog2/catalog/synchronization/application/usecases/SynchronizeCatalogUseCaseImpl.java`
- clase 'WebClientCentralIntegrationAdapter' grande (LOC=134, métodos=17, campos=4) — `src/main/java/com/prog2/catalog/integration/infrastructure/client/adapter/WebClientCentralIntegrationAdapter.java`
- clase 'WebClientCentralIntegrationAdapter' con baja cohesión (34%) — `src/main/java/com/prog2/catalog/integration/infrastructure/client/adapter/WebClientCentralIntegrationAdapter.java`
- método 'WebClientCentralIntegrationAdapter.IntegrationSession' de 50 líneas — `src/main/java/com/prog2/catalog/integration/infrastructure/client/adapter/WebClientCentralIntegrationAdapter.java`
- método 'WebClientCentralIntegrationAdapter.IntegrationSession' con complejidad 12 — `src/main/java/com/prog2/catalog/integration/infrastructure/client/adapter/WebClientCentralIntegrationAdapter.java`
- método 'WebClientCentralIntegrationAdapter.exchange' con complejidad 7 — `src/main/java/com/prog2/catalog/integration/infrastructure/client/adapter/WebClientCentralIntegrationAdapter.java`
- método 'WebClientCentralIntegrationAdapter.defaultIfEmpty' con complejidad 6 — `src/main/java/com/prog2/catalog/integration/infrastructure/client/adapter/WebClientCentralIntegrationAdapter.java`
- método 'WebClientCentralIntegrationAdapter.map' con complejidad 6 — `src/main/java/com/prog2/catalog/integration/infrastructure/client/adapter/WebClientCentralIntegrationAdapter.java`
- método 'InitializeIntegrationUseCaseImpl.initialize' de 39 líneas — `src/main/java/com/prog2/catalog/integration/application/usecases/InitializeIntegrationUseCaseImpl.java`
- método 'InitializeIntegrationUseCaseImpl.initialize' con complejidad 9 — `src/main/java/com/prog2/catalog/integration/application/usecases/InitializeIntegrationUseCaseImpl.java`
- clase 'CatalogStartupRunner' grande (LOC=79, métodos=4, campos=8) — `src/main/java/com/prog2/catalog/infrastructure/startup/CatalogStartupRunner.java`
- clase 'CatalogStartupRunner' con baja cohesión (33%) — `src/main/java/com/prog2/catalog/infrastructure/startup/CatalogStartupRunner.java`
- método 'CatalogStartupRunner.initializeTechnicalAccess' con complejidad 7 — `src/main/java/com/prog2/catalog/infrastructure/startup/CatalogStartupRunner.java`
- método 'JwtSecurityProperties.JwtSecurityProperties' de 58 líneas — `src/main/java/com/prog2/catalog/infrastructure/security/config/JwtSecurityProperties.java`
- método 'JwtSecurityProperties.JwtSecurityProperties' con complejidad 8 — `src/main/java/com/prog2/catalog/infrastructure/security/config/JwtSecurityProperties.java`
- método 'IntegrationProperties.IntegrationProperties' de 55 líneas — `src/main/java/com/prog2/catalog/infrastructure/config/IntegrationProperties.java`
- método 'IntegrationProperties.IntegrationProperties' con complejidad 7 — `src/main/java/com/prog2/catalog/infrastructure/config/IntegrationProperties.java`
- clase 'CatalogApplicationTests' grande (LOC=277, métodos=27, campos=6) — `src/test/java/com/prog2/catalog/CatalogApplicationTests.java`
- clase 'CatalogApplicationTests' con baja cohesión (16%) — `src/test/java/com/prog2/catalog/CatalogApplicationTests.java`
- clase 'SynchronizeCatalogUseCaseTests' grande (LOC=227, métodos=21, campos=5) — `src/test/java/com/prog2/catalog/synchronization/SynchronizeCatalogUseCaseTests.java`
- método 'CatalogSnapshotClientTests.interruptedOrStalledBodyAfterSuccessHeadersRemainsATemporaryDownloadFailure' de 31 líneas — `src/test/java/com/prog2/catalog/synchronization/CatalogSnapshotClientTests.java`
- clase 'CatalogSnapshotPersistenceTests' grande (LOC=246, métodos=23, campos=7) — `src/test/java/com/prog2/catalog/synchronization/CatalogSnapshotPersistenceTests.java`
- clase 'CatalogSnapshotPersistenceTests' con baja cohesión (28%) — `src/test/java/com/prog2/catalog/synchronization/CatalogSnapshotPersistenceTests.java`
- método 'CatalogSnapshotPersistenceTests.VALUES' de 41 líneas — `src/test/java/com/prog2/catalog/synchronization/CatalogSnapshotPersistenceTests.java`
- método 'CatalogSnapshotPersistenceTests.VALUES' con 5 parámetros — `src/test/java/com/prog2/catalog/synchronization/CatalogSnapshotPersistenceTests.java`
- método 'CatalogSnapshotPersistenceTests.readersSeeCompleteCopiesAndRepeatableReadKeepsTheVersionAcrossCommit' de 37 líneas — `src/test/java/com/prog2/catalog/synchronization/CatalogSnapshotPersistenceTests.java`
- clase 'JwtTestTokens' con baja cohesión (4%) — `src/test/java/com/prog2/catalog/security/JwtTestTokens.java`
- clase 'SecurityHttpTests' grande (LOC=258, métodos=25, campos=5) — `src/test/java/com/prog2/catalog/security/SecurityHttpTests.java`
- clase 'SecurityHttpTests' con baja cohesión (28%) — `src/test/java/com/prog2/catalog/security/SecurityHttpTests.java`
- método 'SecurityHttpTests.returnsUnavailableWithoutRenewalForUnusableCentralSession' con complejidad 7 — `src/test/java/com/prog2/catalog/security/SecurityHttpTests.java`
- clase 'IntegrationBehaviorTests' grande (LOC=244, métodos=22, campos=5) — `src/test/java/com/prog2/catalog/integration/IntegrationBehaviorTests.java`
- clase 'TechnicalIntegrationTestServer' grande (LOC=108, métodos=18, campos=9) — `src/test/java/com/prog2/catalog/integration/TechnicalIntegrationTestServer.java`
- clase 'TechnicalIntegrationTestServer' con baja cohesión (8%) — `src/test/java/com/prog2/catalog/integration/TechnicalIntegrationTestServer.java`
- método 'TechnicalIntegrationTestServer.handle' de 30 líneas — `src/test/java/com/prog2/catalog/integration/TechnicalIntegrationTestServer.java`
- método 'TechnicalIntegrationTestServer.handle' con complejidad 8 — `src/test/java/com/prog2/catalog/integration/TechnicalIntegrationTestServer.java`

**DIP/concretos** (61)
- clase 'TransactionalCatalogSnapshotWriter' muy acoplada (CBO=26) — `src/main/java/com/prog2/catalog/synchronization/infrastructure/persistence/service/TransactionalCatalogSnapshotWriter.java`
- clase 'CatalogSnapshotPersistenceMapper' muy acoplada (CBO=9) — `src/main/java/com/prog2/catalog/synchronization/infrastructure/persistence/mapper/CatalogSnapshotPersistenceMapper.java`
- clase 'CatalogSnapshotPersistenceMapper' instancia 4 concretos (CBO=9) — `src/main/java/com/prog2/catalog/synchronization/infrastructure/persistence/mapper/CatalogSnapshotPersistenceMapper.java`
- clase 'JpaCatalogSnapshotAdapter' muy acoplada (CBO=21) — `src/main/java/com/prog2/catalog/synchronization/infrastructure/persistence/adapter/JpaCatalogSnapshotAdapter.java`
- clase 'JpaCatalogSnapshotAdapter' instancia 3 concretos (CBO=21) — `src/main/java/com/prog2/catalog/synchronization/infrastructure/persistence/adapter/JpaCatalogSnapshotAdapter.java`
- clase 'CatalogSyncStateEntity' muy acoplada (CBO=9) — `src/main/java/com/prog2/catalog/synchronization/infrastructure/persistence/entity/CatalogSyncStateEntity.java`
- clase 'CatalogSnapshotResponseMapper' muy acoplada (CBO=31) — `src/main/java/com/prog2/catalog/synchronization/infrastructure/client/mapper/CatalogSnapshotResponseMapper.java`
- clase 'CatalogSnapshotResponseMapper' instancia 6 concretos (CBO=31) — `src/main/java/com/prog2/catalog/synchronization/infrastructure/client/mapper/CatalogSnapshotResponseMapper.java`
- clase 'WebClientCatalogSnapshotAdapter' muy acoplada (CBO=37) — `src/main/java/com/prog2/catalog/synchronization/infrastructure/client/adapter/WebClientCatalogSnapshotAdapter.java`
- clase 'WebClientCatalogSnapshotAdapter' instancia 5 concretos (CBO=37) — `src/main/java/com/prog2/catalog/synchronization/infrastructure/client/adapter/WebClientCatalogSnapshotAdapter.java`
- clase 'SynchronizationConfiguration' muy acoplada (CBO=11) — `src/main/java/com/prog2/catalog/synchronization/infrastructure/config/SynchronizationConfiguration.java`
- clase 'SynchronizationConfiguration' instancia 3 concretos (CBO=11) — `src/main/java/com/prog2/catalog/synchronization/infrastructure/config/SynchronizationConfiguration.java`
- clase 'IntegrationTechnicalAccessAdapter' muy acoplada (CBO=8) — `src/main/java/com/prog2/catalog/synchronization/infrastructure/integration/adapter/IntegrationTechnicalAccessAdapter.java`
- clase 'SynchronizeCatalogUseCaseImpl' muy acoplada (CBO=29) — `src/main/java/com/prog2/catalog/synchronization/application/usecases/SynchronizeCatalogUseCaseImpl.java`
- clase 'SynchronizeCatalogUseCaseImpl' instancia 6 concretos (CBO=29) — `src/main/java/com/prog2/catalog/synchronization/application/usecases/SynchronizeCatalogUseCaseImpl.java`
- clase 'SnapshotFailureKind' muy acoplada (CBO=9) — `src/main/java/com/prog2/catalog/synchronization/domain/enums/SnapshotFailureKind.java`
- clase 'SynchronizationPhase' muy acoplada (CBO=9) — `src/main/java/com/prog2/catalog/synchronization/domain/enums/SynchronizationPhase.java`
- clase 'CatalogSnapshotRepository' muy acoplada (CBO=10) — `src/main/java/com/prog2/catalog/synchronization/domain/ports/out/CatalogSnapshotRepository.java`
- clase 'CatalogSnapshot' muy acoplada (CBO=11) — `src/main/java/com/prog2/catalog/synchronization/domain/model/CatalogSnapshot.java`
- clase 'WebClientCentralIntegrationAdapter' muy acoplada (CBO=52) — `src/main/java/com/prog2/catalog/integration/infrastructure/client/adapter/WebClientCentralIntegrationAdapter.java`
- clase 'WebClientCentralIntegrationAdapter' instancia 9 concretos (CBO=52) — `src/main/java/com/prog2/catalog/integration/infrastructure/client/adapter/WebClientCentralIntegrationAdapter.java`
- clase 'IntegrationTokenController' muy acoplada (CBO=19) — `src/main/java/com/prog2/catalog/integration/infrastructure/web/controller/IntegrationTokenController.java`
- clase 'InitializeIntegrationUseCaseImpl' muy acoplada (CBO=24) — `src/main/java/com/prog2/catalog/integration/application/usecases/InitializeIntegrationUseCaseImpl.java`
- clase 'InitializeIntegrationUseCaseImpl' instancia 7 concretos (CBO=24) — `src/main/java/com/prog2/catalog/integration/application/usecases/InitializeIntegrationUseCaseImpl.java`
- clase 'IntegrationSession' muy acoplada (CBO=11) — `src/main/java/com/prog2/catalog/integration/domain/model/IntegrationSession.java`
- clase 'CatalogStartupRunner' muy acoplada (CBO=22) — `src/main/java/com/prog2/catalog/infrastructure/startup/CatalogStartupRunner.java`
- clase 'JwtSecurityProperties' muy acoplada (CBO=19) — `src/main/java/com/prog2/catalog/infrastructure/security/config/JwtSecurityProperties.java`
- clase 'SecurityConfiguration' muy acoplada (CBO=50) — `src/main/java/com/prog2/catalog/infrastructure/security/config/SecurityConfiguration.java`
- clase 'SecurityConfiguration' instancia 11 concretos (CBO=50) — `src/main/java/com/prog2/catalog/infrastructure/security/config/SecurityConfiguration.java`
- clase 'JwtContractValidator' muy acoplada (CBO=14) — `src/main/java/com/prog2/catalog/infrastructure/security/jwt/JwtContractValidator.java`
- clase 'SecurityProblemHandler' muy acoplada (CBO=25) — `src/main/java/com/prog2/catalog/infrastructure/security/web/SecurityProblemHandler.java`
- clase 'DatabaseConfiguration' muy acoplada (CBO=9) — `src/main/java/com/prog2/catalog/infrastructure/config/DatabaseConfiguration.java`
- clase 'CatalogConfiguration' muy acoplada (CBO=12) — `src/main/java/com/prog2/catalog/infrastructure/config/CatalogConfiguration.java`
- clase 'IntegrationProperties' muy acoplada (CBO=15) — `src/main/java/com/prog2/catalog/infrastructure/config/IntegrationProperties.java`
- clase 'IntegrationProperties' instancia 3 concretos (CBO=15) — `src/main/java/com/prog2/catalog/infrastructure/config/IntegrationProperties.java`
- clase 'CatalogApplicationTests' muy acoplada (CBO=69) — `src/test/java/com/prog2/catalog/CatalogApplicationTests.java`
- clase 'CatalogApplicationTests' instancia 9 concretos (CBO=69) — `src/test/java/com/prog2/catalog/CatalogApplicationTests.java`
- clase 'SynchronizeCatalogUseCaseTests' muy acoplada (CBO=35) — `src/test/java/com/prog2/catalog/synchronization/SynchronizeCatalogUseCaseTests.java`
- clase 'SynchronizeCatalogUseCaseTests' instancia 6 concretos (CBO=35) — `src/test/java/com/prog2/catalog/synchronization/SynchronizeCatalogUseCaseTests.java`
- clase 'CatalogSnapshotClientTests' muy acoplada (CBO=23) — `src/test/java/com/prog2/catalog/synchronization/CatalogSnapshotClientTests.java`
- clase 'CatalogSnapshotClientTests' instancia 13 concretos (CBO=23) — `src/test/java/com/prog2/catalog/synchronization/CatalogSnapshotClientTests.java`
- clase 'CatalogSnapshotValidationTests' muy acoplada (CBO=12) — `src/test/java/com/prog2/catalog/synchronization/CatalogSnapshotValidationTests.java`
- clase 'CatalogSnapshotValidationTests' instancia 5 concretos (CBO=12) — `src/test/java/com/prog2/catalog/synchronization/CatalogSnapshotValidationTests.java`
- clase 'SnapshotFixtures' muy acoplada (CBO=9) — `src/test/java/com/prog2/catalog/synchronization/SnapshotFixtures.java`
- clase 'SnapshotFixtures' instancia 4 concretos (CBO=9) — `src/test/java/com/prog2/catalog/synchronization/SnapshotFixtures.java`
- clase 'CatalogSnapshotPersistenceTests' muy acoplada (CBO=86) — `src/test/java/com/prog2/catalog/synchronization/CatalogSnapshotPersistenceTests.java`
- clase 'CatalogSnapshotPersistenceTests' instancia 10 concretos (CBO=86) — `src/test/java/com/prog2/catalog/synchronization/CatalogSnapshotPersistenceTests.java`
- clase 'TechnicalSnapshotAccessTests' muy acoplada (CBO=31) — `src/test/java/com/prog2/catalog/synchronization/TechnicalSnapshotAccessTests.java`
- clase 'TechnicalSnapshotAccessTests' instancia 15 concretos (CBO=31) — `src/test/java/com/prog2/catalog/synchronization/TechnicalSnapshotAccessTests.java`
- clase 'JwtTestTokens' muy acoplada (CBO=28) — `src/test/java/com/prog2/catalog/security/JwtTestTokens.java`
- clase 'JwtTestTokens' instancia 6 concretos (CBO=28) — `src/test/java/com/prog2/catalog/security/JwtTestTokens.java`
- clase 'SecurityHttpTests' muy acoplada (CBO=70) — `src/test/java/com/prog2/catalog/security/SecurityHttpTests.java`
- clase 'SecurityHttpTests' instancia 13 concretos (CBO=70) — `src/test/java/com/prog2/catalog/security/SecurityHttpTests.java`
- clase 'JwtSecurityPropertiesTests' muy acoplada (CBO=15) — `src/test/java/com/prog2/catalog/security/JwtSecurityPropertiesTests.java`
- clase 'IntegrationBehaviorTests' muy acoplada (CBO=56) — `src/test/java/com/prog2/catalog/integration/IntegrationBehaviorTests.java`
- clase 'IntegrationBehaviorTests' instancia 14 concretos (CBO=56) — `src/test/java/com/prog2/catalog/integration/IntegrationBehaviorTests.java`
- clase 'IntegrationPropertiesTests' muy acoplada (CBO=11) — `src/test/java/com/prog2/catalog/integration/IntegrationPropertiesTests.java`
- clase 'TechnicalIntegrationTestServer' muy acoplada (CBO=29) — `src/test/java/com/prog2/catalog/integration/TechnicalIntegrationTestServer.java`
- clase 'TechnicalIntegrationTestServer' instancia 8 concretos (CBO=29) — `src/test/java/com/prog2/catalog/integration/TechnicalIntegrationTestServer.java`
- clase 'CatalogStartupRunnerTests' muy acoplada (CBO=28) — `src/test/java/com/prog2/catalog/infrastructure/startup/CatalogStartupRunnerTests.java`
- clase 'CatalogStartupRunnerTests' instancia 13 concretos (CBO=28) — `src/test/java/com/prog2/catalog/infrastructure/startup/CatalogStartupRunnerTests.java`

**OCP/type-switch** (2)
- clase 'SecurityConfiguration' con 2 type-checks — `src/main/java/com/prog2/catalog/infrastructure/security/config/SecurityConfiguration.java`
- clase 'JwtContractValidator' con 2 type-checks — `src/main/java/com/prog2/catalog/infrastructure/security/jwt/JwtContractValidator.java`

**duplicación** (6)
- duplicación lógica: 1.5% · boilerplate: 1.4%
- bloque de 8 líneas repetido 5 veces en src/test/java/com/prog2/catalog/synchronization/CatalogSnapshotValidationTests.java:47, src/test/java/com/prog2/catalog/synchronization/CatalogSnapshotValidationTests.java:48, src/test/java/com/prog2/catalog/synchronization/CatalogSnapshotValidationTests.java:49 — `src/test/java/com/prog2/catalog/synchronization/CatalogSnapshotValidationTests.java`
- bloque de 8 líneas repetido 3 veces en src/main/java/com/prog2/catalog/synchronization/infrastructure/persistence/entity/ProfessionalCategoryEntity.java:56, src/main/java/com/prog2/catalog/synchronization/infrastructure/persistence/entity/WeeklyScheduleEntity.java:88, src/main/java/com/prog2/catalog/synchronization/infrastructure/persistence/entity/ProfessionalEntity.java:64 — `src/main/java/com/prog2/catalog/synchronization/infrastructure/persistence/entity/ProfessionalCategoryEntity.java`
- bloque de 8 líneas repetido 3 veces en src/main/java/com/prog2/catalog/synchronization/infrastructure/persistence/entity/ProfessionalCategoryEntity.java:57, src/main/java/com/prog2/catalog/synchronization/infrastructure/persistence/entity/WeeklyScheduleEntity.java:89, src/main/java/com/prog2/catalog/synchronization/infrastructure/persistence/entity/ProfessionalEntity.java:65 — `src/main/java/com/prog2/catalog/synchronization/infrastructure/persistence/entity/ProfessionalCategoryEntity.java`
- bloque de 8 líneas repetido 3 veces en src/main/java/com/prog2/catalog/synchronization/infrastructure/persistence/entity/ProfessionalCategoryEntity.java:54, src/main/java/com/prog2/catalog/synchronization/infrastructure/persistence/entity/WeeklyScheduleEntity.java:86, src/main/java/com/prog2/catalog/synchronization/infrastructure/persistence/entity/ProfessionalEntity.java:62 — `src/main/java/com/prog2/catalog/synchronization/infrastructure/persistence/entity/ProfessionalCategoryEntity.java`
- bloque de 8 líneas repetido 3 veces en src/main/java/com/prog2/catalog/synchronization/infrastructure/persistence/entity/ProfessionalCategoryEntity.java:58, src/main/java/com/prog2/catalog/synchronization/infrastructure/persistence/entity/WeeklyScheduleEntity.java:90, src/main/java/com/prog2/catalog/synchronization/infrastructure/persistence/entity/ProfessionalEntity.java:66 — `src/main/java/com/prog2/catalog/synchronization/infrastructure/persistence/entity/ProfessionalCategoryEntity.java`

## 6. Evolución por commit

Métricas calculadas sobre el estado de `main` en **cada commit** (8 commits con trabajo), del primero al último. La calidad usa el perfil `exigente`; el aporte de código lo mide RepoSense. El índice puede **bajar** al crecer el proyecto: se acumulan señales.

```mermaid
xychart-beta
    title "Índice de calidad a lo largo de los commits"
    x-axis ["5872261", "941aed3", "511aa03", "690becd", "aa9e8d1", "9d1407c", "14b03bb", "81ae780"]
    y-axis "Índice" 0 --> 100
    line [0, 15, 15, 35, 40, 51, 55, 57]
```

| # | Fecha | Commit | Autor | LOC | Índice | %POO | Duplic. | Sueltas |
|---|---|---|---|---:|---:|---:|---:|---:|
| 1 | 2026-09-07 | `5872261` | Valentino Perassi Ferrara | 0 | 0 | 0% | 0.0% | 0 |
| 2 | 2026-09-10 | `941aed3` | Ing. Daniel Quinteros | 0 | 15 | 0% | 0.0% | 0 |
| 3 | 2026-10-06 | `511aa03` | Valentino Perassi Ferrara | 0 | 15 | 0% | 0.0% | 0 |
| 4 | 2026-10-06 | `690becd` | Valentino Perassi Ferrara | 172 | 35 | 73% | 0.0% | 0 |
| 5 | 2026-10-12 | `aa9e8d1` | Valentino Perassi Ferrara | 172 | 40 | 73% | 0.0% | 0 |
| 6 | 2026-10-12 | `9d1407c` | Valentino Perassi Ferrara | 1,492 | 51 | 80% | 0.0% | 0 |
| 7 | 2026-10-15 | `14b03bb` | Valentino Perassi Ferrara | 2,427 | 55 | 78% | 0.0% | 0 |
| 8 | 2026-10-18 | `81ae780` | Valentino Perassi Ferrara | 4,569 | 57 | 80% | 1.5% | 0 |

---
_Informe generado por la cátedra. Las dimensiones y umbrales corresponden a `calidad.py` (perfil `exigente`); el semáforo, a `CRITERIOS-CALIDAD.md`. La documentación del repositorio no se evalúa. Ante dudas, revisá el archivo señalado._
<!-- evaluacion:fin -->
