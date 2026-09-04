package com.sw1.umltool.features.project.repository;

import com.sw1.umltool.features.project.model.ProjectMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMemberEntity, String> {

    List<ProjectMemberEntity> findByProjectId(String projectId);

    Optional<ProjectMemberEntity> findByProjectIdAndUserId(String projectId, String userId);

    boolean existsByProjectIdAndUserId(String projectId, String userId);
}
