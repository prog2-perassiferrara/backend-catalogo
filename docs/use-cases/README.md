# Casos de uso de Catálogo y sincronización

**Estado:** Los seis casos fueron revisados con el usuario. La interpretación de disponibilidad en CU-04 quedó acordada como decisión del proyecto. Estos casos describen comportamiento y no sustituyen las `SPEC.md` de cada funcionalidad.

**Fuentes:** [enunciado](../../../PROJECT_STATEMENT-v1.md), secciones 4.1, 5 y 6; [contrato de integración](../../../INTEGRATION_REFERENCE-v2.md), secciones 7, 14 y 15.3.

## Estructura de las descripciones

Cada ficha complementa el diagrama con objetivo, actores, disparador, precondiciones, flujo principal, alternativas vinculadas a pasos concretos y postcondiciones. Las reglas y decisiones pendientes se registran aparte para no mezclar el recorrido normal con restricciones o diseño técnico.

Las precondiciones describen la situación necesaria para iniciar el recorrido normal; las postcondiciones explican qué queda garantizado al terminar. Una alternativa indica el paso donde se desvía el recorrido y si continúa o termina. Esta es una convención narrativa del proyecto, no una plantilla textual obligatoria de UML.

CU-01 y CU-03 son procesos automáticos de soporte: no requieren inventar un actor externo que los inicie. CU-06 describe una interacción de KMP o de Turnos por ejecución, no una secuencia que obligue a intervenir a ambos.

## Actores y límite

- **Usuario final vía KMP:** consulta profesionales directamente en Catálogo y disponibilidad real y reservas directamente en Turnos. Es una decisión del proyecto; el enunciado no prescribe esa ruta.
- **Backend Turnos:** consume profesionales y agendas vigentes mediante un contrato protegido con JWT.
- **Servicio de cátedra:** actor de apoyo que provee snapshot REST, datos Redis y notificaciones Kafka. No es el usuario final.
- El arranque es un disparador interno, no un actor. Falta acordar cómo detectar notificaciones perdidas.

## Diagrama

Requiere Mermaid 12.0.0 o superior (`usecase-beta`). Probado en Mermaid Live 12.0.0.

```mermaid
usecase-beta
direction LR
actor KMP("Usuario final vía KMP")
actor Central("Servicio de cátedra")
actor Turnos("Backend Turnos")

systemBoundary Catalogo["Backend Catálogo y sincronización"]
  Inicializar("CU-01 Inicializar o reconstruir catálogo")
  Actualizar("CU-02 Incorporar cambios")
  Recuperar("CU-03 Resolver discontinuidad")
  Buscar("CU-04 Buscar profesionales")
  Agenda("CU-05 Consultar profesional y agenda vigente")
  Estado("CU-06 Informar estado de sincronización")
end

KMP --> Buscar
KMP --> Estado
Turnos --> Agenda
Turnos --> Estado
Central --> Inicializar
Central --> Actualizar
Recuperar ..> : extend Actualizar
Recuperar ..> : include Inicializar
```

KMP recibe un estado resumido para informar posibles datos desactualizados; Turnos recibe la vigencia necesaria para decidir si puede iniciar operaciones que dependen del catálogo. La recuperación también puede activarse al iniciar con un estado no verificable.

## Índice

| Caso | Resultado principal |
| --- | --- |
| [CU-01](CU-01-inicializar-o-reconstruir.md) | Copia completa y versión consistentes. Revisado con el usuario. |
| [CU-02](CU-02-incorporar-cambios.md) | Cambios pendientes aplicados en orden, sin repetir efectos. Revisado con el usuario. |
| [CU-03](CU-03-resolver-discontinuidad.md) | Recuperación por snapshot cuando no es seguro continuar. Revisado con el usuario. |
| [CU-04](CU-04-buscar-profesionales.md) | Búsqueda y filtros sobre la copia local. Revisado con el usuario. |
| [CU-05](CU-05-consultar-agenda-vigente.md) | Datos vigentes para el servicio de Turnos. Revisado con el usuario. |
| [CU-06](CU-06-estado-de-sincronizacion.md) | Estado resumido para KMP, vigencia para Turnos y errores técnicos para diagnóstico. Revisado con el usuario. |

La disponibilidad real de un turno requiere ocupaciones centrales y corresponde a Turnos. Para CU-04 se acordó que el filtro local de «disponibilidad» significa agenda habilitada para el día de la semana de la fecha elegida.

**Decisión acordada para CU-01:** durante una reconstrucción o si esta falla, KMP puede buscar en la última copia consistente con aviso de posible desactualización; Turnos no inicia operaciones que requieran catálogo vigente hasta que termine correctamente la sincronización.

**Decisión acordada para CU-03:** documentar la recuperación como caso separado para hacer explícito su comportamiento y validación.

**Decisión acordada para CU-06:** KMP recibe estado resumido, Turnos conoce la vigencia y los errores técnicos quedan para diagnóstico.
