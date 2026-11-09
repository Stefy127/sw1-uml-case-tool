package com.sw1.umltool.features.ai.changeproposal.repository;

import com.sw1.umltool.features.ai.changeproposal.model.persistence.AiChangeProposalApplicationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiChangeProposalApplicationRepository extends JpaRepository<AiChangeProposalApplicationEntity, String> {
    Optional<AiChangeProposalApplicationEntity> findByProjectIdAndUserIdAndDiagramIdAndRequestId(
            String projectId, String userId, String diagramId, String requestId);
}
