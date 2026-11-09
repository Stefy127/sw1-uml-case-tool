package com.sw1.umltool.features.ai.changeproposal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AiChangeProposalApplyRequest(
        @NotBlank String diagramId,
        long baseVersion,
        @NotBlank String requestId,
        boolean confirmed,
        @NotEmpty List<@NotNull @Valid AiChangeProposalApplyOperation> operations) { }
