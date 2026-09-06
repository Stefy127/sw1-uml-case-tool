# Importación UML desde imagen con n8n

La aplicación envía la imagen desde Spring Boot a un workflow n8n independiente. Angular nunca recibe el token ni llama directamente a Gemini.

## Configuración

```env
N8N_UML_IMAGE_WEBHOOK_URL=http://host.docker.internal:5678/webhook/uml-image-interpret
N8N_UML_IMAGE_WEBHOOK_TOKEN=
```

El token es opcional. Si se configura, Spring lo envía como `Authorization: Bearer ...`.

## Workflow recomendado

`Webhook (POST)` → `Gemini multimodal` → `Structured Output Parser` → `Respond to Webhook`.

El webhook recibe JSON:

```json
{
  "fileName": "diagrama.png",
  "contentType": "image/png",
  "imageBase64": "..."
}
```

Debe devolver JSON conforme a esta forma (sin Markdown):

```json
{
  "success": true,
  "diagram": {
    "classes": [{
      "ref": "class-1",
      "name": "Cliente",
      "abstract": false,
      "attributes": [{"name":"id","type":"Long","visibility":"PRIVATE","static":false,"final":false}],
      "methods": []
    }],
    "relations": [{
      "ref": "relation-1",
      "type": "ASSOCIATION",
      "sourceClassRef": "class-1",
      "targetClassName": "Pedido",
      "sourceMultiplicity": "1",
      "targetMultiplicity": "0..*",
      "sourceNavigable": false,
      "targetNavigable": false
    }],
    "associationClasses": []
  },
  "warnings": [],
  "confidence": 0.92
}
```

Las referencias `ref` son temporales. Spring genera todos los UUID internos, construye el modelo canónico y lo valida antes del preview y del apply.

## Multiplicidades

`sourceMultiplicity` y `targetMultiplicity` son strings opcionales. Gemini debe devolverlos cuando sean legibles, conservando exactamente valores como `1`, `0..1`, `0..*`, `1..*` o `*`, y asociándolos al extremo de la clase correspondiente. Si no son visibles o son ambiguos, debe omitirlos o devolver `null` y agregar un warning. No debe intercambiarlos al normalizar origen y destino.

El Structured Output Parser debe permitir:

```json
{
  "sourceMultiplicity": { "type": ["string", "null"] },
  "targetMultiplicity": { "type": ["string", "null"] }
}
```

Si el nodo no acepta arrays de tipos, usar `type: "string"` y omitir el campo cuando no pueda determinarse. El backend mantiene el fallback existente `1..1` y genera un warning; no inventa multiplicidades nuevas.

## System prompt sugerido

Para cada relación inspecciona ambos extremos. Si una multiplicidad es visible junto a una clase, asígnala al extremo correspondiente y conserva exactamente su valor UML (`1`, `0..1`, `0..*`, `1..*` o `*`). No intercambies las multiplicidades al normalizar `source` y `target`. Si no es visible o es ambigua, omite el campo o usa `null` y agrega un warning; nunca la infieras.

> Eres un intérprete visual de diagramas UML de clases. Analiza exclusivamente lo visible. Extrae clases, atributos, métodos, parámetros y relaciones ASSOCIATION, AGGREGATION, COMPOSITION, INHERITANCE o DEPENDENCY. Distingue flechas, diamantes, multiplicidades, roles, navegabilidad, relaciones recursivas y clases de asociación. No inventes elementos: omite lo ilegible y agrega un warning. Devuelve exclusivamente JSON válido conforme al esquema; no expliques, no uses Markdown y no generes Java.

Para herencia, `source` es la hija y `target` la padre. En agregación/composición, `source` representa el extremo whole. Una relación recursiva puede usar la misma clase en ambos extremos. Las clases de asociación deben referenciar una relación `ASSOCIATION` mediante `relationRef`.

## Seguridad y límites

- El backend acepta PNG, JPEG y WEBP de hasta 10 MB y verifica firmas binarias.
- El archivo no se persiste; solo se procesa durante la solicitud.
- La salida de IA nunca se persiste directamente: se mapea y valida con `CanonicalModelValidator`.
- Sin URL configurada, el endpoint responde que el servicio de importación visual aún no está configurado.
- No se reconstruye el layout original: se genera una cuadrícula determinística editable.
