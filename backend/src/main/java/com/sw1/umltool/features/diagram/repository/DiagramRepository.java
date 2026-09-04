package com.sw1.umltool.features.diagram.repository;

import com.sw1.umltool.features.diagram.model.persistence.DiagramEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DiagramRepository extends JpaRepository<DiagramEntity, String> {

    List<DiagramEntity> findByProjectId(String projectId);

    Optional<DiagramEntity> findByIdAndProjectId(String id, String projectId);
}
