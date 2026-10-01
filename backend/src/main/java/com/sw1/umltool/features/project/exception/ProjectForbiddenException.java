package com.sw1.umltool.features.project.exception;

public class ProjectForbiddenException extends RuntimeException {
    public ProjectForbiddenException() { super("Solo el propietario puede administrar este proyecto."); }
}
