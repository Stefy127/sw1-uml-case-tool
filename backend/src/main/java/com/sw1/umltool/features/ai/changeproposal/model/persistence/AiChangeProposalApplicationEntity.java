package com.sw1.umltool.features.ai.changeproposal.model.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_change_proposal_applications", uniqueConstraints = @UniqueConstraint(
        name = "uk_ai_change_proposal_application_scope",
        columnNames = {"project_id", "user_id", "diagram_id", "request_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChangeProposalApplicationEntity {
    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "request_id", nullable = false, length = 100)
    private String requestId;

    @Column(name = "project_id", nullable = false, length = 100)
    private String projectId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "diagram_id", nullable = false, length = 100)
    private String diagramId;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "previous_version", nullable = false)
    private long previousVersion;

    @Column(name = "new_version", nullable = false)
    private long newVersion;

    @Column(name = "applied_operations", nullable = false)
    private int appliedOperations;

    @Column(name = "canonical_model_json", nullable = false, columnDefinition = "TEXT")
    private String canonicalModelJson;

    @Column(name = "view_state_json", nullable = false, columnDefinition = "TEXT")
    private String viewStateJson;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
