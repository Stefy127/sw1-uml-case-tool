# SW1-UML-ASSOCIATIONS-MULTIPLICITY

- Tema: asociaciones y multiplicidades
- Fuente: OMG UML 2.5.1, formal/2017-12-05, https://www.omg.org/spec/UML/2.5.1/
- Versión: 1.0

## Reglas verificables

- Una asociación conecta dos extremos de clase identificables.
- En SW1, cada relación persistida valida sus clases origen y destino y sus dos multiplicidades.
- Las multiplicidades usan límite inferior numérico y límite superior numérico o `*`.

## Elementos opcionales

- Los nombres de rol pueden omitirse; su ausencia no es una infracción si las multiplicidades están definidas.

## Ejemplos

- Válido: `Cliente 1..1 — Pedido 0..*` sin nombres de rol.
- Inválido en SW1: relación cuyo extremo referencia una clase inexistente.

## Limitaciones

- Este documento no inventa obligatoriedad de roles ni reglas de negocio.
