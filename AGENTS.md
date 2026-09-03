Este es un proyecto de SW1.

No cambiar tecnologías sin autorización.

El modelo canónico es la fuente de verdad semántica.

No generar Java directamente con IA.

Todo cambio UML debe convertirse a operaciones válidas.

Toda relación debe validarse antes de generar Spring.

Spring generado:
Entity
Repository
Service
Controller
DTO únicamente si corresponde.

N:M se normaliza mediante entidad puente.

Dependency no genera FK.

La colaboración usa WebSocket, operaciones atómicas,
versionado y servidor autoritativo.

Después de modificar código:
compilar y ejecutar las pruebas correspondientes.
La aplicación principal y el backend Spring Boot generado son dos sistemas distintos.

No confundir:
1. Backend de la herramienta CASE.
2. Backend Spring Boot generado desde UML.

Toda entrada UML debe terminar en el mismo modelo canónico:
- Manual
- IA texto
- Voz
- Fotografía
- XMI

La IA no modifica directamente el estado visual del editor.
Debe producir operaciones semánticas que se aplican al modelo canónico.

La colaboración debe permitir edición concurrente real.
No sincronizar reemplazando el JSON completo del diagrama en cada cambio.

Toda operación colaborativa debe contemplar:
- operationId
- diagramId
- userId
- baseVersion
- tipo de operación
- payload

El servidor es la autoridad final del estado colaborativo.

Nunca guardar claves API, contraseñas reales o secretos en Git.

Antes de implementar una nueva funcionalidad:
1. Revisar arquitectura existente.
2. Explicar qué archivos se modificarán.
3. Mantener compatibilidad con el modelo canónico.

Después de implementar:
1. Compilar.
2. Ejecutar pruebas.
3. Reportar archivos modificados.
4. Reportar errores pendientes.