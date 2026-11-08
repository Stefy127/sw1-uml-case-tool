package com.sw1.umltool.features.ai.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.sw1.umltool.features.ai.chat.model.AiContextScope;

public record SendAiMessageRequest(@NotBlank @Size(max = 4000) String content,
                                   @Size(max = 100) String clientMessageId,
                                   @Size(max = 100) String diagramId,
                                   AiContextScope contextScope,
                                   Long diagramVersion) {
    public SendAiMessageRequest(String content) { this(content, null, null, null, null); }
    public SendAiMessageRequest(String content, String clientMessageId) { this(content, clientMessageId, null, null, null); }
    public SendAiMessageRequest(String content, String clientMessageId, String diagramId) { this(content, clientMessageId, diagramId, null, null); }
}
