package com.sw1.umltool.features.project.dto;

import com.sw1.umltool.features.project.model.ProjectEntity;

public final class ProjectMapper {

    private ProjectMapper() {
    }

    public static ProjectResponse toResponse(ProjectEntity project) {
        return ProjectResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .ownerUserId(project.getOwnerUserId())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }
}
