package com.sw1.umltool.features.diagram.repository;

import com.sw1.umltool.features.diagram.model.persistence.DiagramEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface DiagramRepository extends JpaRepository<DiagramEntity, String> {

    List<DiagramEntity> findByProjectId(String projectId);
    Page<DiagramEntity> findByProjectId(String projectId, Pageable pageable);
    long countByProjectId(String projectId);
    void deleteByProjectId(String projectId);

    Optional<DiagramEntity> findByIdAndProjectId(String id, String projectId);
}
