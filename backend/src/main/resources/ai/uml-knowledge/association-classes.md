# SW1-UML-ASSOCIATION-CLASSES

- Tema: clases de asociación
- Fuente: OMG UML 2.5.1, formal/2017-12-05, https://www.omg.org/spec/UML/2.5.1/
- Versión: 1.0

## Reglas verificables

- SW1 representa el vínculo mediante `AssociationClassLink` entre una relación y una clase existentes.
- El vínculo debe referenciar IDs existentes y una relación `ASSOCIATION` según el validador canónico.

## Elementos opcionales

- Los atributos propios de la clase de asociación dependen del modelo.

## Ejemplos

- Válido: `Inscripción` vinculada a la asociación entre `Alumno` y `Materia`.

## Limitaciones

- No se debe crear una relación directa adicional solo porque existe una clase de asociación.
