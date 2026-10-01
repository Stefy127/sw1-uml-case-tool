package com.sw1.umltool.features.project.service;

import com.sw1.umltool.features.project.model.ProjectEntity;
import com.sw1.umltool.features.project.model.ProjectMemberEntity;
import com.sw1.umltool.features.project.model.ProjectMemberRole;
import com.sw1.umltool.features.project.repository.ProjectMemberRepository;
import com.sw1.umltool.features.project.repository.ProjectRepository;
import com.sw1.umltool.features.project.dto.UpdateProjectRequest;
import com.sw1.umltool.features.project.exception.ProjectForbiddenException;
import com.sw1.umltool.features.project.exception.ProjectNotFoundException;
import com.sw1.umltool.features.diagram.repository.DiagramRepository;
import com.sw1.umltool.features.diagram.model.persistence.DiagramEntity;
import com.sw1.umltool.features.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectServiceTest {

    private ProjectRepository projectRepository;
    private ProjectMemberRepository projectMemberRepository;
    private DiagramRepository diagramRepository;
    private UserRepository userRepository;
    private ProjectAccessService access;
    private ProjectService service;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        projectMemberRepository = mock(ProjectMemberRepository.class);
        diagramRepository = mock(DiagramRepository.class);
        userRepository = mock(UserRepository.class);
        access = mock(ProjectAccessService.class);
        service = new ProjectService(projectRepository, projectMemberRepository, userRepository, access, diagramRepository);
        when(projectRepository.save(any(ProjectEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(projectMemberRepository.save(any(ProjectMemberEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsProjectAndOwnerMember() {
        ProjectEntity project = service.createProject("CRM", "Description", "user-1");

        assertNotNull(project.getId());
        assertFalse(project.getId().isBlank());
        assertEquals("CRM", project.getName());
        verify(projectRepository).save(project);
        verify(projectMemberRepository).save(org.mockito.ArgumentMatchers.argThat(member ->
                project.getId().equals(member.getProjectId())
                        && "user-1".equals(member.getUserId())
                        && ProjectMemberRole.OWNER.equals(member.getRole())));
    }

    @Test
    void emptyNameFails() {
        assertThrows(IllegalArgumentException.class, () -> service.createProject("", "Description", "user-1"));
    }

    @Test
    void emptyOwnerFails() {
        assertThrows(IllegalArgumentException.class, () -> service.createProject("CRM", "Description", ""));
    }

    @Test
    void ownerUpdatesNameAndDescription() {
        ProjectEntity project = project();
        when(projectRepository.findById("project-1")).thenReturn(java.util.Optional.of(project));
        UpdateProjectRequest request = new UpdateProjectRequest();
        request.setName(" Nuevo ");
        request.setDescription("Updated description");

        ProjectEntity updated = service.updateProject("project-1", "owner-1", request);

        assertEquals("Nuevo", updated.getName());
        assertEquals("Updated description", updated.getDescription());
        verify(projectRepository).save(project);
    }

    @Test
    void blankOrTooLongNameFails() {
        ProjectEntity project = project();
        when(projectRepository.findById("project-1")).thenReturn(java.util.Optional.of(project));
        UpdateProjectRequest blank = new UpdateProjectRequest();
        blank.setName("  ");
        assertThrows(IllegalArgumentException.class, () -> service.updateProject("project-1", "owner-1", blank));
        UpdateProjectRequest longName = new UpdateProjectRequest();
        longName.setName("x".repeat(151));
        assertThrows(IllegalArgumentException.class, () -> service.updateProject("project-1", "owner-1", longName));
    }

    @Test
    void editorIsForbiddenFromUpdating() {
        ProjectEntity project = project();
        when(projectRepository.findById("project-1")).thenReturn(java.util.Optional.of(project));
        org.mockito.Mockito.doThrow(new ProjectForbiddenException()).when(access).requireOwner("project-1", "editor-1");
        UpdateProjectRequest request = new UpdateProjectRequest();
        request.setName("Updated");
        assertThrows(ProjectForbiddenException.class, () -> service.updateProject("project-1", "editor-1", request));
    }

    @Test
    void ownerDeletesDiagramsMembersAndProject() {
        when(projectRepository.existsById("project-1")).thenReturn(true);

        service.deleteProject("project-1", "owner-1");

        verify(diagramRepository).deleteByProjectId("project-1");
        verify(projectMemberRepository).deleteByProjectId("project-1");
        verify(projectRepository).deleteById("project-1");
    }

    @Test
    void deletingMissingProjectReturnsNotFound() {
        when(projectRepository.existsById("missing")).thenReturn(false);
        assertThrows(ProjectNotFoundException.class, () -> service.deleteProject("missing", "owner-1"));
    }

    @Test
    void editorIsForbiddenFromDeleting() {
        when(projectRepository.existsById("project-1")).thenReturn(true);
        org.mockito.Mockito.doThrow(new ProjectForbiddenException()).when(access).requireOwner("project-1", "editor-1");
        assertThrows(ProjectForbiddenException.class, () -> service.deleteProject("project-1", "editor-1"));
        org.mockito.Mockito.verifyNoInteractions(diagramRepository);
    }

    @Test
    void ownerDuplicatesProjectAndDiagramsWithoutSharing() {
        ProjectEntity original = project();
        original.setShareToken("original-token");
        when(projectRepository.findById("project-1")).thenReturn(java.util.Optional.of(original));
        DiagramEntity diagram = DiagramEntity.builder()
                .id("diagram-1").projectId("project-1").name("Modelo")
                .version(4).canonicalModelJson("{\"id\":\"class-1\"}")
                .viewStateJson("{\"diagramId\":\"diagram-1\"}")
                .build();
        when(diagramRepository.findByProjectId("project-1")).thenReturn(java.util.List.of(diagram));

        ProjectEntity copy = service.duplicateProject("project-1", "owner-1");

        assertEquals("CRM - Copia", copy.getName());
        assertEquals("Description", copy.getDescription());
        assertEquals("owner-1", copy.getOwnerUserId());
        assertFalse("project-1".equals(copy.getId()));
        assertEquals(com.sw1.umltool.features.project.model.ProjectShareMode.RESTRICTED, copy.getShareMode());
        assertEquals(null, copy.getShareToken());
        verify(projectMemberRepository).save(org.mockito.ArgumentMatchers.argThat(member ->
                copy.getId().equals(member.getProjectId())
                        && "owner-1".equals(member.getUserId())
                        && ProjectMemberRole.OWNER.equals(member.getRole())));
        verify(diagramRepository).save(org.mockito.ArgumentMatchers.argThat(clone ->
                !"diagram-1".equals(clone.getId())
                        && copy.getId().equals(clone.getProjectId())
                        && "Modelo".equals(clone.getName())
                        && clone.getVersion() == 4
                        && diagram.getCanonicalModelJson().equals(clone.getCanonicalModelJson())
                        && diagram.getViewStateJson().equals(clone.getViewStateJson())));
    }

    @Test
    void editorCannotDuplicateProject() {
        ProjectEntity original = project();
        when(projectRepository.findById("project-1")).thenReturn(java.util.Optional.of(original));
        org.mockito.Mockito.doThrow(new ProjectForbiddenException()).when(access).requireOwner("project-1", "editor-1");

        assertThrows(ProjectForbiddenException.class, () -> service.duplicateProject("project-1", "editor-1"));
        org.mockito.Mockito.verifyNoInteractions(diagramRepository);
    }

    @Test
    void duplicatingMissingProjectReturnsNotFound() {
        when(projectRepository.findById("missing")).thenReturn(java.util.Optional.empty());
        assertThrows(ProjectNotFoundException.class, () -> service.duplicateProject("missing", "owner-1"));
    }

    private ProjectEntity project() {
        return ProjectEntity.builder().id("project-1").name("CRM").description("Description").ownerUserId("owner-1").build();
    }
}
