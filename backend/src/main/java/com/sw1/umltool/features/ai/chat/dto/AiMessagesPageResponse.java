package com.sw1.umltool.features.ai.chat.dto;

import java.util.List;

public record AiMessagesPageResponse(List<AiMessageResponse> messages, String nextCursor) { }
