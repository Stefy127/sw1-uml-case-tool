# Ollama en Docker

Esta configuración prepara Ollama para el futuro uso desde Spring AI. En esta
fase no se instala Spring AI ni se ejecuta ningún modelo automáticamente al
arrancar el backend.

## Arquitectura

`ollama` forma parte de la red privada creada por Docker Compose. No tiene
`ports`, por lo que el puerto `11434` no se publica en el host. El backend usa
`http://ollama:11434` mediante `OLLAMA_BASE_URL`, pero no tiene una dependencia
de arranque o healthcheck sobre Ollama: la aplicación principal puede iniciar
aunque el servicio esté detenido o aún no tenga modelos.

Los modelos viven en el volumen Docker `ollama_models`; sus pesos no deben
guardarse en Git. La imagen utilizada es `ollama/ollama:latest`, siguiendo la
imagen oficial. Para despliegues reproducibles conviene fijar posteriormente
un tag de imagen aprobado por el equipo.

## CPU, desarrollo local

Requisitos: Docker Engine/Desktop con Compose v2.

```powershell
docker compose --profile ollama up -d ollama
docker compose ps
docker compose logs -f ollama
```

Descargar el modelo una sola vez dentro del volumen persistente:

```powershell
docker compose --profile ollama exec ollama ollama pull qwen3.5:4b
docker compose --profile ollama exec ollama ollama list
docker compose --profile ollama exec ollama ollama ps
```

El modelo recomendado es `qwen3.5:4b`. En el entorno validado ocupa
aproximadamente 3.1 GB en memoria de Ollama. Seleccionarlo explícitamente con
`OLLAMA_MODEL` cuando la
integración Spring AI esté implementada. Para una máquina AWS más limitada se
puede usar, por ejemplo, `qwen3:1.7b` o `qwen3:0.6b`, sin cambiar el Compose.

El nombre de modelo es una configuración de la futura integración; esta fase
no hace `pull` ni `run` durante el arranque.

## GPU NVIDIA opcional

El archivo base funciona en CPU. Si el host tiene GPU NVIDIA compatible,
controlador instalado y NVIDIA Container Toolkit configurado, iniciar con:

```powershell
docker compose --profile ollama -f docker-compose.yml -f docker-compose.gpu.yml up -d ollama
docker compose --profile ollama -f docker-compose.yml -f docker-compose.gpu.yml exec ollama ollama ps
docker compose --profile ollama -f docker-compose.yml -f docker-compose.gpu.yml logs ollama
```

El override añade una reserva Docker Compose de un dispositivo NVIDIA con
capacidad `gpu`. La existencia del YAML no garantiza que AWS tenga GPU: la
instancia EC2, sus drivers y NVIDIA Container Toolkit deben soportarlo. En una
instancia sin GPU se debe usar el Compose base y un modelo más pequeño.

## Variables

| Variable | Valor inicial | Uso |
| --- | --- | --- |
| `OLLAMA_BASE_URL` | `http://ollama:11434` | URL privada consumida por el backend |
| `OLLAMA_MODEL` | `qwen3.5:4b` | Modelo usado por Spring AI |
| `OLLAMA_NUM_CTX` | `4096` | Contexto inicial de la conversación |
| `OLLAMA_KEEP_ALIVE` | `2m` | Tiempo de permanencia del modelo cargado |
| `OLLAMA_NUM_PARALLEL` | `1` | Solicitudes paralelas iniciales |
| `OLLAMA_MAX_LOADED_MODELS` | `1` | Modelos cargados simultáneamente |

Los límites son conservadores para 16 GB de RAM y una RTX 4050 de 6 GB. Deben
medirse con el modelo y el contexto real antes de aumentarlos.

## Verificación y mantenimiento

```powershell
docker compose ps
docker compose --profile ollama exec ollama ollama list
docker compose --profile ollama exec ollama ollama ps
docker compose --profile ollama logs --tail=100 ollama
docker compose config
docker compose -f docker-compose.yml -f docker-compose.gpu.yml config
```

Para comprobar la persistencia, detener y recrear solo el contenedor sin
eliminar el volumen:

```powershell
docker compose --profile ollama stop ollama
docker compose --profile ollama rm -f ollama
docker compose --profile ollama up -d ollama
docker compose --profile ollama exec ollama ollama list
```

No usar `docker compose down -v` si se desea conservar los modelos. Para
eliminarlos de forma consciente, borrar únicamente el volumen nombrado:

```powershell
docker volume rm examen1_ollama_models
```

El nombre real puede variar según el nombre del proyecto Compose; comprobarlo
con `docker volume ls` antes de borrar. Esta operación es irreversible para
los modelos descargados.

## Red y seguridad

No acceder a Ollama mediante `localhost:11434` desde el backend: dentro de
Compose el hostname correcto es `ollama`. El puerto no está publicado y solo
los servicios de la red Compose pueden resolverlo. Si se necesita una prueba
manual desde el host, no se debe añadir un `ports` permanente; usar una
configuración temporal y revisarla antes de desplegar.

## AWS

Dockerizar Ollama no equivale a disponer de GPU. En EC2 hay que elegir una
familia con GPU si se requiere aceleración, instalar el driver NVIDIA y
NVIDIA Container Toolkit, y validar `docker run --gpus all ...` antes de usar
el override. En CPU, dimensionar memoria, concurrencia y modelo según las
métricas reales. No se modifica ninguna configuración AWS en esta fase porque
el hardware objetivo aún no está confirmado.

## Siguiente fase

La integración Spring AI debe añadir el cliente Ollama de forma opcional,
mantener el timeout/fallback adecuado y decidir qué operaciones del chat usan
modelo local. Nada de eso se realiza aquí; el backend existente no depende de
la disponibilidad de Ollama para iniciar.
