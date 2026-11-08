package com.sw1.umltool.features.ai.changeproposal.dto;

public record AiChangeProposal(
        String proposalId,
        String operation,
        String targetElementId,
        String targetElementName,
        String targetAttributeId,
        String oldValue,
        String newValue,
        String reason,
        boolean requiresBusinessConfirmation) { }
