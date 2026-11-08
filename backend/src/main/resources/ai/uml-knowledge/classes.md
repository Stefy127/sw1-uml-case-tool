# SW1-UML-CLASSES

- Tema: clases y abstracción
- Fuente: OMG UML 2.5.1, formal/2017-12-05, https://www.omg.org/spec/UML/2.5.1/
- Versión: 1.0

## Reglas verificables

- Una clase UML puede ser concreta o abstracta.
- La ausencia de operaciones o constructores no demuestra que una clase concreta sea inválida.
- `isAbstract` describe la abstracción del elemento; no implica por sí mismo una cantidad mínima de atributos u operaciones.

## Elementos opcionales

- Atributos y operaciones pueden estar ausentes en la representación del diagrama.

## Ejemplos

- Válido: `Cliente` concreto con atributos y sin operaciones.
- Válido: `EntidadBase` abstracta.

## Limitaciones

- La utilidad del diseño no se infiere solo por contar atributos u operaciones.
- SW1 valida IDs, nombres y estructura del modelo canónico por separado.
