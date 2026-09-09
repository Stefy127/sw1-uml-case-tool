package com.sw1.umltool.features.project.repository;

import com.sw1.umltool.features.project.model.ProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<ProjectEntity, String> {

    List<ProjectEntity> findByOwnerUserId(String ownerUserId);
    Optional<ProjectEntity> findByShareToken(String shareToken);
}
