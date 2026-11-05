package com.sw1.umltool.features.ai.chat.dto;

import com.sw1.umltool.features.ai.chat.model.AiMessageEntity;
import com.sw1.umltool.features.ai.chat.model.AiMessageAuthorType;

import java.time.Instant;
import java.time.ZoneOffset;

public record AiMessageResponse(String id, String clientMessageId, String authorUserId, String authorName,
                                AiMessageAuthorType authorType, String content,
                                Instant createdAt) {
    public static AiMessageResponse from(AiMessageEntity value) {
        String authorId = value.getAuthorUser() == null ? null : value.getAuthorUser().getId();
        String authorName = value.getAuthorUser() == null ? "SW1 IA" :
                (value.getAuthorUser().getFirstName() + " " + value.getAuthorUser().getLastName()).trim();
        Instant createdAt = value.getCreatedAt() == null
                ? null
                : value.getCreatedAt().toInstant(ZoneOffset.UTC);
        return new AiMessageResponse(value.getId(), value.getClientMessageId(), authorId, authorName, value.getAuthorType(),
                value.getContent(), createdAt);
    }
}
