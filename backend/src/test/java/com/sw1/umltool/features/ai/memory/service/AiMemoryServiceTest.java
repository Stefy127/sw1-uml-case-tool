package com.sw1.umltool.features.ai.memory.service;

import com.sw1.umltool.features.ai.memory.dto.CreateAiMemoryRequest;
import com.sw1.umltool.features.ai.memory.model.AiMemoryScope;
import com.sw1.umltool.features.ai.memory.model.AiMemoryType;
import com.sw1.umltool.features.ai.memory.model.AiMemoryEntity;
import com.sw1.umltool.features.ai.memory.repository.AiMemoryRepository;
import com.sw1.umltool.features.project.model.ProjectMemberEntity;
import com.sw1.umltool.features.project.model.ProjectMemberRole;
import com.sw1.umltool.features.project.service.ProjectAccessService;
import com.sw1.umltool.features.collaboration.websocket.CollaborationBroadcastService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiMemoryServiceTest {
    private AiMemoryRepository memories;
    private ProjectAccessService access;
    private CollaborationBroadcastService broadcaster;
    private AiMemoryService service;

    @BeforeEach
    void setUp() {
        memories = mock(AiMemoryRepository.class);
        access = mock(ProjectAccessService.class);
        broadcaster = mock(CollaborationBroadcastService.class);
        service = new AiMemoryService(memories, access, broadcaster, 10, 2000);
    }

    @Test
    void viewerCannotCreateProjectMemory() {
        when(access.resolveRole("project-1", "viewer")).thenReturn(ProjectMemberRole.VIEWER);
        when(access.requireMember("project-1", "viewer")).thenReturn(ProjectMemberEntity.builder().build());

        assertThrows(com.sw1.umltool.features.project.exception.ProjectForbiddenException.class, () -> service.create("project-1", "viewer",
                new CreateAiMemoryRequest(AiMemoryScope.PROJECT, AiMemoryType.NOTE, "Solo lectura", null)));
    }

    @Test
    void duplicateMemoryIsRejectedWithoutCreatingAnotherRow() {
        when(access.resolveRole("project-1", "editor")).thenReturn(ProjectMemberRole.EDITOR);
        when(access.requireMember("project-1", "editor")).thenReturn(ProjectMemberEntity.builder().build());
        when(memories.findByProjectIdAndScopeAndOwnerUserIdIsNullAndContentIgnoreCaseOrderByUpdatedAtDesc(
                "project-1", AiMemoryScope.PROJECT, "Usar UUID"))
                .thenReturn(List.of(AiMemoryEntity.builder().active(true).build()));

        assertThrows(IllegalArgumentException.class, () -> service.create("project-1", "editor",
                new CreateAiMemoryRequest(AiMemoryScope.PROJECT, AiMemoryType.CONVENTION, "Usar UUID", null)));
    }

    @Test
    void reactivatesDeletedProjectMemoryAndEmitsCreatedEvent() {
        when(access.resolveRole("project-1", "editor")).thenReturn(ProjectMemberRole.EDITOR);
        when(access.requireMember("project-1", "editor")).thenReturn(ProjectMemberEntity.builder().build());
        AiMemoryEntity deleted = AiMemoryEntity.builder()
                .id("memory-1")
                .projectId("project-1")
                .scope(AiMemoryScope.PROJECT)
                .type(AiMemoryType.CONVENTION)
                .content("Las clases UML deben utilizar nombres en singular")
                .createdByUserId("owner")
                .active(false)
                .createdAt(LocalDateTime.now().minusMinutes(2))
                .updatedAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(memories.findByProjectIdAndScopeAndOwnerUserIdIsNullAndContentIgnoreCaseOrderByUpdatedAtDesc(
                "project-1", AiMemoryScope.PROJECT, "Las clases UML deben utilizar nombres en singular"))
                .thenReturn(List.of(deleted));
        when(memories.save(deleted)).thenReturn(deleted);

        var response = service.create("project-1", "editor",
                new CreateAiMemoryRequest(AiMemoryScope.PROJECT, AiMemoryType.DECISION,
                        "Las clases UML deben utilizar nombres en singular", null));

        assertEquals("memory-1", response.id());
        assertEquals(AiMemoryType.DECISION, response.type());
        assertTrue(deleted.isActive());
        assertEquals("project-1", response.projectId());
        org.mockito.Mockito.verify(access).lockProject("project-1");
        org.mockito.Mockito.verify(broadcaster).broadcastProjectMessage(
                org.mockito.ArgumentMatchers.eq("project-1"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void privateMemoryReactivationIsScopedToOwner() {
        when(access.resolveRole("project-1", "user-1")).thenReturn(ProjectMemberRole.EDITOR);
        when(access.requireMember("project-1", "user-1")).thenReturn(ProjectMemberEntity.builder().build());
        when(memories.findByProjectIdAndScopeAndOwnerUserIdAndContentIgnoreCaseOrderByUpdatedAtDesc(
                "project-1", AiMemoryScope.PRIVATE, "user-1", "Preferir composition"))
                .thenReturn(List.of(AiMemoryEntity.builder()
                        .id("private-1").projectId("project-1").scope(AiMemoryScope.PRIVATE)
                        .ownerUserId("user-1").type(AiMemoryType.NOTE).content("Preferir composition")
                        .active(false).build()));
        AiMemoryEntity deleted = AiMemoryEntity.builder()
                .id("private-1").projectId("project-1").scope(AiMemoryScope.PRIVATE)
                .ownerUserId("user-1").type(AiMemoryType.NOTE).content("Preferir composition")
                .active(false).build();
        when(memories.findByProjectIdAndScopeAndOwnerUserIdAndContentIgnoreCaseOrderByUpdatedAtDesc(
                "project-1", AiMemoryScope.PRIVATE, "user-1", "Preferir composition"))
                .thenReturn(List.of(deleted));
        when(memories.save(deleted)).thenReturn(deleted);

        var response = service.create("project-1", "user-1",
                new CreateAiMemoryRequest(AiMemoryScope.PRIVATE, AiMemoryType.NOTE,
                        "Preferir composition", null));

        assertEquals("private-1", response.id());
        assertTrue(deleted.isActive());
        assertFalse(response.scope() == AiMemoryScope.PROJECT);
    }

    @Test
    void listReturnsOneMemoryForHistoricalCaseInsensitiveDuplicates() {
        when(access.requireMember("project-1", "editor")).thenReturn(ProjectMemberEntity.builder().build());
        when(memories.findByProjectIdAndScopeAndActiveTrueOrderByUpdatedAtDesc(
                eq("project-1"), eq(AiMemoryScope.PROJECT), any()))
                .thenReturn(List.of(
                        AiMemoryEntity.builder().id("newer").projectId("project-1").scope(AiMemoryScope.PROJECT)
                                .type(AiMemoryType.NOTE).content("Nombres en singular").active(true).build(),
                        AiMemoryEntity.builder().id("older").projectId("project-1").scope(AiMemoryScope.PROJECT)
                                .type(AiMemoryType.NOTE).content(" nombres EN SINGULAR ").active(true).build()));

        var result = service.list("project-1", "editor", AiMemoryScope.PROJECT);

        assertEquals(1, result.size());
        assertEquals("newer", result.get(0).id());
    }
}
