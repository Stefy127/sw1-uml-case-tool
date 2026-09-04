package com.sw1.umltool.features.project.service;

import com.sw1.umltool.features.project.model.ProjectEntity;
import com.sw1.umltool.features.project.model.ProjectMemberEntity;
import com.sw1.umltool.features.project.model.ProjectMemberRole;
import com.sw1.umltool.features.project.repository.ProjectMemberRepository;
import com.sw1.umltool.features.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    public ProjectService(ProjectRepository projectRepository, ProjectMemberRepository projectMemberRepository) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
    }

    @Transactional
    public ProjectEntity createProject(String name, String description, String ownerUserId) {
        if (isBlank(name)) throw new IllegalArgumentException("Project name is required");
        if (isBlank(ownerUserId)) throw new IllegalArgumentException("Owner user id is required");

        LocalDateTime now = LocalDateTime.now();
        ProjectEntity project = ProjectEntity.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .description(description)
                .ownerUserId(ownerUserId)
                .createdAt(now)
                .updatedAt(now)
                .build();
        ProjectEntity savedProject = projectRepository.save(project);

        projectMemberRepository.save(ProjectMemberEntity.builder()
                .id(UUID.randomUUID().toString())
                .projectId(savedProject.getId())
                .userId(ownerUserId)
                .role(ProjectMemberRole.OWNER)
                .createdAt(LocalDateTime.now())
                .build());

        return savedProject;
    }

    public List<ProjectEntity> findByOwnerUserId(String ownerUserId) {
        if (isBlank(ownerUserId)) throw new IllegalArgumentException("Owner user id is required");
        return projectRepository.findByOwnerUserId(ownerUserId);
    }

    public Optional<ProjectEntity> findById(String id) {
        if (isBlank(id)) throw new IllegalArgumentException("Project id is required");
        return projectRepository.findById(id);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
