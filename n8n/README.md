# n8n - SW1 UML CASE Tool

El `docker-compose.yml` de la raíz ejecuta n8n junto con PostgreSQL, backend y frontend. Desde la raíz del repositorio:

```bash
docker compose up -d n8n
```

El editor queda disponible únicamente en `http://localhost:5678`. En EC2 se accede mediante un túnel SSH; el puerto 5678 no debe abrirse públicamente.

Los workflows exportados están en `workflows/workflows.json`. Después de importarlos, crea en n8n la credencial `Google Gemini(PaLM) Api account`, usa la clave indicada por `GEMINI_API_KEY` y selecciona esa credencial en los dos nodos Gemini.

El Compose incluido en esta carpeta se conserva para ejecutar n8n de forma independiente durante desarrollo. No debe levantarse al mismo tiempo que el Compose principal porque ambos usan el puerto 5678.
