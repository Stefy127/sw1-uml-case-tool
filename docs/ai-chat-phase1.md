# Chat colaborativo — Fase 1

Esta fase implementa únicamente conversaciones persistentes mediante REST.
No incluye Spring AI, Ollama, respuestas simuladas ni eventos WebSocket de chat.

## Endpoints

El usuario debe estar autenticado y ser miembro real del proyecto (`OWNER`, `EDITOR` o `VIEWER`). Los accesos únicamente mediante enlace no tienen chat en esta fase.

```text
GET  /api/projects/{projectId}/ai/chat
GET  /api/projects/{projectId}/ai/chat/{conversationId}/messages
POST /api/projects/{projectId}/ai/chat/{conversationId}/messages

GET  /api/projects/{projectId}/ai/private-chat
GET  /api/projects/{projectId}/ai/private-chat/{conversationId}/messages
POST /api/projects/{projectId}/ai/private-chat/{conversationId}/messages
```

El body de envío es:

```json
{"content":"Mensaje de ejemplo"}
```

El historial devuelve como máximo 30 mensajes por solicitud. Para mensajes anteriores se utiliza el cursor opaco `nextCursor` como parámetro `before`. No se utiliza `OFFSET` ni se carga todo el historial al abrir el editor.

Las conversaciones grupales se identifican por proyecto. Las privadas se identifican por proyecto y usuario autenticado. El backend nunca toma `userId`, autor o rol desde el body.

## Configuración

```text
AI_CHAT_MAX_MESSAGE_LENGTH=4000
```

Las tablas y restricciones se crean mediante el mecanismo actual del proyecto (`spring.jpa.hibernate.ddl-auto=update`). No se añadió una migración destructiva.

La pestaña privada solo persiste mensajes del usuario. No genera respuestas ficticias; la integración con Ollama queda para una fase posterior.
