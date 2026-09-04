package com.sw1.umltool.features.diagram.dto;

import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.persistence.DiagramEntity;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;
import com.sw1.umltool.features.diagram.service.DiagramStateSerializer;

public final class DiagramMapper {

    private DiagramMapper() {
    }

    public static DiagramSummaryResponse toSummary(DiagramEntity entity) {
        return DiagramSummaryResponse.builder()
                .id(entity.getId())
                .projectId(entity.getProjectId())
                .name(entity.getName())
                .version(entity.getVersion())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public static DiagramDetailResponse toDetail(DiagramEntity entity, DiagramStateSerializer serializer) {
        UmlDiagram canonicalModel = serializer.deserializeCanonical(entity.getCanonicalModelJson());
        DiagramViewState viewState = serializer.deserializeViewState(entity.getViewStateJson());
        return DiagramDetailResponse.builder()
                .id(entity.getId())
                .projectId(entity.getProjectId())
                .name(entity.getName())
                .version(entity.getVersion())
                .canonicalModel(canonicalModel)
                .viewState(viewState)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
