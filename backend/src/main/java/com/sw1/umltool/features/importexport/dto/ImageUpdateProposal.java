package com.sw1.umltool.features.importexport.dto;

import java.util.List;

public record ImageUpdateProposal(
        String proposalId,
        String type,
        String className,
        String secondaryClassName,
        String sourceClassName,
        String targetClassName,
        String newClassName,
        String attributeName,
        String newAttributeName,
        String attributeType,
        String methodName,
        String newMethodName,
        String returnType,
        String relationType,
        Double confidence,
        String summary,
        Boolean destructive,
        List<String> warnings) {
    public boolean isDestructive() {
        return Boolean.TRUE.equals(destructive)
                || List.of("DELETE_CLASS", "REMOVE_ATTRIBUTE", "REMOVE_METHOD", "REMOVE_RELATION").contains(type);
    }
}
