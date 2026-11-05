package com.sw1.umltool.features.ai.memory.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_memories", indexes = {
        @Index(name = "idx_ai_memory_project_scope", columnList = "project_id,scope,active"),
        @Index(name = "idx_ai_memory_owner", columnList = "project_id,scope,owner_user_id,active")
}, uniqueConstraints = @UniqueConstraint(name = "uk_ai_memory_project_scope_owner_content",
        columnNames = {"project_id", "scope", "owner_user_id", "content"}))
@Check(constraints = "(scope <> 'PRIVATE' OR owner_user_id IS NOT NULL) AND (scope <> 'PROJECT' OR owner_user_id IS NULL)")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMemoryEntity {
    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "project_id", nullable = false, length = 36)
    private String projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiMemoryScope scope;

    @Column(name = "owner_user_id", length = 36)
    private String ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiMemoryType type;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(name = "created_by_user_id", nullable = false, length = 36)
    private String createdByUserId;

    @Column(name = "source_message_id", length = 36)
    private String sourceMessageId;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
