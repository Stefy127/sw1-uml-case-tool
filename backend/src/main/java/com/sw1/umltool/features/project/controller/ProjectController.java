package com.sw1.umltool.features.project.controller;

import com.sw1.umltool.features.project.dto.CreateProjectRequest;
import com.sw1.umltool.features.project.dto.ProjectMapper;
import com.sw1.umltool.features.project.dto.ProjectResponse;
import com.sw1.umltool.features.project.service.ProjectService;
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
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProjectMapper.toResponse(projectService.createProject(
                        request.getName(), request.getDescription(), request.getOwnerUserId())));
    }

    @GetMapping
    public List<ProjectResponse> findByOwner(@RequestParam String ownerUserId) {
        return projectService.findByOwnerUserId(ownerUserId).stream()
                .map(ProjectMapper::toResponse)
                .toList();
    }

    @GetMapping("/{projectId}")
    public ResponseEntity<ProjectResponse> findById(@PathVariable String projectId) {
        return projectService.findById(projectId)
                .map(project -> ResponseEntity.ok(ProjectMapper.toResponse(project)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
