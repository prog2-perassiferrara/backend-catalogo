# CU-03. Resolver una discontinuidad

**Estado:** Revisado con el usuario.

## Identificación

| Campo | Descripción |
| --- | --- |
| Sistema | Backend Catálogo y sincronización. |
| Objetivo | Recuperar una copia verificable cuando no es seguro continuar mediante incrementos. |
| Actor iniciador | No hay un actor externo obligatorio: Catálogo inicia la recuperación al detectar el problema. |
| Actor de apoyo | Servicio de cátedra, que entrega el snapshot utilizado por CU-01. |
| Disparador | Versión local fuera del historial disponible, incremento ausente, secuencia discontinua o estado local no verificable. |

## Precondiciones

- Catálogo ha detectado que no puede demostrar continuidad desde el estado local.
- La integración técnica está configurada para solicitar un snapshot.

## Flujo principal

1. Catálogo identifica que no puede continuar la actualización incremental de forma segura.
2. Catálogo deja de aplicar esa secuencia de incrementos.
3. Catálogo ejecuta [CU-01](CU-01-inicializar-o-reconstruir.md) para obtener y aplicar un snapshot completo.
4. Una vez aplicado el snapshot, Catálogo informa el resultado de la recuperación y utiliza su versión como punto de partida de las siguientes actualizaciones.

## Flujos alternativos y de excepción

- **3a. CU-01 falla:** Catálogo informa que la recuperación no terminó correctamente. No salta versiones ni declara recuperada la sincronización; el caso termina.
- **3b. Hay consultas durante la recuperación:** se aplica la política acordada en CU-01. KMP puede buscar sobre la última copia consistente con aviso; Turnos espera para operaciones que requieren catálogo vigente. El flujo principal continúa.
- **3a/3b, sin copia consistente:** las búsquedas no pueden resolverse.

## Postcondiciones

- **Éxito:** existe una copia consistente con la versión del snapshot aplicado; las actualizaciones posteriores parten de esa versión.
- **Fallo:** no se declara completada la recuperación; se conserva la última copia consistente, si existe, y se informa el error.

## Reglas y relaciones

- Extiende [CU-02](CU-02-incorporar-cambios.md) cuando la continuidad no puede verificarse. También puede iniciarse al detectar un estado no verificable durante el arranque.
- Incluye CU-01: la recuperación utiliza siempre un snapshot completo.
- Una notificación repetida después de recuperarse no revierte el estado ni repite efectos.
- Mantener CU-03 separado para documentar y verificar la recuperación fue una decisión del usuario.

## Fuentes y pendientes

**Fuentes:** [enunciado, sección 6.2](../../../PROJECT_STATEMENT-v1.md); [contrato, secciones 14.2 y 18.2](../../../INTEGRATION_REFERENCE-v2.md).

**Pendiente para el PLAN:** coordinación entre reconstrucción y actualización incremental, y diagnóstico de la causa de recuperación.
