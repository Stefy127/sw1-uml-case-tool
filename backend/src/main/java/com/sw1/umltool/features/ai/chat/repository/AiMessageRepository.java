package com.sw1.umltool.features.ai.chat.repository;

import com.sw1.umltool.features.ai.chat.model.AiMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AiMessageRepository extends JpaRepository<AiMessageEntity, String> {
    Optional<AiMessageEntity> findByConversation_IdAndClientMessageId(String conversationId, String clientMessageId);
    Optional<AiMessageEntity> findByResponseToMessageId(String responseToMessageId);
    @Query("select m from AiMessageEntity m left join fetch m.authorUser " +
            "where m.conversation.id = :conversationId " +
            "order by m.createdAt desc, m.id desc")
    List<AiMessageEntity> findRecent(@Param("conversationId") String conversationId, Pageable pageable);

    @Query("select m from AiMessageEntity m left join fetch m.authorUser " +
            "where m.conversation.id = :conversationId " +
            "and (m.createdAt < :beforeCreatedAt or (m.createdAt = :beforeCreatedAt and m.id < :beforeId)) " +
            "order by m.createdAt desc, m.id desc")
    List<AiMessageEntity> findBefore(@Param("conversationId") String conversationId,
                                     @Param("beforeCreatedAt") LocalDateTime beforeCreatedAt,
                                     @Param("beforeId") String beforeId,
                                     Pageable pageable);
}
