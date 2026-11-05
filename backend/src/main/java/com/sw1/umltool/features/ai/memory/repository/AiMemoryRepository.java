package com.sw1.umltool.features.ai.memory.repository;

import com.sw1.umltool.features.ai.memory.model.AiMemoryEntity;
import com.sw1.umltool.features.ai.memory.model.AiMemoryScope;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface AiMemoryRepository extends JpaRepository<AiMemoryEntity, String> {
    List<AiMemoryEntity> findByProjectIdAndScopeAndActiveTrueOrderByUpdatedAtDesc(
            String projectId, AiMemoryScope scope, Pageable pageable);

    List<AiMemoryEntity> findByProjectIdAndScopeAndOwnerUserIdAndActiveTrueOrderByUpdatedAtDesc(
            String projectId, AiMemoryScope scope, String ownerUserId, Pageable pageable);

    Optional<AiMemoryEntity> findByIdAndProjectIdAndActiveTrue(String id, String projectId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<AiMemoryEntity> findByProjectIdAndScopeAndOwnerUserIdAndContentIgnoreCaseOrderByUpdatedAtDesc(
            String projectId, AiMemoryScope scope, String ownerUserId, String content);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<AiMemoryEntity> findByProjectIdAndScopeAndOwnerUserIdIsNullAndContentIgnoreCaseOrderByUpdatedAtDesc(
            String projectId, AiMemoryScope scope, String content);
}
