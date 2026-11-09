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
import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.canonical.UmlClass;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;
import com.sw1.umltool.features.diagram.service.DiagramStateSerializer;
import com.sw1.umltool.features.diagram.service.PersistentDiagramOperationService;
import com.sw1.umltool.features.diagram.service.DiagramOperationService;
import com.sw1.umltool.features.diagram.operation.DiagramOperation;
import com.sw1.umltool.features.diagram.operation.DiagramOperationApplier;
import com.sw1.umltool.features.diagram.operation.DiagramOperationType;
import com.sw1.umltool.features.diagram.operation.DiagramStateCloner;
import com.sw1.umltool.features.diagram.operation.payload.AddAttributePayload;
import com.sw1.umltool.features.diagram.model.canonical.UmlAttribute;
import com.sw1.umltool.features.diagram.model.canonical.enums.Visibility;
import com.sw1.umltool.features.diagram.validation.CanonicalModelValidator;
import tools.jackson.databind.ObjectMapper;
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
    private DiagramStateSerializer diagramStateSerializer;
    private ProjectService service;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        projectMemberRepository = mock(ProjectMemberRepository.class);
        diagramRepository = mock(DiagramRepository.class);
        userRepository = mock(UserRepository.class);
        access = mock(ProjectAccessService.class);
        diagramStateSerializer = new DiagramStateSerializer(new ObjectMapper());
        service = new ProjectService(projectRepository, projectMemberRepository, userRepository, access, diagramRepository, diagramStateSerializer);
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
        UmlDiagram sourceCanonical = UmlDiagram.builder().id("diagram-1").name("Modelo")
                .version(4)
                .classes(new java.util.ArrayList<>(java.util.List.of(UmlClass.builder().id("class-1").name("Cliente").build())))
                .build();
        DiagramViewState sourceView = DiagramViewState.builder().diagramId("diagram-1").build();
        DiagramEntity diagram = DiagramEntity.builder()
                .id("diagram-1").projectId("project-1").name("Modelo")
                .version(4).canonicalModelJson(diagramStateSerializer.serializeCanonical(sourceCanonical))
                .viewStateJson(diagramStateSerializer.serializeViewState(sourceView))
                .build();
        when(diagramRepository.findByProjectId("project-1")).thenReturn(java.util.List.of(diagram));

        ProjectEntity copy = service.duplicateProject("project-1", "owner-1");

        assertEquals("CRM - Copia", copy.getName());
        assertEquals("Description", copy.getDescription());
        assertEquals("owner-1", copy.getOwnerUserId());
        assertFalse("project-1".equals(copy.getId()));
        assertEquals(com.sw1.umltool.features.project.model.ProjectShareMode.RESTRICTED, copy.getShareMode());
        assertEquals(null, copy.getShareToken());
        org.mockito.ArgumentCaptor<DiagramEntity> diagramCaptor = org.mockito.ArgumentCaptor.forClass(DiagramEntity.class);
        verify(diagramRepository).save(diagramCaptor.capture());
        DiagramEntity clonedDiagram = diagramCaptor.getValue();
        assertFalse("diagram-1".equals(clonedDiagram.getId()));
        assertEquals(copy.getId(), clonedDiagram.getProjectId());
        assertEquals(4, clonedDiagram.getVersion());
        assertEquals(clonedDiagram.getId(), diagramStateSerializer.deserializeCanonical(clonedDiagram.getCanonicalModelJson()).getId());
        assertEquals(clonedDiagram.getId(), diagramStateSerializer.deserializeViewState(clonedDiagram.getViewStateJson()).getDiagramId());
        assertEquals("diagram-1", diagramStateSerializer.deserializeCanonical(diagram.getCanonicalModelJson()).getId());
        assertEquals("diagram-1", diagramStateSerializer.deserializeViewState(diagram.getViewStateJson()).getDiagramId());
        verify(projectMemberRepository).save(org.mockito.ArgumentMatchers.argThat(member ->
                copy.getId().equals(member.getProjectId())
                        && "owner-1".equals(member.getUserId())
                        && ProjectMemberRole.OWNER.equals(member.getRole())));
        assertEquals("Modelo", clonedDiagram.getName());
        assertEquals("Cliente", diagramStateSerializer.deserializeCanonical(clonedDiagram.getCanonicalModelJson())
                .getClasses().get(0).getName());
    }

    @Test
    void duplicatedDiagramAcceptsManualOperationUsingItsPersistedIdentity() {
        ProjectEntity original = project();
        when(projectRepository.findById("project-1")).thenReturn(java.util.Optional.of(original));
        UmlDiagram sourceCanonical = UmlDiagram.builder().id("diagram-1").name("Modelo").version(0)
                .classes(new java.util.ArrayList<>(java.util.List.of(UmlClass.builder()
                        .id("class-1").name("Pedido").build())))
                .build();
        DiagramEntity source = DiagramEntity.builder().id("diagram-1").projectId("project-1")
                .name("Modelo").version(0)
                .canonicalModelJson(diagramStateSerializer.serializeCanonical(sourceCanonical))
                .viewStateJson(diagramStateSerializer.serializeViewState(DiagramViewState.builder().diagramId("diagram-1").build()))
                .build();
        when(diagramRepository.findByProjectId("project-1")).thenReturn(java.util.List.of(source));

        service.duplicateProject("project-1", "owner-1");
        org.mockito.ArgumentCaptor<DiagramEntity> captor = org.mockito.ArgumentCaptor.forClass(DiagramEntity.class);
        verify(diagramRepository).save(captor.capture());
        DiagramEntity duplicate = captor.getValue();
        when(diagramRepository.findByIdForUpdate(duplicate.getId())).thenReturn(java.util.Optional.of(duplicate));

        PersistentDiagramOperationService operationService = new PersistentDiagramOperationService(
                diagramRepository, diagramStateSerializer,
                new DiagramOperationService(new DiagramOperationApplier(), new CanonicalModelValidator(), new DiagramStateCloner()));
        operationService.execute(duplicate.getId(), DiagramOperation.builder()
                .operationId("operation-1").diagramId(duplicate.getId()).userId("owner-1").baseVersion(0)
                .type(DiagramOperationType.ADD_ATTRIBUTE)
                .payload(AddAttributePayload.builder().classId("class-1").attribute(UmlAttribute.builder()
                        .id("attribute-1").name("Apellido").type("String").visibility(Visibility.PUBLIC).build()).build())
                .build());

        UmlDiagram updated = diagramStateSerializer.deserializeCanonical(duplicate.getCanonicalModelJson());
        assertEquals(duplicate.getId(), updated.getId());
        assertEquals(1, updated.getVersion());
        assertEquals("Apellido", updated.getClasses().get(0).getAttributes().get(0).getName());
        assertEquals("diagram-1", diagramStateSerializer.deserializeCanonical(source.getCanonicalModelJson()).getId());
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
