package com.sw1.umltool.features.ai.memory.service;

import com.sw1.umltool.features.ai.memory.dto.AiMemoryResponse;
import com.sw1.umltool.features.ai.memory.dto.CreateAiMemoryRequest;
import com.sw1.umltool.features.ai.memory.dto.UpdateAiMemoryRequest;
import com.sw1.umltool.features.ai.memory.exception.AiMemoryNotFoundException;
import com.sw1.umltool.features.ai.memory.model.AiMemoryEntity;
import com.sw1.umltool.features.ai.memory.model.AiMemoryScope;
import com.sw1.umltool.features.ai.memory.repository.AiMemoryRepository;
import com.sw1.umltool.features.project.model.ProjectMemberRole;
import com.sw1.umltool.features.project.service.ProjectAccessService;
import com.sw1.umltool.features.project.exception.ProjectForbiddenException;
import com.sw1.umltool.features.collaboration.websocket.CollaborationBroadcastService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AiMemoryService {
    private final AiMemoryRepository memories;
    private final ProjectAccessService access;
    private final CollaborationBroadcastService broadcaster;
    private final int maxItems;
    private final int maxContentLength;

    public AiMemoryService(AiMemoryRepository memories, ProjectAccessService access,
                           CollaborationBroadcastService broadcaster,
                           @Value("${ai.memory.max-items:10}") int maxItems,
                           @Value("${ai.memory.max-content-length:2000}") int maxContentLength) {
        this.memories = memories;
        this.access = access;
        this.broadcaster = broadcaster;
        this.maxItems = Math.max(1, maxItems);
        this.maxContentLength = Math.max(1, maxContentLength);
    }

    @Transactional(readOnly = true)
    public List<AiMemoryResponse> list(String projectId, String userId, AiMemoryScope scope) {
        access.requireMember(projectId, userId);
        List<AiMemoryEntity> values = scope == AiMemoryScope.PRIVATE
                ? memories.findByProjectIdAndScopeAndOwnerUserIdAndActiveTrueOrderByUpdatedAtDesc(
                        projectId, scope, userId, PageRequest.of(0, maxItems))
                : memories.findByProjectIdAndScopeAndActiveTrueOrderByUpdatedAtDesc(
                        projectId, scope, PageRequest.of(0, maxItems));
        Map<String, AiMemoryResponse> unique = new LinkedHashMap<>();
        values.stream().map(AiMemoryResponse::from)
                .forEach(value -> unique.putIfAbsent(duplicateKey(value.content()), value));
        return List.copyOf(unique.values());
    }

    private String duplicateKey(String content) {
        return content.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public AiMemoryResponse create(String projectId, String userId, CreateAiMemoryRequest request) {
        requireWritableScope(projectId, userId, request.scope());
        access.lockProject(projectId);
        String content = normalizeContent(request.content());
        List<AiMemoryEntity> matches = request.scope() == AiMemoryScope.PRIVATE
                ? memories.findByProjectIdAndScopeAndOwnerUserIdAndContentIgnoreCaseOrderByUpdatedAtDesc(
                        projectId, request.scope(), userId, content)
                : memories.findByProjectIdAndScopeAndOwnerUserIdIsNullAndContentIgnoreCaseOrderByUpdatedAtDesc(
                        projectId, request.scope(), content);
        if (matches.stream().anyMatch(AiMemoryEntity::isActive)) {
            throw new IllegalArgumentException("Ese recuerdo ya existe en este ámbito.");
        }
        if (!matches.isEmpty()) {
            AiMemoryEntity value = matches.get(0);
            value.setType(request.type());
            value.setContent(content);
            value.setSourceMessageId(blankToNull(request.sourceMessageId()));
            value.setActive(true);
            value.setUpdatedAt(LocalDateTime.now());
            AiMemoryResponse response = AiMemoryResponse.from(memories.save(value));
            if (value.getScope() == AiMemoryScope.PROJECT) {
                broadcastProjectMemoryAfterCommit("AI_MEMORY_PROJECT_CREATED", response);
            }
            return response;
        }
        LocalDateTime now = LocalDateTime.now();
        AiMemoryEntity value = AiMemoryEntity.builder()
                .id(UUID.randomUUID().toString())
                .projectId(projectId)
                .scope(request.scope())
                .ownerUserId(request.scope() == AiMemoryScope.PRIVATE ? userId : null)
                .type(request.type())
                .content(content)
                .createdByUserId(userId)
                .sourceMessageId(blankToNull(request.sourceMessageId()))
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
        AiMemoryResponse response = AiMemoryResponse.from(memories.save(value));
        if (value.getScope() == AiMemoryScope.PROJECT) broadcastProjectMemoryAfterCommit("AI_MEMORY_PROJECT_CREATED", response);
        return response;
    }

    @Transactional
    public AiMemoryResponse update(String projectId, String userId, String memoryId, UpdateAiMemoryRequest request) {
        AiMemoryEntity value = findAuthorized(projectId, userId, memoryId);
        requireWritableScope(projectId, userId, value.getScope());
        value.setType(request.type());
        value.setContent(normalizeContent(request.content()));
        value.setUpdatedAt(LocalDateTime.now());
        AiMemoryResponse response = AiMemoryResponse.from(memories.save(value));
        if (value.getScope() == AiMemoryScope.PROJECT) broadcastProjectMemoryAfterCommit("AI_MEMORY_PROJECT_UPDATED", response);
        return response;
    }

    @Transactional
    public void delete(String projectId, String userId, String memoryId) {
        AiMemoryEntity value = findAuthorized(projectId, userId, memoryId);
        requireWritableScope(projectId, userId, value.getScope());
        value.setActive(false);
        value.setUpdatedAt(LocalDateTime.now());
        memories.save(value);
        if (value.getScope() == AiMemoryScope.PROJECT) {
            afterCommit(() -> broadcaster.broadcastProjectMessage(projectId, java.util.Map.of(
                    "type", "AI_MEMORY_PROJECT_DELETED", "projectId", projectId, "memoryId", memoryId)));
        }
    }

    private AiMemoryEntity findAuthorized(String projectId, String userId, String memoryId) {
        AiMemoryEntity value = memories.findByIdAndProjectIdAndActiveTrue(memoryId, projectId)
                .orElseThrow(AiMemoryNotFoundException::new);
        access.requireMember(projectId, userId);
        if (value.getScope() == AiMemoryScope.PRIVATE && !userId.equals(value.getOwnerUserId())) {
            throw new AiMemoryNotFoundException();
        }
        return value;
    }

    private void requireWritableScope(String projectId, String userId, AiMemoryScope scope) {
        access.requireMember(projectId, userId);
        if (scope == AiMemoryScope.PROJECT) {
            ProjectMemberRole role = access.resolveRole(projectId, userId);
            if (role != ProjectMemberRole.OWNER && role != ProjectMemberRole.EDITOR) {
                throw new ProjectForbiddenException();
            }
        }
    }

    private String normalizeContent(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException("El recuerdo no puede estar vacío.");
        if (normalized.length() > maxContentLength) {
            throw new IllegalArgumentException("El recuerdo supera el límite permitido.");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void broadcastProjectMemoryAfterCommit(String type, AiMemoryResponse response) {
        afterCommit(() -> broadcaster.broadcastProjectMessage(response.projectId(), java.util.Map.of(
                "type", type, "projectId", response.projectId(), "memory", response)));
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { action.run(); }
            });
        } else {
            action.run();
        }
    }
}
