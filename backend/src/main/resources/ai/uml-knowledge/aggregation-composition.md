# SW1-UML-AGGREGATION-COMPOSITION

- Tema: agregación y composición
- Fuente: OMG UML 2.5.1, formal/2017-12-05, https://www.omg.org/spec/UML/2.5.1/
- Versión: 1.0

## Reglas verificables

- `AGGREGATION` y `COMPOSITION` son tipos distintos de relación en el modelo canónico.
- La comprobación debe usar el tipo y los extremos persistidos, no la posición visual inferida por el texto.

## Elementos opcionales

- Los roles pueden estar vacíos cuando el modelo los permite.

## Ejemplos

- Válido: una relación con tipo `AGGREGATION` y multiplicidades válidas.
- Válido: una relación con tipo `COMPOSITION` y multiplicidades válidas.

## Limitaciones

- El análisis no debe inventar ownership JPA, cascadas ni reglas de base de datos.
