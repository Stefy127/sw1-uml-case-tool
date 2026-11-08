# SW1-UML-ATTRIBUTES-OPERATIONS

- Tema: atributos y operaciones
- Fuente: OMG UML 2.5.1, formal/2017-12-05, https://www.omg.org/spec/UML/2.5.1/
- Versión: 1.0

## Reglas verificables

- Un atributo pertenece a una clase y puede tener nombre y tipo en el modelo de SW1.
- Una operación puede tener parámetros; su ausencia no es un error estructural por sí sola.
- No se debe imponer PascalCase, camelCase, snake_case, singular o plural sin una convención PROJECT explícita.

## Elementos opcionales

- Visibilidad, modificadores y parámetros dependen de los datos presentes en el modelo.

## Ejemplos

- Válido: `apellido: String` como atributo aunque la clase use una convención distinta.
- Válido: clase sin operaciones.

## Limitaciones

- Las preferencias de nombres son recomendaciones, no restricciones UML universales.
