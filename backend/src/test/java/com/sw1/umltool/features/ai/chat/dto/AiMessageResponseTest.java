package com.sw1.umltool.features.ai.chat.dto;

import com.sw1.umltool.features.ai.chat.model.AiMessageAuthorType;
import com.sw1.umltool.features.ai.chat.model.AiMessageEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiMessageResponseTest {
    @Test
    void exposesMessageTimestampAsAnUnambiguousUtcInstant() {
        var createdAt = LocalDateTime.of(2026, 1, 15, 18, 56);
        var message = AiMessageEntity.builder()
                .id("message-1")
                .authorType(AiMessageAuthorType.USER)
                .content("Hola")
                .createdAt(createdAt)
                .build();

        var response = AiMessageResponse.from(message);

        assertEquals(createdAt.toInstant(ZoneOffset.UTC), response.createdAt());
    }
}
