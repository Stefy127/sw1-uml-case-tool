# Arquitectura del Proyecto

## 1. Descripción general

El sistema corresponde al Primer Parcial de Ingeniería de Software 1.

El objetivo es desarrollar una herramienta CASE colaborativa que permita crear y modificar diagramas de clases UML mediante distintos mecanismos de entrada:

- Edición manual.
- Inteligencia Artificial mediante texto.
- Comandos por voz.
- Fotografía de diagramas.
- Importación desde Enterprise Architect mediante XMI.

El sistema también debe permitir generar un backend Spring Boot a partir del modelo UML.

---

## 2. Separación de sistemas

El proyecto contiene dos conceptos distintos:

### 2.1 Backend de la herramienta CASE

Es el backend principal de la aplicación que se está desarrollando.

Responsabilidades principales:

- Autenticación.
- Gestión de usuarios.
- Gestión de proyectos.
- Gestión de diagramas.
- Persistencia del modelo UML.
- Inteligencia Artificial.
- Colaboración en tiempo real.
- Importación y exportación XMI.
- Generación de proyectos Spring Boot.

### 2.2 Backend Spring Boot generado

Es el backend que la herramienta CASE crea automáticamente a partir de un diagrama UML.

Debe generar como mínimo:

- Entity / Model.
- Repository.
- Service.
- Controller.
- DTO únicamente cuando sea necesario.

El backend generado utilizará:

- Spring Boot.
- Maven.
- JPA / Hibernate.
- PostgreSQL.
- Swagger / OpenAPI.
- Colección Postman.

---

## 3. Arquitectura del backend de la herramienta CASE

El backend se organiza por funcionalidades.

Estructura principal:

```text
src/main/java/com/sw1/umltool/
├── common/
├── config/
├── features/
│   ├── ai/
│   ├── auth/
│   ├── collaboration/
│   ├── diagram/
│   ├── generator/
│   ├── importexport/
│   └── project/
└── UmlToolApplication.java