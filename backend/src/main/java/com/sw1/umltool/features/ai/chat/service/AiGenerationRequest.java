package com.sw1.umltool.features.ai.chat.service;

import com.sw1.umltool.features.ai.chat.model.AiConversationType;

/** Scalar-only payload safe to hand to the asynchronous AI worker. */
public record AiGenerationRequest(String triggerMessageId, String conversationId,
                                  String projectId, String userId, String content,
                                  AiConversationType conversationType) { }
