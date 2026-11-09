package com.sw1.umltool.features.diagram.service;

import com.sw1.umltool.features.diagram.model.persistence.DiagramEntity;
import com.sw1.umltool.features.diagram.model.canonical.UmlClass;
import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;
import com.sw1.umltool.features.diagram.repository.DiagramRepository;
import com.sw1.umltool.features.project.repository.ProjectRepository;
import com.sw1.umltool.features.project.service.ProjectAccessService;
import com.sw1.umltool.features.diagram.validation.CanonicalModelValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class DiagramServiceTest {

    private DiagramRepository diagramRepository;
    private ProjectRepository projectRepository;
    private DiagramStateSerializer serializer;
    private DiagramService service;

    @BeforeEach
    void setUp() {
        diagramRepository = mock(DiagramRepository.class);
        projectRepository = mock(ProjectRepository.class);
        serializer = mock(DiagramStateSerializer.class);
        service = new DiagramService(diagramRepository, projectRepository, serializer, mock(ProjectAccessService.class), new CanonicalModelValidator());
        when(projectRepository.existsById("project-1")).thenReturn(true);
        when(serializer.serializeCanonical(any())).thenReturn("canonical-json");
        when(serializer.serializeViewState(any())).thenReturn("view-json");
        when(diagramRepository.save(any(DiagramEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsEmptyDiagramAtVersionZero() {
        DiagramEntity diagram = service.createDiagram("project-1", "Main");

        assertFalse(diagram.getId().isBlank());
        assertEquals("project-1", diagram.getProjectId());
        assertEquals("Main", diagram.getName());
        assertEquals(0, diagram.getVersion());
        assertEquals("canonical-json", diagram.getCanonicalModelJson());
        assertEquals("view-json", diagram.getViewStateJson());
    }

    @Test
    void missingProjectFails() {
        when(projectRepository.existsById("missing")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.createDiagram("missing", "Main"));
    }

    @Test
    void emptyNameFails() {
        assertThrows(IllegalArgumentException.class, () -> service.createDiagram("project-1", ""));
    }

    @Test
    void persistsInitialCanonicalModelAndViewStateWithSynchronizedIds() {
        UmlDiagram initial = UmlDiagram.builder().id("client-id").name("old").version(99)
                .classes(java.util.List.of(UmlClass.builder().id("class-1").name("Cliente").build())).build();
        DiagramViewState view = DiagramViewState.builder().diagramId("old-diagram").build();

        DiagramEntity diagram = service.createDiagram("project-1", "Ventas", initial, view);

        assertEquals(diagram.getId(), initial.getId());
        assertEquals(diagram.getId(), view.getDiagramId());
        assertEquals(0, initial.getVersion());
        assertEquals("Ventas", initial.getName());
        verify(serializer).serializeCanonical(initial);
        verify(serializer).serializeViewState(view);
    }
}
