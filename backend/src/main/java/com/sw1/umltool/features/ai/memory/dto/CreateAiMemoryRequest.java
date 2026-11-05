package com.sw1.umltool.features.ai.memory.dto;

import com.sw1.umltool.features.ai.memory.model.AiMemoryScope;
import com.sw1.umltool.features.ai.memory.model.AiMemoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAiMemoryRequest(
        @NotNull AiMemoryScope scope,
        @NotNull AiMemoryType type,
        @NotBlank @Size(max = 2000) String content,
        @Size(max = 36) String sourceMessageId) {
}
