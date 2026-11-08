# SW1-UML-CASE-CAPABILITIES

- Tema: capacidades y restricciones de SW1 UML CASE Tool
- Fuente: implementación versionada de SW1 UML CASE Tool, versión 1.0
- Versión: 1.0

## Reglas verificables

- El modelo canónico es la fuente de verdad para clases, atributos, operaciones, parámetros, relaciones y clases de asociación.
- Dependency no se interpreta como una relación persistente ni como una FK.
- El análisis solo puede referenciar elementos que existan en el modelo canónico.
- Las memorias PRIVATE no forman parte del contexto de análisis PROJECT.

## Elementos opcionales

- Las recomendaciones de diseño no cambian automáticamente el diagrama.

## Ejemplos

- Válido: una dependencia sin convertirla en atributo o FK.
- Válido: análisis sin hallazgos cuando el modelo no presenta inconsistencias verificables.

## Limitaciones

- Estas restricciones describen SW1 y no deben presentarse como normas UML universales.
