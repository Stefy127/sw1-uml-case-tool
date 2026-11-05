package com.sw1.umltool.features.ai.chat.model;

import com.sw1.umltool.features.auth.model.UserEntity;
import com.sw1.umltool.features.project.model.ProjectEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_conversations",
        indexes = @Index(name = "idx_ai_conversation_project", columnList = "project_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_ai_conversation_scope", columnNames = "scope_key"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiConversationEntity {
    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiConversationType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private UserEntity ownerUser;

    @Column(name = "scope_key", nullable = false, updatable = false, length = 300)
    private String scopeKey;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
