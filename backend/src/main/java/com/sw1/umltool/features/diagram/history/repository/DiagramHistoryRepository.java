package com.sw1.umltool.features.diagram.history.repository;

import com.sw1.umltool.features.diagram.history.model.DiagramHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface DiagramHistoryRepository extends JpaRepository<DiagramHistoryEntity, String> {
    Page<DiagramHistoryEntity> findByDiagramIdOrderByCreatedAtDesc(String diagramId, Pageable pageable);
    Optional<DiagramHistoryEntity> findFirstByDiagramIdAndStateOrderByNewVersionDesc(String diagramId, String state);
    Optional<DiagramHistoryEntity> findFirstByDiagramIdAndStateAndActionOrderByNewVersionDesc(
            String diagramId, String state, String action);
    Optional<DiagramHistoryEntity> findFirstByDiagramIdAndRelatedHistoryIdAndActionOrderByNewVersionDesc(
            String diagramId, String relatedHistoryId, String action);
    List<DiagramHistoryEntity> findByDiagramIdAndStateAndAction(String diagramId, String state, String action);
}
