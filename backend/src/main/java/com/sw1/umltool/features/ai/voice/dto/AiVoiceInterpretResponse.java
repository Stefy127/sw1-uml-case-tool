package com.sw1.umltool.features.ai.voice.dto;

import java.util.List;

public record AiVoiceInterpretResponse(
        boolean success,
        AiCommand command,
        Double confidence,
        String summary,
        List<String> errors
) {
    public record AiCommand(
            String type,
            String className,
            String secondaryClassName,
            String sourceClassName,
            String targetClassName,
            String newClassName,
            String attributeName,
            String attributeType,
            String methodName,
            String relationType
    ) {}
}
