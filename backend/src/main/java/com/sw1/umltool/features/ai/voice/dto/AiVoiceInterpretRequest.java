package com.sw1.umltool.features.ai.voice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AiVoiceInterpretRequest(
        @NotBlank @Size(max = 2000) String text,
        String language,
        @Valid DiagramContext diagramContext
) {
    public record DiagramContext(List<ClassContext> classes) {}
    public record ClassContext(String id, String name) {}
}
