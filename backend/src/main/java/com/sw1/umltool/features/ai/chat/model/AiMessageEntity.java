package com.sw1.umltool.features.ai.chat.model;

import com.sw1.umltool.features.auth.model.UserEntity;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_messages", indexes = {
        @Index(name = "idx_ai_message_conversation_cursor", columnList = "conversation_id,created_at,id"),
        @Index(name = "uk_ai_message_client_id", columnList = "conversation_id,client_message_id", unique = true)
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMessageEntity {
    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private AiConversationEntity conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_user_id")
    private UserEntity authorUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_type", nullable = false, length = 20)
    private AiMessageAuthorType authorType;

    @Column(nullable = false, length = 4000)
    private String content;

    @Column(name = "client_message_id", length = 100)
    private String clientMessageId;

    @Column(name = "response_to_message_id", unique = true, length = 36)
    private String responseToMessageId;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
