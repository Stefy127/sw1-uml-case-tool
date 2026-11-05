package com.sw1.umltool.features.ai.chat.service;

import com.sw1.umltool.features.ai.chat.dto.AiMessageResponse;
import com.sw1.umltool.features.ai.chat.model.AiConversationType;
import com.sw1.umltool.features.ai.chat.model.AiMessageAuthorType;
import com.sw1.umltool.features.ai.chat.model.AiMessageEntity;
import com.sw1.umltool.features.ai.chat.repository.AiConversationRepository;
import com.sw1.umltool.features.ai.chat.repository.AiMessageRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class AiResponsePersistenceService {
    private final AiConversationRepository conversations;
    private final AiMessageRepository messages;

    public AiResponsePersistenceService(AiConversationRepository conversations, AiMessageRepository messages) {
        this.conversations = conversations;
        this.messages = messages;
    }

    @Transactional
    public Optional<GeneratedResponse> persist(AiGenerationRequest request, String content, AiMessageAuthorType authorType) {
        if (messages.findByResponseToMessageId(request.triggerMessageId()).isPresent()) return Optional.empty();
        var conversation = conversations.findById(request.conversationId()).orElse(null);
        if (conversation == null || !request.projectId().equals(conversation.getProject().getId())) return Optional.empty();
        var entity = AiMessageEntity.builder().id(UUID.randomUUID().toString()).conversation(conversation)
                .authorType(authorType).content(content).createdAt(LocalDateTime.now())
                .responseToMessageId(request.triggerMessageId()).build();
        try {
            entity = messages.saveAndFlush(entity);
        } catch (DataIntegrityViolationException duplicate) {
            return Optional.empty();
        }
        return Optional.of(new GeneratedResponse(AiMessageResponse.from(entity), request.conversationType()));
    }

    public record GeneratedResponse(AiMessageResponse message, AiConversationType conversationType) { }
}
