package com.sw1.umltool.features.project.repository;

import com.sw1.umltool.features.project.model.ProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<ProjectEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProjectEntity p where p.id = :projectId")
    Optional<ProjectEntity> findByIdForUpdate(@Param("projectId") String projectId);

    List<ProjectEntity> findByOwnerUserId(String ownerUserId);
    Optional<ProjectEntity> findByShareToken(String shareToken);
}
