package com.sw1.umltool.features.ai.chat.service;

import com.sw1.umltool.features.ai.chat.model.AiConversationType;
import com.sw1.umltool.features.ai.chat.model.AiContextScope;

/** Scalar-only payload safe to hand to the asynchronous AI worker. */
public record AiGenerationRequest(String triggerMessageId, String conversationId,
                                  String projectId, String userId, String content,
                                  AiConversationType conversationType, String diagramId,
                                  AiContextScope contextScope, Long diagramVersion,
                                  String contextSnapshot) {
    public AiGenerationRequest(String triggerMessageId, String conversationId,
                               String projectId, String userId, String content,
                               AiConversationType conversationType) {
        this(triggerMessageId, conversationId, projectId, userId, content, conversationType, null, null, null, null);
    }
}
