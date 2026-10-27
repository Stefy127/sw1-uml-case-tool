package com.sw1.umltool.features.importexport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ImageUpdateApplyRequest(
        @NotBlank String diagramId,
        long baseVersion,
        boolean allowDestructiveChanges,
        @NotNull List<ImageUpdateSelectedProposal> operations) {}
