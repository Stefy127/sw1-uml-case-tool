package com.sw1.umltool.features.diagram.history.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "diagram_history")
@Getter
@Setter
@NoArgsConstructor
public class DiagramHistoryEntity {
    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;
    @Column(nullable = false, length = 100)
    private String projectId;
    @Column(nullable = false, length = 100)
    private String diagramId;
    @Column(nullable = false, length = 100)
    private String userId;
    @Column(nullable = false, length = 100)
    private String operationId;
    @Column(nullable = false, length = 80)
    private String operationType;
    @Column(nullable = false, length = 30)
    private String origin;
    @Column(nullable = false, length = 20)
    private String action;
    @Column(nullable = false, length = 20)
    private String state;
    @Column(nullable = false)
    private long previousVersion;
    @Column(nullable = false)
    private long newVersion;
    @Column(nullable = false, length = 36)
    private String relatedHistoryId;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String beforeCanonicalModelJson;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String afterCanonicalModelJson;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String beforeViewStateJson;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String afterViewStateJson;
    @Column(nullable = false)
    private LocalDateTime createdAt;
}
