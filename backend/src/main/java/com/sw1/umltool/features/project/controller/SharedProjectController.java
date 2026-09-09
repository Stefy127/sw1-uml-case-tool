package com.sw1.umltool.features.project.controller;

import com.sw1.umltool.features.project.dto.SharedProjectResponse;
import com.sw1.umltool.features.project.service.ProjectService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shared/projects")
public class SharedProjectController {
    private final ProjectService projectService;
    public SharedProjectController(ProjectService projectService) { this.projectService = projectService; }
    @GetMapping("/{token}") public SharedProjectResponse resolve(@PathVariable String token, Authentication auth) { return projectService.resolveShareToken(token, auth.getName()); }
}
