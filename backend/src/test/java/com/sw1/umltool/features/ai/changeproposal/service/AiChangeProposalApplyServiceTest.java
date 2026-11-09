package com.sw1.umltool.features.ai.changeproposal.service;

import com.sw1.umltool.features.ai.changeproposal.dto.AiChangeProposalApplyOperation;
import com.sw1.umltool.features.ai.changeproposal.dto.AiChangeProposalApplyRequest;
import com.sw1.umltool.features.ai.changeproposal.dto.AiChangeProposalApplyResponse;
import com.sw1.umltool.features.ai.changeproposal.repository.AiChangeProposalApplicationRepository;
import com.sw1.umltool.features.collaboration.websocket.CollaborationBroadcastService;
import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.persistence.DiagramEntity;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;
import com.sw1.umltool.features.diagram.operation.DiagramOperation;
import com.sw1.umltool.features.diagram.operation.OperationExecutionResult;
import com.sw1.umltool.features.diagram.repository.DiagramRepository;
import com.sw1.umltool.features.diagram.service.DiagramStateSerializer;
import com.sw1.umltool.features.diagram.service.PersistentDiagramOperationService;
import tools.jackson.databind.ObjectMapper;
import com.sw1.umltool.features.project.model.ProjectMemberRole;
import com.sw1.umltool.features.project.service.ProjectAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiChangeProposalApplyServiceTest {
    private DiagramRepository diagrams;
    private ProjectAccessService access;
    private PersistentDiagramOperationService operations;
    private CollaborationBroadcastService broadcaster;
    private AiChangeProposalApplicationRepository applications;
    private DiagramStateSerializer serializer;
    private AiChangeProposalApplyService service;

    @BeforeEach
    void setUp() {
        diagrams = mock(DiagramRepository.class);
        access = mock(ProjectAccessService.class);
        operations = mock(PersistentDiagramOperationService.class);
        broadcaster = mock(CollaborationBroadcastService.class);
        applications = mock(AiChangeProposalApplicationRepository.class);
        serializer = new DiagramStateSerializer(new ObjectMapper());
        service = new AiChangeProposalApplyService(diagrams, access, operations, broadcaster, applications, serializer);
        when(diagrams.findByIdForUpdate("diagram-1"))
                .thenReturn(Optional.of(DiagramEntity.builder().id("diagram-1").projectId("project-1").build()));
        when(access.resolveRole("project-1", "user-1")).thenReturn(ProjectMemberRole.OWNER);
        when(operations.executeBatch(eq("diagram-1"), any(), eq("user-1"), eq("AI")))
                .thenReturn(result());
    }

    @Test
    void appliesAddAttributeThroughOfficialBatchPipelineAndBroadcastsAfterSuccess() {
        AiChangeProposalApplyResponse response = service.apply("project-1", "user-1", request(
                new AiChangeProposalApplyOperation("proposal-1", "ADD_ATTRIBUTE", "class-1", null,
                        "codigoPedido", "String")));

        assertEquals(1, response.appliedOperations());
        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(operations).executeBatch(eq("diagram-1"), captor.capture(), eq("user-1"), eq("AI"));
        List<?> mapped = captor.getValue();
        assertEquals(1, mapped.size());
        assertEquals(com.sw1.umltool.features.diagram.operation.DiagramOperationType.ADD_ATTRIBUTE,
                ((DiagramOperation) mapped.get(0)).getType());
        verify(broadcaster).broadcastSnapshot(eq("diagram-1"), eq("user-1"),
                eq("AI_CHANGE_PROPOSALS_APPLIED"), any(UmlDiagram.class), any(DiagramViewState.class));
    }

    @Test
    void sameRequestIdIsIdempotentWithinTheRunningService() {
        AiChangeProposalApplyRequest request = request(
                new AiChangeProposalApplyOperation("proposal-1", "RENAME_ATTRIBUTE", "class-1", "attr-1",
                        "nombreCompleto", null));

        service.apply("project-1", "user-1", request);
        service.apply("project-1", "user-1", request);

        verify(operations).executeBatch(eq("diagram-1"), any(), eq("user-1"), eq("AI"));
        verify(broadcaster).broadcastSnapshot(eq("diagram-1"), eq("user-1"),
                eq("AI_CHANGE_PROPOSALS_APPLIED"), any(UmlDiagram.class), any(DiagramViewState.class));
    }

    @Test
    void persistedApplicationIsIdempotentAfterServiceRestart() {
        AiChangeProposalApplyRequest request = request(
                new AiChangeProposalApplyOperation("proposal-1", "ADD_ATTRIBUTE", "class-1", null,
                        "codigo", "String"));

        service.apply("project-1", "user-1", request);
        var captor = org.mockito.ArgumentCaptor.forClass(
                com.sw1.umltool.features.ai.changeproposal.model.persistence.AiChangeProposalApplicationEntity.class);
        verify(applications).save(captor.capture());
        when(applications.findByProjectIdAndUserIdAndDiagramIdAndRequestId(
                "project-1", "user-1", "diagram-1", "request-1"))
                .thenReturn(Optional.of(captor.getValue()));

        AiChangeProposalApplyService restarted = new AiChangeProposalApplyService(
                diagrams, access, operations, broadcaster, applications, serializer);
        AiChangeProposalApplyResponse replay = restarted.apply("project-1", "user-1", request);

        assertEquals(1, replay.appliedOperations());
        verify(operations).executeBatch(eq("diagram-1"), any(), eq("user-1"), eq("AI"));
        verify(broadcaster).broadcastSnapshot(eq("diagram-1"), eq("user-1"),
                eq("AI_CHANGE_PROPOSALS_APPLIED"), any(UmlDiagram.class), any(DiagramViewState.class));
    }

    @Test
    void viewerCannotApplyProposal() {
        when(access.resolveRole("project-1", "user-1")).thenReturn(ProjectMemberRole.VIEWER);

        assertThrows(RuntimeException.class, () -> service.apply("project-1", "user-1", request(
                new AiChangeProposalApplyOperation("proposal-1", "ADD_ATTRIBUTE", "class-1", null,
                        "codigo", "String"))));

        verify(operations, never()).executeBatch(any(), any(), any(), any());
        verify(broadcaster, never()).broadcastSnapshot(any(), any(), any(), any(), any());
    }

    @Test
    void unsupportedOperationIsRejectedBeforePersistence() {
        assertThrows(IllegalArgumentException.class, () -> service.apply("project-1", "user-1", request(
                new AiChangeProposalApplyOperation("proposal-1", "DELETE_CLASS", "class-1", null,
                        null, null))));

        verify(operations, never()).executeBatch(any(), any(), any(), any());
        verify(broadcaster, never()).broadcastSnapshot(any(), any(), any(), any(), any());
    }

    private AiChangeProposalApplyRequest request(AiChangeProposalApplyOperation operation) {
        return new AiChangeProposalApplyRequest("diagram-1", 0, "request-1", true, List.of(operation));
    }

    private OperationExecutionResult result() {
        return OperationExecutionResult.builder()
                .operationId("operation-1")
                .diagramId("diagram-1")
                .previousVersion(0)
                .newVersion(1)
                .diagram(UmlDiagram.builder().id("diagram-1").version(1).build())
                .viewState(DiagramViewState.builder().diagramId("diagram-1").build())
                .build();
    }
}
