package com.sw1.umltool.features.importexport.dto;

import java.util.List;

public record ImageUpdatePreviewResponse(
        boolean success,
        String diagramId,
        long baseVersion,
        List<ImageUpdateProposal> operations,
        List<String> warnings,
        Double confidence) {}
