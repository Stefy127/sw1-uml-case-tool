# Integración de interpretación n8n

El editor intenta primero interpretar el texto con `VoiceCommandParserService`. n8n solo se consulta cuando el parser local falla y el usuario pulsa «Interpretar con IA».

## Arquitectura

`SpeechRecognition -> parser local -> preview -> DiagramOperation` o, como fallback explícito, `parser local -> backend proxy -> n8n -> respuesta JSON validada -> preview -> DiagramOperation`.

El endpoint propio es `POST /api/ai/voice/interpret` con JSON:

```json
{
  "text": "haz que Empleado herede de Persona",
  "language": "es-BO",
  "diagramContext": { "classes": [{ "id": "interno", "name": "Empleado" }] }
}
```

El backend usa `N8N_VOICE_WEBHOOK_URL` y opcionalmente `N8N_VOICE_WEBHOOK_TOKEN`. En Docker Desktop para Windows, configura `N8N_VOICE_WEBHOOK_URL=http://host.docker.internal:5678/webhook/voice-interpret`; no uses `localhost`, porque dentro del contenedor apunta al propio backend. El token nunca llega a Angular ni se escribe en logs. Si la URL no está configurada, el proxy devuelve un error seguro.

## Contrato n8n

n8n debe responder únicamente JSON con `success`, `command`, `confidence`, `summary` y `errors`. El comando puede usar `className`/`secondaryClassName` o `sourceClassName`/`targetClassName`; el proxy normaliza los nombres antes de devolverlo. Solo se aceptan comandos y tipos de relación permitidos por el backend.

Workflow recomendado: Webhook -> Set/Normalize Input -> LLM -> Structured Output Parser -> Respond to Webhook. n8n solo interpreta texto: no accede a PostgreSQL, no llama al endpoint de operaciones y no genera Java.

## Prompt recomendado

Eres un parser de comandos UML. Convierte el texto en español en exactamente una acción semántica. Solo puedes usar `CREATE_CLASS`, `DELETE_CLASS`, `RENAME_CLASS`, `ADD_ATTRIBUTE`, `REMOVE_ATTRIBUTE`, `ADD_METHOD` y `CREATE_RELATION`. Para relaciones usa `ASSOCIATION`, `AGGREGATION`, `COMPOSITION`, `INHERITANCE` o `DEPENDENCY`. No inventes IDs, usa nombres de clase, rechaza acciones múltiples y devuelve únicamente JSON válido sin Markdown.

Una confianza menor a 0.75 debe quedar como preview no aplicable; la aplicación sigue requiriendo revisión explícita del usuario. Los comandos multiacción se rechazan en esta versión.
