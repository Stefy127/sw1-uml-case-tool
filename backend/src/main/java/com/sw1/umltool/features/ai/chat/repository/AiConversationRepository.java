package com.sw1.umltool.features.ai.chat.repository;

import com.sw1.umltool.features.ai.chat.model.AiConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiConversationRepository extends JpaRepository<AiConversationEntity, String> {
    Optional<AiConversationEntity> findByScopeKey(String scopeKey);
}
