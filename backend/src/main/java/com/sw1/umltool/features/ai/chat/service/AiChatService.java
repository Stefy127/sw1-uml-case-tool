package com.sw1.umltool.features.ai.chat.service;

import com.sw1.umltool.features.ai.chat.dto.AiConversationResponse;
import com.sw1.umltool.features.ai.chat.dto.AiMessageResponse;
import com.sw1.umltool.features.ai.chat.dto.AiMessagesPageResponse;
import com.sw1.umltool.features.ai.chat.dto.SendAiMessageRequest;
import com.sw1.umltool.features.ai.chat.exception.AiConversationNotFoundException;
import com.sw1.umltool.features.ai.chat.model.AiConversationEntity;
import com.sw1.umltool.features.ai.chat.model.AiConversationType;
import com.sw1.umltool.features.ai.chat.model.AiMessageAuthorType;
import com.sw1.umltool.features.ai.chat.model.AiMessageEntity;
import com.sw1.umltool.features.ai.chat.repository.AiConversationRepository;
import com.sw1.umltool.features.ai.chat.repository.AiMessageRepository;
import com.sw1.umltool.features.auth.model.UserEntity;
import com.sw1.umltool.features.auth.repository.UserRepository;
import com.sw1.umltool.features.project.model.ProjectEntity;
import com.sw1.umltool.features.project.service.ProjectAccessService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AiChatService {
    public static final int PAGE_SIZE = 30;
    private final AiConversationRepository conversations;
    private final AiMessageRepository messages;
    private final UserRepository users;
    private final ProjectAccessService access;
    private final int maxMessageLength;
    private final AiOrchestrator orchestrator;
    private final Map<String, Object> conversationLocks = new ConcurrentHashMap<>();
    private final Map<String, Object> messageLocks = new ConcurrentHashMap<>();

    @Autowired
    public AiChatService(AiConversationRepository conversations, AiMessageRepository messages,
                         UserRepository users, ProjectAccessService access,
                         @Value("${ai.chat.max-message-length:4000}") int maxMessageLength,
                         AiOrchestrator orchestrator) {
        this.conversations = conversations;
        this.messages = messages;
        this.users = users;
        this.access = access;
        this.maxMessageLength = maxMessageLength;
        this.orchestrator = orchestrator;
    }

    @Transactional
    public AiConversationResponse getGroupConversation(String projectId, String userId) {
        access.requireMember(projectId, userId);
        return AiConversationResponse.from(getOrCreate(projectId, null, AiConversationType.GROUP));
    }

    @Transactional
    public AiConversationResponse getPrivateConversation(String projectId, String userId) {
        access.requireMember(projectId, userId);
        return AiConversationResponse.from(getOrCreate(projectId, userId, AiConversationType.PRIVATE));
    }

    @Transactional
    public AiMessagesPageResponse listMessages(String projectId, String userId, String conversationId, String before) {
        AiConversationEntity conversation = authorizedConversation(projectId, userId, conversationId);
        Cursor cursor = decodeCursor(before);
        List<AiMessageEntity> fetched = cursor == null
                ? messages.findRecent(conversation.getId(), PageRequest.of(0, PAGE_SIZE + 1))
                : messages.findBefore(conversation.getId(), cursor.createdAt(), cursor.id(), PageRequest.of(0, PAGE_SIZE + 1));
        boolean hasMore = fetched.size() > PAGE_SIZE;
        List<AiMessageEntity> page = new ArrayList<>(fetched.subList(0, Math.min(PAGE_SIZE, fetched.size())));
        String nextCursor = hasMore ? encodeCursor(page.get(page.size() - 1)) : null;
        Collections.reverse(page);
        return new AiMessagesPageResponse(page.stream().map(AiMessageResponse::from).toList(), nextCursor);
    }

    @Transactional
    public AiMessageResponse sendGroupMessage(String projectId, String userId, String conversationId, SendAiMessageRequest request) {
        return AiMessageResponse.from(saveMessage(authorizedConversation(projectId, userId, conversationId), userId, request));
    }

    @Transactional
    public MessageSaveResult saveGroupMessage(String projectId, String userId, String conversationId, SendAiMessageRequest request) {
        return saveMessageResult(authorizedGroupConversation(projectId, userId, conversationId), userId, request);
    }

    public void requireGroupConversationAccess(String projectId, String userId, String conversationId) {
        authorizedGroupConversation(projectId, userId, conversationId);
    }

    @Transactional
    public AiMessageResponse sendPrivateMessage(String projectId, String userId, String conversationId, SendAiMessageRequest request) {
        return AiMessageResponse.from(saveMessage(authorizedConversation(projectId, userId, conversationId), userId, request));
    }

    public void requireConversationAccess(String projectId, String userId, String conversationId) {
        authorizedConversation(projectId, userId, conversationId);
    }

    private AiMessageEntity saveMessage(AiConversationEntity conversation, String userId, SendAiMessageRequest request) {
        return saveMessageResult(conversation, userId, request).message();
    }

    private MessageSaveResult saveMessageResult(AiConversationEntity conversation, String userId, SendAiMessageRequest request) {
        String content = request == null || request.content() == null ? "" : request.content().trim();
        if (content.isBlank()) throw new IllegalArgumentException("El mensaje no puede estar vacío.");
        if (content.length() > maxMessageLength) throw new IllegalArgumentException("El mensaje no puede superar " + maxMessageLength + " caracteres.");
        UserEntity author = users.findById(userId).orElseThrow(AiConversationNotFoundException::new);
        String clientMessageId = request == null || request.clientMessageId() == null || request.clientMessageId().isBlank()
                ? UUID.randomUUID().toString() : request.clientMessageId().trim();
        String lockKey = conversation.getId() + ":" + clientMessageId;
        Object lock = messageLocks.computeIfAbsent(lockKey, ignored -> new Object());
        try {
            synchronized (lock) {
                var existing = messages.findByConversation_IdAndClientMessageId(conversation.getId(), clientMessageId);
                if (existing.isPresent()) return new MessageSaveResult(existing.get(), false);
                LocalDateTime now = LocalDateTime.now();
                conversation.setUpdatedAt(now);
                conversations.save(conversation);
                AiMessageEntity saved = messages.saveAndFlush(AiMessageEntity.builder()
                        .id(UUID.randomUUID().toString())
                        .conversation(conversation)
                        .authorUser(author)
                        .authorType(AiMessageAuthorType.USER)
                        .content(content)
                        .clientMessageId(clientMessageId)
                        .createdAt(now)
                        .build());
                if (shouldInvokeAi(conversation, content)) requestAiAfterCommit(saved, conversation, userId, content);
                return new MessageSaveResult(saved, true);
            }
        } finally {
            messageLocks.remove(lockKey, lock);
        }
    }

    private boolean shouldInvokeAi(AiConversationEntity conversation, String content) {
        return conversation.getType() == AiConversationType.PRIVATE || content.matches("(?is).*\\B@ia\\b.*");
    }

    private void requestAiAfterCommit(AiMessageEntity message, AiConversationEntity conversation, String userId, String content) {
        AiGenerationRequest request = new AiGenerationRequest(message.getId(), conversation.getId(),
                conversation.getProject().getId(), userId, content, conversation.getType());
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            orchestrator.request(request);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() { orchestrator.request(request); }
        });
    }

    private AiMessageEntity saveMessageLegacy(AiConversationEntity conversation, String userId, SendAiMessageRequest request) {
        String content = request == null || request.content() == null ? "" : request.content().trim();
        if (content.isBlank()) throw new IllegalArgumentException("El mensaje no puede estar vacío.");
        if (content.length() > maxMessageLength) throw new IllegalArgumentException("El mensaje no puede superar " + maxMessageLength + " caracteres.");
        UserEntity author = users.findById(userId).orElseThrow(AiConversationNotFoundException::new);
        LocalDateTime now = LocalDateTime.now();
        conversation.setUpdatedAt(now);
        conversations.save(conversation);
        return messages.save(AiMessageEntity.builder()
                .id(UUID.randomUUID().toString())
                .conversation(conversation)
                .authorUser(author)
                .authorType(AiMessageAuthorType.USER)
                .content(content)
                .createdAt(now)
                .build());
    }

    private AiConversationEntity getOrCreate(String projectId, String userId, AiConversationType type) {
        String scopeKey = type == AiConversationType.GROUP
                ? projectId + ":GROUP"
                : projectId + ":PRIVATE:" + userId;
        Object lock = conversationLocks.computeIfAbsent(scopeKey, ignored -> new Object());
        try {
            synchronized (lock) {
                return conversations.findByScopeKey(scopeKey).orElseGet(() -> createConversation(projectId, userId, type, scopeKey));
            }
        } finally {
            conversationLocks.remove(scopeKey, lock);
        }
    }

    private AiConversationEntity createConversation(String projectId, String userId, AiConversationType type, String scopeKey) {
        LocalDateTime now = LocalDateTime.now();
        AiConversationEntity value = AiConversationEntity.builder()
                .id(UUID.randomUUID().toString())
                .project(ProjectEntity.builder().id(projectId).build())
                .type(type)
                .ownerUser(userId == null ? null : UserEntity.builder().id(userId).build())
                .scopeKey(scopeKey)
                .createdAt(now)
                .updatedAt(now)
                .build();
        try {
            return conversations.saveAndFlush(value);
        } catch (DataIntegrityViolationException exception) {
            return conversations.findByScopeKey(scopeKey).orElseThrow(() -> exception);
        }
    }

    private AiConversationEntity authorizedConversation(String projectId, String userId, String conversationId) {
        if (conversationId == null || conversationId.isBlank()) throw new IllegalArgumentException("conversationId es obligatorio.");
        AiConversationEntity conversation = conversations.findById(conversationId)
                .orElseThrow(AiConversationNotFoundException::new);
        if (!projectId.equals(conversation.getProject().getId())) throw new AiConversationNotFoundException();
        if (conversation.getType() == AiConversationType.PRIVATE) {
            if (conversation.getOwnerUser() == null || !userId.equals(conversation.getOwnerUser().getId())) {
                throw new AiConversationNotFoundException();
            }
        } else {
            access.requireMember(projectId, userId);
        }
        return conversation;
    }

    private AiConversationEntity authorizedGroupConversation(String projectId, String userId, String conversationId) {
        AiConversationEntity conversation = authorizedConversation(projectId, userId, conversationId);
        if (conversation.getType() != AiConversationType.GROUP) throw new AiConversationNotFoundException();
        return conversation;
    }

    private String encodeCursor(AiMessageEntity message) {
        String raw = message.getCreatedAt() + "|" + message.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decodeCursor(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 2);
            if (parts.length != 2) throw new IllegalArgumentException();
            return new Cursor(LocalDateTime.parse(parts[0]), parts[1]);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Cursor inválido.");
        }
    }

    private record Cursor(LocalDateTime createdAt, String id) { }

    public record MessageSaveResult(AiMessageEntity message, boolean created) { }
}
