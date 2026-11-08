# SW1-UML-NAMING-CONVENTIONS

- Tema: convenciones de nomenclatura
- Fuente: Convención PROJECT explícita del usuario; no es una regla universal de OMG UML.
- Versión: 1.0

## Reglas verificables

- Una convención PROJECT solo se aplica al tipo de elemento y alcance que indique explícitamente.
- Una regla sobre nombres de clases no se aplica a atributos, operaciones o parámetros.

## Elementos opcionales

- Singularidad, capitalización y estilos como PascalCase o snake_case son preferencias si no existe una regla PROJECT explícita.

## Ejemplos

- Una regla “clases en singular” puede analizar `Cliente`, pero no `apellido` como atributo.

## Limitaciones

- No decidir singular/plural con una heurística simple de la última letra.
- Si falta evidencia suficiente, no producir WARNING o ERROR.
