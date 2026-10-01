package com.sw1.umltool.features.project.exception;

public class ProjectNotFoundException extends RuntimeException {
    public ProjectNotFoundException(String projectId) { super("Proyecto no encontrado: " + projectId); }
}
