package com.sw1.umltool.features.importexport.dto;

import java.util.List;

public record ImageUpdateAiResponse(
        boolean success,
        List<ImageUpdateProposal> operations,
        List<String> warnings,
        Double confidence) {}
