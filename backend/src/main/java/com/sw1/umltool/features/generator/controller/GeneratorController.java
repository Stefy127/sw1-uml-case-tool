package com.sw1.umltool.features.generator.controller;

import com.sw1.umltool.features.diagram.service.DiagramService;
import com.sw1.umltool.features.generator.dto.GenerateBackendRequest;
import com.sw1.umltool.features.generator.service.GeneratorService;
import com.sw1.umltool.features.project.service.ProjectAccessService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/diagrams")
public class GeneratorController {
    private final DiagramService diagrams;
    private final ProjectAccessService access;
    private final GeneratorService generator;

    public GeneratorController(DiagramService diagrams, ProjectAccessService access, GeneratorService generator) {
        this.diagrams = diagrams; this.access = access; this.generator = generator;
    }

    @PostMapping(value = "/{diagramId}/generate/backend", produces = "application/zip")
    public ResponseEntity<byte[]> generate(@PathVariable String diagramId, @RequestBody(required = false) GenerateBackendRequest request, Authentication authentication) {
        if (authentication == null) return ResponseEntity.status(401).build();
        var diagram = diagrams.findById(diagramId, authentication.getName()).orElseThrow(() -> new IllegalArgumentException("Diagrama no encontrado."));
        access.requireDiagramEditor(diagramId, authentication.getName());
        byte[] zip = generator.generate(diagram, request);
        String name = generator.fileName(request == null ? null : request.artifactId(), diagram.getName());
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + ".zip\"")
                .contentType(MediaType.parseMediaType("application/zip")).body(zip);
    }
}
