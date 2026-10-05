# CU-06. Informar el estado de sincronización

**Estado:** Revisado con el usuario.

## Identificación

| Campo | Descripción |
| --- | --- |
| Sistema | Backend Catálogo y sincronización. |
| Objetivo | Permitir que un consumidor distinga una copia vigente de una copia anterior posiblemente desactualizada. |
| Actores principales | Usuario final vía KMP y backend Turnos, cada uno en su propia interacción. |
| Actores de apoyo | Ninguno durante la entrega del estado local. |
| Disparador | KMP necesita informar la situación de los datos o Turnos necesita conocer la vigencia para una operación. |

## Precondiciones

- El consumidor está autenticado y autorizado para recibir la información que le corresponde.
- Catálogo está disponible para informar su estado; no se exige que exista una copia local ni que la última sincronización haya tenido éxito.

## Flujo principal

1. El consumidor necesita conocer el estado de los datos del catálogo.
2. Catálogo determina si existe una copia consistente, su versión aplicada y el resultado de la sincronización.
3. Catálogo comunica el estado con el detalle apropiado para el consumidor: resumen para KMP o información de vigencia para Turnos.
4. Si el consumidor es KMP, presenta al usuario el aviso que corresponde al estado recibido. Si es Turnos, utiliza la información para decidir si puede iniciar una operación que requiere catálogo vigente.

## Flujos alternativos y de excepción

- **2a. Sincronización en curso:** Catálogo distingue la última copia consistente del resultado aún no aplicado y continúa en el paso 3. KMP puede buscar con aviso; Turnos espera para operaciones que requieren catálogo vigente.
- **2b. Sincronización fallida:** Catálogo informa que la copia consistente anterior podría estar desactualizada y continúa en el paso 3. Se aplica la misma política de consultas que en 2a.
- **2c. Sin copia consistente:** Catálogo informa esa condición y continúa en el paso 3. No presenta el estado como vigente ni habilita búsquedas sobre datos inexistentes o parciales.

## Postcondiciones

- **Éxito:** el consumidor conoce el estado aplicable a su interacción, incluso si la sincronización está en curso, falló o todavía no existe una copia.
- No se exponen credenciales ni JWT técnicos de cátedra. La consulta no modifica la copia ni la versión aplicada.

## Reglas y relaciones

- KMP recibe un estado resumido; Turnos recibe información de vigencia; los errores técnicos detallados quedan para diagnóstico operativo. Esta distribución fue acordada con el usuario.
- Este caso aporta información a [CU-04](CU-04-buscar-profesionales.md) y [CU-05](CU-05-consultar-agenda-vigente.md).
- La forma de entregar el estado puede integrarse en otros contratos; no se presupone un endpoint independiente.

## Fuentes y pendientes

**Fuente:** [enunciado, secciones 4.1, 8 y 9](../../../PROJECT_STATEMENT-v1.md).

**Pendiente para el PLAN:** transporte del estado, valores concretos e información de errores conservada para diagnóstico.
