package com.sw1.umltool.features.ai.chat.dto;

import com.sw1.umltool.features.ai.chat.model.AiConversationEntity;
import com.sw1.umltool.features.ai.chat.model.AiConversationType;

import java.time.LocalDateTime;

public record AiConversationResponse(String id, String projectId, AiConversationType type,
                                     String ownerUserId, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static AiConversationResponse from(AiConversationEntity value) {
        return new AiConversationResponse(value.getId(), value.getProject().getId(), value.getType(),
                value.getOwnerUser() == null ? null : value.getOwnerUser().getId(),
                value.getCreatedAt(), value.getUpdatedAt());
    }
}
