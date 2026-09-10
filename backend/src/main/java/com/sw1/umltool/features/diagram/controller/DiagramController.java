package com.sw1.umltool.features.diagram.controller;

import com.sw1.umltool.features.diagram.dto.CreateDiagramRequest;
import com.sw1.umltool.features.diagram.dto.DiagramDetailResponse;
import com.sw1.umltool.features.diagram.dto.DiagramMapper;
import com.sw1.umltool.features.diagram.dto.DiagramSummaryResponse;
import com.sw1.umltool.features.diagram.service.DiagramService;
import com.sw1.umltool.features.diagram.service.DiagramStateSerializer;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import com.sw1.umltool.features.importexport.service.XmiExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;

@RestController
@RequestMapping("/api/diagrams")
public class DiagramController {

    private final DiagramService diagramService;
    private final DiagramStateSerializer diagramStateSerializer;
    private final XmiExportService xmiExportService;

    public DiagramController(DiagramService diagramService, DiagramStateSerializer diagramStateSerializer, XmiExportService xmiExportService) {
        this.diagramService = diagramService;
        this.diagramStateSerializer = diagramStateSerializer;
        this.xmiExportService = xmiExportService;
    }

    @PostMapping
    public ResponseEntity<DiagramDetailResponse> create(@Valid @RequestBody CreateDiagramRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(toDetail(authentication == null
                ? diagramService.createDiagram(request.getProjectId(), request.getName())
                : diagramService.createDiagram(request.getProjectId(), request.getName(), authentication.getName())));
    }

    @GetMapping
    public List<DiagramSummaryResponse> findByProject(@RequestParam String projectId, Authentication authentication) {
        return (authentication == null ? diagramService.findByProjectId(projectId) : diagramService.findByProjectId(projectId, authentication.getName())).stream()
                .map(DiagramMapper::toSummary)
                .toList();
    }

    @GetMapping("/{diagramId}")
    public ResponseEntity<DiagramDetailResponse> findById(@PathVariable String diagramId, Authentication authentication) {
        return (authentication == null ? diagramService.findById(diagramId) : diagramService.findById(diagramId, authentication.getName()))
                .map(entity -> ResponseEntity.ok(toDetail(entity)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private DiagramDetailResponse toDetail(com.sw1.umltool.features.diagram.model.persistence.DiagramEntity entity) {
        return DiagramMapper.toDetail(entity, diagramStateSerializer);
    }

    @GetMapping(value = "/{diagramId}/export/xmi", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<byte[]> exportXmi(@PathVariable String diagramId, Authentication authentication) {
        if (authentication == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return diagramService.findById(diagramId, authentication.getName())
                .map(entity -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeFilename(entity.getName()) + ".xmi\"")
                        .contentType(MediaType.APPLICATION_XML)
                        .body(xmiExportService.export(entity.getId(), entity.getCanonicalModelJson(), entity.getViewStateJson())))
                .orElseGet(() -> ResponseEntity.<byte[]>notFound().build());
    }

    private String safeFilename(String value) { return (value == null ? "diagrama" : value).replaceAll("[^A-Za-z0-9 _-]", "_").trim().replaceAll(" +", "_"); }
}
