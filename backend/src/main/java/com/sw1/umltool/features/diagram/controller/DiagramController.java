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

import java.util.List;

@RestController
@RequestMapping("/api/diagrams")
public class DiagramController {

    private final DiagramService diagramService;
    private final DiagramStateSerializer diagramStateSerializer;

    public DiagramController(DiagramService diagramService, DiagramStateSerializer diagramStateSerializer) {
        this.diagramService = diagramService;
        this.diagramStateSerializer = diagramStateSerializer;
    }

    @PostMapping
    public ResponseEntity<DiagramDetailResponse> create(@Valid @RequestBody CreateDiagramRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(toDetail(diagramService.createDiagram(
                request.getProjectId(), request.getName())));
    }

    @GetMapping
    public List<DiagramSummaryResponse> findByProject(@RequestParam String projectId) {
        return diagramService.findByProjectId(projectId).stream()
                .map(DiagramMapper::toSummary)
                .toList();
    }

    @GetMapping("/{diagramId}")
    public ResponseEntity<DiagramDetailResponse> findById(@PathVariable String diagramId) {
        return diagramService.findById(diagramId)
                .map(entity -> ResponseEntity.ok(toDetail(entity)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private DiagramDetailResponse toDetail(com.sw1.umltool.features.diagram.model.persistence.DiagramEntity entity) {
        return DiagramMapper.toDetail(entity, diagramStateSerializer);
    }
}
