package com.sw1.umltool.features.ai.changeproposal.dto;

import java.util.List;

public record AiChangeProposalResponse(
        String projectId,
        String diagramId,
        long diagramVersion,
        List<AiChangeProposal> proposals,
        boolean isStale) { }
