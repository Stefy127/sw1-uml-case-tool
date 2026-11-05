# UML image update workflow

This flow is separate from the full image import workflow. It proposes semantic
operations against the current canonical model and never replaces the diagram
directly.

Environment:

```env
N8N_UML_IMAGE_UPDATE_WEBHOOK_URL=http://n8n:5678/webhook/uml-image-update-interpret
```

Endpoints:

- `POST /api/import/image/update-preview`: multipart fields `diagramId`,
  `baseVersion`, `instruction`, `allowDestructiveChanges`, and `file`.
- `POST /api/import/image/update-apply`: JSON fields `diagramId`,
  `baseVersion`, `allowDestructiveChanges`, and selected `operations`.

The workflow must return JSON with `success`, `operations`, `warnings`, and
`confidence`. Each operation uses the existing semantic operation vocabulary,
for example `ADD_ATTRIBUTE`, `RENAME_CLASS`, `REMOVE_RELATION`, or
`CHANGE_RELATION_TYPE`. Do not return canonical model IDs or Java code.

The prompt must compare the image with the complete `diagramContext`, including
classes, attributes, methods, parameters, relations, multiplicities and
association-class links. For example, if the context contains `Cliente` with
`ID: Long` and `Nombre: String`, and `Pedido` with `ID: Long`, while the image
shows `Pedido` with `Nombre: String`, return exactly one `ADD_ATTRIBUTE`
operation for `Pedido` with `attributeName: "Nombre"` and
`attributeType: "String"`. If there is no semantic difference, return an empty
`operations` array. The contract key is always `operations`; do not return
`proposals`, `changes` or `items`.

The backend checks the diagram editor permission, the optimistic
`baseVersion`, supported operation types, destructive-change opt-in, and the
canonical validator. Selected operations are applied atomically and produce a
single new diagram version. A version conflict returns HTTP 409.

The n8n workflow is intentionally inactive in the repository export and must
be activated/configured manually after importing it into n8n.
