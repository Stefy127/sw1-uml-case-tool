package com.sw1.umltool.features.project.dto;

import com.sw1.umltool.features.project.model.ProjectMemberRole;

public record SharedProjectResponse(ProjectResponse project, ProjectMemberRole role) {}
