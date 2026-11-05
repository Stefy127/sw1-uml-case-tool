package com.sw1.umltool.features.ai.memory.dto;

import com.sw1.umltool.features.ai.memory.model.AiMemoryEntity;
import com.sw1.umltool.features.ai.memory.model.AiMemoryScope;
import com.sw1.umltool.features.ai.memory.model.AiMemoryType;

import java.time.LocalDateTime;

public record AiMemoryResponse(String id, String projectId, AiMemoryScope scope, String ownerUserId,
                               AiMemoryType type, String content, String createdByUserId,
                               String sourceMessageId, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static AiMemoryResponse from(AiMemoryEntity value) {
        return new AiMemoryResponse(value.getId(), value.getProjectId(), value.getScope(), value.getOwnerUserId(),
                value.getType(), value.getContent(), value.getCreatedByUserId(), value.getSourceMessageId(),
                value.getCreatedAt(), value.getUpdatedAt());
    }
}
