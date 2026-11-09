package com.sw1.umltool.features.ai.changeproposal.dto;

public record AiChangeProposalApplyOperation(
        String proposalId,
        String operation,
        String targetElementId,
        String targetAttributeId,
        String newAttributeName,
        String attributeType) { }
