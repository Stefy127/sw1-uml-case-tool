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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import com.sw1.umltool.features.project.dto.AddProjectMemberRequest;
import com.sw1.umltool.features.project.dto.ProjectMemberResponse;
import com.sw1.umltool.features.project.dto.ProjectRoleResponse;
import com.sw1.umltool.features.project.model.ProjectMemberRole;

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

    @GetMapping("/shared") public List<ProjectResponse> shared(Authentication auth) { return projectService.shared(auth.getName()).stream().map(ProjectMapper::toResponse).toList(); }
    @GetMapping("/{projectId}/members") public List<ProjectMemberResponse> members(@PathVariable String projectId, Authentication auth) { return projectService.members(projectId, auth.getName()); }
    @GetMapping("/{projectId}/my-role") public ProjectRoleResponse role(@PathVariable String projectId, Authentication auth) { return projectService.role(projectId, auth.getName()); }
    @PostMapping("/{projectId}/members") public ResponseEntity<ProjectMemberResponse> addMember(@PathVariable String projectId, Authentication auth, @Valid @RequestBody AddProjectMemberRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(projectService.addMember(projectId,auth.getName(),request)); }
    @PutMapping("/{projectId}/members/{memberId}/role") public ProjectMemberResponse changeRole(@PathVariable String projectId,@PathVariable String memberId,Authentication auth,@RequestBody java.util.Map<String,ProjectMemberRole> request) { return projectService.changeRole(projectId,auth.getName(),memberId,request.get("role")); }
    @DeleteMapping("/{projectId}/members/{memberId}") public ResponseEntity<Void> removeMember(@PathVariable String projectId,@PathVariable String memberId,Authentication auth) { projectService.removeMember(projectId,auth.getName(),memberId); return ResponseEntity.noContent().build(); }
}
