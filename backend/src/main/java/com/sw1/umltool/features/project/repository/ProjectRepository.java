package com.sw1.umltool.features.project.repository;

import com.sw1.umltool.features.project.model.ProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRepository extends JpaRepository<ProjectEntity, String> {

    List<ProjectEntity> findByOwnerUserId(String ownerUserId);
}
