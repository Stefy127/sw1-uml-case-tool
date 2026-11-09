package com.sw1.umltool.features.diagram.history.service;

import com.sw1.umltool.features.collaboration.websocket.CollaborationBroadcastService;
import com.sw1.umltool.features.diagram.history.model.DiagramHistoryEntity;
import com.sw1.umltool.features.diagram.history.repository.DiagramHistoryRepository;
import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.persistence.DiagramEntity;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;
import com.sw1.umltool.features.diagram.repository.DiagramRepository;
import com.sw1.umltool.features.diagram.service.DiagramStateSerializer;
import com.sw1.umltool.features.project.service.ProjectAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiagramHistoryServiceTest {
    private DiagramHistoryRepository history;
    private DiagramRepository diagrams;
    private ProjectAccessService access;
    private DiagramHistoryService service;
    private DiagramStateSerializer serializer;
    private DiagramEntity diagram;

    @BeforeEach
    void setUp() {
        history = mock(DiagramHistoryRepository.class);
        diagrams = mock(DiagramRepository.class);
        access = mock(ProjectAccessService.class);
        serializer = new DiagramStateSerializer(new ObjectMapper());
        service = new DiagramHistoryService(history, diagrams, serializer, access,
                mock(CollaborationBroadcastService.class), new DiagramHistoryDescriptionService(serializer));
        diagram = DiagramEntity.builder().id("diagram-1").projectId("project-1").version(1)
                .canonicalModelJson(canonical(1)).viewStateJson(viewState()).build();
        when(history.findByDiagramIdAndStateAndAction("diagram-1", "UNDONE", "APPLY"))
                .thenReturn(List.of());
        when(history.save(any(DiagramHistoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void persistsBeforeAndAfterSnapshotsForAnAppliedOperation() {
        service.recordApplied(diagram, "user-1", "operation-1", "ADD_ATTRIBUTE", "MANUAL", 0,
                canonical(0), viewState(), canonical(1), viewState());

        var captor = org.mockito.ArgumentCaptor.forClass(DiagramHistoryEntity.class);
        verify(history).save(captor.capture());
        assertEquals(0, captor.getValue().getPreviousVersion());
        assertEquals(1, captor.getValue().getNewVersion());
        assertEquals(canonical(0), captor.getValue().getBeforeCanonicalModelJson());
        assertEquals("MANUAL", captor.getValue().getOrigin());
    }

    @Test
    void undoRestoresSnapshotAndIncrementsVersion() {
        DiagramHistoryEntity target = appliedHistory();
        when(diagrams.findByIdForUpdate("diagram-1")).thenReturn(Optional.of(diagram));
        when(history.findFirstByDiagramIdAndStateAndActionOrderByNewVersionDesc("diagram-1", "ACTIVE", "APPLY"))
                .thenReturn(Optional.of(target));

        var response = service.undo("diagram-1", "user-1");

        assertEquals(2, diagram.getVersion());
        assertEquals(2, serializer.deserializeCanonical(diagram.getCanonicalModelJson()).getVersion());
        assertEquals("UNDO", response.action());
        assertEquals("UNDONE", target.getState());
    }

    private DiagramHistoryEntity appliedHistory() {
        DiagramHistoryEntity value = new DiagramHistoryEntity();
        value.setId("history-1");
        value.setProjectId("project-1");
        value.setDiagramId("diagram-1");
        value.setUserId("user-1");
        value.setOperationId("operation-1");
        value.setOperationType("ADD_ATTRIBUTE");
        value.setOrigin("MANUAL");
        value.setAction("APPLY");
        value.setState("ACTIVE");
        value.setPreviousVersion(0);
        value.setNewVersion(1);
        value.setBeforeCanonicalModelJson(canonical(0));
        value.setAfterCanonicalModelJson(canonical(1));
        value.setBeforeViewStateJson(viewState());
        value.setAfterViewStateJson(viewState());
        return value;
    }

    private String canonical(long version) {
        return serializer.serializeCanonical(UmlDiagram.builder().id("diagram-1").version(version).build());
    }

    private String viewState() {
        return serializer.serializeViewState(DiagramViewState.builder().diagramId("diagram-1").build());
    }
}
