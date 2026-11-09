package com.sw1.umltool.features.ai.changeproposal.dto;

import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;

public record AiChangeProposalApplyResponse(
        String requestId,
        String projectId,
        String diagramId,
        long previousVersion,
        long newVersion,
        int appliedOperations,
        UmlDiagram canonicalModel,
        DiagramViewState viewState) { }
