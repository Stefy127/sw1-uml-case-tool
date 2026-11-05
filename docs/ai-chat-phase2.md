# Chat colaborativo por proyecto — Fase 2

La mensajería GROUP reutiliza `/ws/collaboration`. La sesión conserva la
suscripción al diagrama para UML y puede registrar suscripciones adicionales
por `projectId`; no se abre una conexión por conversación.

## Eventos

- Entrada: `CHAT_PROJECT_SUBSCRIBE`, `CHAT_PROJECT_UNSUBSCRIBE`, `CHAT_MESSAGE_SEND`.
- Salida: `CHAT_PROJECT_SUBSCRIBED`, `CHAT_MESSAGE_CREATED`, `CHAT_MESSAGE_REJECTED`.

El servidor obtiene el usuario desde el handshake JWT. La suscripción exige un
`ProjectMemberEntity`; los accesos únicamente por enlace no pueden suscribirse.
Los mensajes privados continúan usando REST y nunca se difunden por proyecto.

## Persistencia

`clientMessageId` se persiste en `ai_messages` y tiene un índice único compuesto
con `conversation_id`. El servidor guarda primero y difunde después. Un reintento
con el mismo identificador devuelve el mensaje ya persistido sin emitir una
segunda creación.

## Límites

La difusión es de instancia única y usa el registro concurrente existente junto
con `ConcurrentWebSocketSessionDecorator`. No hay broker, polling, hilos por
conversación ni historial completo en memoria. En un despliegue horizontal se
requeriría un mecanismo externo de fan-out o afinidad de sesión.
