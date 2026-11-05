# Fase 3B: Spring AI + Ollama

Esta fase integra Spring AI 2.0.2 con el servicio Ollama dockerizado. Spring AI
2.0 está diseñado para Spring Boot 4.0/4.1 y Spring Framework 7; la aplicación
usa Java 21 y el starter oficial `spring-ai-starter-model-ollama`.

## Arquitectura

- `AiChatService` conserva autorización, paginación y persistencia humana.
- `AiContextService` lee proyecto y diagrama desde PostgreSQL y construye un
  contexto UML compacto en backend.
- `AiOrchestrator` es la única capa que llama a `ChatClient`.
- El orquestador usa una cola limitada y un worker para no bloquear operaciones
  UML ni permitir generaciones ilimitadas.
- La respuesta se guarda en `ai_messages` como `author_type=AI`.
- `response_to_message_id` es único para impedir dos respuestas a un mismo
  mensaje ante reintentos.

No se usa memoria persistente de Spring AI, vector store, embeddings ni una
fuente de contexto enviada por el cliente. El asistente no tiene herramientas
para modificar `canonicalModel`.

## Chat privado

1. Angular guarda el mensaje mediante el endpoint privado existente.
2. Después del commit, `AiOrchestrator` programa la generación.
3. El contexto se obtiene con el usuario autenticado y el proyecto autorizado.
4. La respuesta se persiste y se difunde únicamente a sesiones WebSocket del
   usuario propietario de la conversación privada.

## Chat grupal y `@IA`

Los mensajes humanos normales se siguen guardando y difundiendo sin invocar
Ollama. Solo un mensaje que contiene la mención `@IA` programa una generación.
La respuesta se guarda en la conversación grupal y se difunde a sus miembros
autorizados mediante el broadcaster existente.

## Configuración

```properties
OLLAMA_BASE_URL=http://ollama:11434
OLLAMA_MODEL=qwen3.5:4b
OLLAMA_NUM_CTX=4096
AI_ORCHESTRATOR_ENABLED=true
AI_ORCHESTRATOR_QUEUE_CAPACITY=2
AI_ORCHESTRATOR_TIMEOUT_SECONDS=120
```

Spring AI no descarga modelos automáticamente:

```properties
spring.ai.ollama.init.pull-model-strategy=never
```

El modelo debe existir previamente en el volumen de Ollama. La aplicación
puede arrancar con Ollama apagado; la generación concreta devuelve una
respuesta controlada de disponibilidad y el chat humano continúa funcionando.

## Prueba local

```powershell
docker compose --profile ollama up -d ollama backend
docker compose --profile ollama exec ollama ollama ps
```

Abrir el editor con un usuario miembro y probar:

- Privado con IA: enviar una pregunta y esperar el evento `CHAT_AI_MESSAGE_CREATED`.
- Proyecto: enviar un mensaje normal y verificar que no hay llamada a Ollama.
- Proyecto: enviar `@IA explica las relaciones del diagrama` y verificar una
  única respuesta visible para todos los miembros autorizados.

## Seguridad y límites

La pertenencia al proyecto se valida antes de abrir conversaciones, listar
mensajes o construir contexto. La identidad se toma del JWT/handshake, nunca
del cuerpo del mensaje. Ollama no se publica en el host y no se envían datos a
Gemini, n8n ni servicios externos desde este flujo.

El contexto usa el diagrama persistido más recientemente actualizado cuando no
se recibe un `diagramId` activo en el contrato actual. Esto es una decisión
conservadora de Fase 3B; una fase posterior puede añadir un identificador de
diagrama explícito al chat sin aceptar el modelo canónico del frontend como
fuente de verdad.

## Limitaciones pendientes

- No hay memoria inteligente de largo plazo.
- No hay streaming de tokens; la respuesta se difunde al terminar.
- La medición de latencia y consumo GPU debe hacerse con el entorno Docker
  operativo y el modelo real cargado.
- `AI_ORCHESTRATOR_TIMEOUT_SECONDS` queda reservado para endurecer el cliente
  HTTP de Ollama en una iteración específica; la disponibilidad actual depende
  del timeout del cliente Spring AI/Ollama y del worker acotado.
El orquestador aplica `OllamaChatOptions.builder().disableThinking()` en cada
solicitud. La respuesta persistida es el contenido final recortado; no se
aplica una expresiÃ³n regular para eliminar texto, evitando borrar contenido
legÃ­timo.
