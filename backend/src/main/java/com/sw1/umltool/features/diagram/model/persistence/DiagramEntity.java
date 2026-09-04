package com.sw1.umltool.features.diagram.model.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "diagrams")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagramEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private String id;

    @Column(nullable = false)
    private String projectId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private long version;

    @Column(name = "canonical_model_json", nullable = false, columnDefinition = "TEXT")
    private String canonicalModelJson;

    @Column(name = "view_state_json", nullable = false, columnDefinition = "TEXT")
    private String viewStateJson;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
