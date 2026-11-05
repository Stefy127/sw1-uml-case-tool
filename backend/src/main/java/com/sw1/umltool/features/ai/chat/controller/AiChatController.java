package com.sw1.umltool.features.ai.chat.controller;

import com.sw1.umltool.features.ai.chat.dto.AiConversationResponse;
import com.sw1.umltool.features.ai.chat.dto.AiMessageResponse;
import com.sw1.umltool.features.ai.chat.dto.AiMessagesPageResponse;
import com.sw1.umltool.features.ai.chat.dto.SendAiMessageRequest;
import com.sw1.umltool.features.ai.chat.service.AiChatService;
import com.sw1.umltool.features.collaboration.websocket.CollaborationBroadcastService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/ai")
public class AiChatController {
    private final AiChatService service;
    private final CollaborationBroadcastService broadcaster;

    public AiChatController(AiChatService service, CollaborationBroadcastService broadcaster) {
        this.service = service;
        this.broadcaster = broadcaster;
    }

    @GetMapping("/chat")
    public AiConversationResponse groupConversation(@PathVariable String projectId, Authentication authentication) {
        return service.getGroupConversation(projectId, authentication.getName());
    }

    @GetMapping("/private-chat")
    public AiConversationResponse privateConversation(@PathVariable String projectId, Authentication authentication) {
        return service.getPrivateConversation(projectId, authentication.getName());
    }

    @GetMapping("/chat/{conversationId}/messages")
    public AiMessagesPageResponse groupMessages(@PathVariable String projectId, @PathVariable String conversationId,
                                                 @RequestParam(required = false) String before,
                                                 Authentication authentication) {
        return service.listMessages(projectId, authentication.getName(), conversationId, before);
    }

    @GetMapping("/private-chat/{conversationId}/messages")
    public AiMessagesPageResponse privateMessages(@PathVariable String projectId, @PathVariable String conversationId,
                                                   @RequestParam(required = false) String before,
                                                   Authentication authentication) {
        return service.listMessages(projectId, authentication.getName(), conversationId, before);
    }

    @PostMapping("/chat/{conversationId}/messages")
    public ResponseEntity<AiMessageResponse> sendGroupMessage(@PathVariable String projectId,
                                                              @PathVariable String conversationId,
                                                              @Valid @RequestBody SendAiMessageRequest request,
                                                              Authentication authentication) {
        service.requireConversationAccess(projectId, authentication.getName(), conversationId);
        AiMessageResponse response = service.sendGroupMessage(projectId, authentication.getName(), conversationId, request);
        broadcaster.broadcastProjectMessage(projectId, Map.of("type", "CHAT_MESSAGE_CREATED", "projectId", projectId,
                "conversationId", conversationId, "clientMessageId", response.clientMessageId(), "message", response));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/private-chat/{conversationId}/messages")
    public ResponseEntity<AiMessageResponse> sendPrivateMessage(@PathVariable String projectId,
                                                                 @PathVariable String conversationId,
                                                                 @Valid @RequestBody SendAiMessageRequest request,
                                                                 Authentication authentication) {
        service.requireConversationAccess(projectId, authentication.getName(), conversationId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.sendPrivateMessage(projectId, authentication.getName(), conversationId, request));
    }
}
