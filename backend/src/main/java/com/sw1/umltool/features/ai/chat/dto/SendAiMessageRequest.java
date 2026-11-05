package com.sw1.umltool.features.ai.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendAiMessageRequest(@NotBlank @Size(max = 4000) String content,
                                   @Size(max = 100) String clientMessageId) {
    public SendAiMessageRequest(String content) { this(content, null); }
}
