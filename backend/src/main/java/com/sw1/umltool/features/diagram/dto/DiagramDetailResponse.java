package com.sw1.umltool.features.diagram.dto;

import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagramDetailResponse {

    private String id;
    private String projectId;
    private String name;
    private long version;
    private UmlDiagram canonicalModel;
    private DiagramViewState viewState;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
