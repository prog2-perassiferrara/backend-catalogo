# SPEC: Simplificación de configuración y boilerplate

**Estado:** Borrador documental. Alcance e implementación autorizados expresamente el 2026-11-03; no se infiere aprobación de este documento nuevo.

## Qué construimos y para quién

Menos código mecánico para mantener y comprender Catálogo, conservando su comportamiento.

## Situación actual

Hay fábricas de componentes sin configuración especial y métodos que solo asignan o devuelven campos.

## Dentro del alcance

- **RF-01:** Reducir wiring y boilerplate sin alterar operaciones, contratos o garantías existentes.
- **RF-02:** Preservar configuración externa, defaults, validación temprana y diagnósticos sin secretos.
- **RF-03:** Mantener construcción, persistencia y API de los objetos afectados.

## Fuera de alcance

Algoritmos de sincronización, recuperación, estados, transacciones, seguridad, migraciones y cambios en otros servicios.

## Flujo del caso de uso

El arranque valida configuración, construye componentes y ejecuta las mismas operaciones en el mismo orden funcional. Las consultas y eventos mantienen sus resultados.

## Datos y reglas de negocio

No cambian datos, reglas, endpoints ni nombres de propiedades o variables.

## Comportamiento del backend y casos alternativos

| Situación | Comportamiento esperado |
| --- | --- |
| Configuración válida o inválida | Mismos defaults, sobrescrituras, validación y errores seguros. |
| Consultas, sincronización, fallos y concurrencia | Mismas garantías existentes. |

## Restricciones del pedido

Solo bloque 1. Sin cambios en Turnos, contratos o infraestructura remota, ni reescritura del historial Git. Lombok selectivo, sin generación de setters o igualdad.

## Criterios de aceptación

- **CA-01 · RF-01:** Contextos sin ambigüedades ni ciclos y suites funcionales completas aprobadas.
- **CA-02 · RF-02:** Configuración válida/inválida y entorno conservan resultados y rechazo previo a recursos externos.
- **CA-03 · RF-03:** Firmas públicas/protegidas y mapeos JPA permanecen equivalentes; no se agregan getters ajenos al contrato actual.

## Cómo se comprueba el comportamiento

| Criterio | Comprobación |
| --- | --- |
| CA-01 | Baseline y clean verify final, incluidos seguridad, OpenAPI, SQL y Kafka temporales. |
| CA-02 | Suites de propiedades y arranque existentes, sin modificar aserciones. |
| CA-03 | Comparación de firmas compiladas, revisión de cuerpos y contratos de ambos backends. |

## Decisiones pendientes

Ninguna para ejecutar el alcance autorizado. Registro documental sujeto a revisión.
