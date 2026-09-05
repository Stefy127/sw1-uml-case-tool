package com.sw1.umltool.features.diagram.service;

import com.sw1.umltool.features.diagram.model.canonical.Multiplicity;
import com.sw1.umltool.features.diagram.model.canonical.UmlClass;
import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.canonical.UmlRelation;
import com.sw1.umltool.features.diagram.model.canonical.enums.RelationType;
import com.sw1.umltool.features.diagram.model.persistence.DiagramEntity;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;
import com.sw1.umltool.features.diagram.model.view.NodeViewState;
import com.sw1.umltool.features.diagram.model.view.RelationViewState;
import com.sw1.umltool.features.diagram.operation.DiagramOperation;
import com.sw1.umltool.features.diagram.operation.DiagramOperationApplier;
import com.sw1.umltool.features.diagram.operation.DiagramOperationType;
import com.sw1.umltool.features.diagram.operation.DiagramStateCloner;
import com.sw1.umltool.features.diagram.operation.OperationApplicationException;
import com.sw1.umltool.features.diagram.operation.VersionConflictException;
import com.sw1.umltool.features.diagram.operation.payload.CreateClassPayload;
import com.sw1.umltool.features.diagram.operation.payload.DeleteClassPayload;
import com.sw1.umltool.features.diagram.operation.payload.MoveClassPayload;
import com.sw1.umltool.features.diagram.operation.payload.RenameClassPayload;
import com.sw1.umltool.features.diagram.operation.payload.UpdateClassStylePayload;
import com.sw1.umltool.features.diagram.repository.DiagramRepository;
import com.sw1.umltool.features.diagram.validation.CanonicalModelValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersistentDiagramOperationServiceTest {

    private DiagramRepository repository;
    private DiagramStateSerializer serializer;
    private PersistentDiagramOperationService service;
    private DiagramEntity entity;

    @BeforeEach
    void setUp() {
        repository = mock(DiagramRepository.class);
        serializer = new DiagramStateSerializer(new ObjectMapper());
        DiagramOperationService operationService = new DiagramOperationService(new DiagramOperationApplier(),
                new CanonicalModelValidator(), new DiagramStateCloner());
        service = new PersistentDiagramOperationService(repository, serializer, operationService);
        entity = persistedEntity(baseDiagram(), baseViewState());
        when(repository.findById("diagram-1")).thenReturn(Optional.of(entity));
        when(repository.save(any(DiagramEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void renamePersistsNewCanonicalStateAndVersion() {
        var result = service.execute("diagram-1", operation("op-1", 0, DiagramOperationType.RENAME_CLASS,
                RenameClassPayload.builder().classId("class-1").name("Client").build()));

        assertEquals(1, result.getNewVersion());
        assertEquals(1, entity.getVersion());
        assertEquals("Client", serializer.deserializeCanonical(entity.getCanonicalModelJson())
                .getClasses().get(0).getName());
        verify(repository).save(entity);
    }

    @Test
    void movePersistsOnlyVisualChangeAndIncrementsVersion() {
        service.execute("diagram-1", operation("op-1", 0, DiagramOperationType.MOVE_CLASS,
                MoveClassPayload.builder().classId("class-1").x(50).y(60).build()));

        assertEquals(1, entity.getVersion());
        assertEquals(1, serializer.deserializeCanonical(entity.getCanonicalModelJson()).getVersion());
        assertEquals("Customer", serializer.deserializeCanonical(entity.getCanonicalModelJson())
                .getClasses().get(0).getName());
        assertEquals(50, serializer.deserializeViewState(entity.getViewStateJson()).getNodes().get(0).getX());
        assertEquals(60, serializer.deserializeViewState(entity.getViewStateJson()).getNodes().get(0).getY());
    }

    @Test
    void stylePersistsOnlyInViewStateAndIncrementsVersion() {
        UmlDiagram original = serializer.deserializeCanonical(entity.getCanonicalModelJson());

        service.execute("diagram-1", operation("op-1", 0, DiagramOperationType.UPDATE_CLASS_STYLE,
                UpdateClassStylePayload.builder().classId("class-1").headerColor("#eee8ff")
                        .bodyColor("#ffffff").borderColor("#8a7be8").build()));

        DiagramViewState viewState = serializer.deserializeViewState(entity.getViewStateJson());
        assertEquals("#eee8ff", viewState.getNodes().get(0).getHeaderColor());
        assertEquals("#ffffff", viewState.getNodes().get(0).getBodyColor());
        assertEquals("#8a7be8", viewState.getNodes().get(0).getBorderColor());
        assertEquals(original.getClasses().get(0).getName(),
                serializer.deserializeCanonical(entity.getCanonicalModelJson()).getClasses().get(0).getName());
        assertEquals(1, entity.getVersion());
    }

    @Test
    void createClassPersistsClassAndNode() {
        service.execute("diagram-1", operation("op-1", 0, DiagramOperationType.CREATE_CLASS,
                CreateClassPayload.builder().classId("class-2").name("Order").width(100).height(80).build()));

        assertEquals("Order", serializer.deserializeCanonical(entity.getCanonicalModelJson())
                .getClasses().get(1).getName());
        assertEquals("class-2", serializer.deserializeViewState(entity.getViewStateJson())
                .getNodes().get(1).getClassId());
    }

    @Test
    void deleteClassPersistsRemovalOfClassNodeAndRelations() {
        UmlRelation relation = relation("relation-1", "class-1", "class-2");
        UmlDiagram diagram = serializer.deserializeCanonical(entity.getCanonicalModelJson());
        diagram.getClasses().add(UmlClass.builder().id("class-2").name("Order").build());
        diagram.getRelations().add(relation);
        DiagramViewState viewState = serializer.deserializeViewState(entity.getViewStateJson());
        viewState.getNodes().add(NodeViewState.builder().classId("class-2").build());
        viewState.getRelations().add(RelationViewState.builder().relationId("relation-1").build());
        entity.setCanonicalModelJson(serializer.serializeCanonical(diagram));
        entity.setViewStateJson(serializer.serializeViewState(viewState));

        service.execute("diagram-1", operation("op-1", 0, DiagramOperationType.DELETE_CLASS,
                DeleteClassPayload.builder().classId("class-1").build()));

        UmlDiagram resultDiagram = serializer.deserializeCanonical(entity.getCanonicalModelJson());
        DiagramViewState resultView = serializer.deserializeViewState(entity.getViewStateJson());
        assertTrue(resultDiagram.getClasses().stream().noneMatch(umlClass -> "class-1".equals(umlClass.getId())));
        assertTrue(resultDiagram.getRelations().isEmpty());
        assertTrue(resultView.getNodes().stream().noneMatch(node -> "class-1".equals(node.getClassId())));
        assertTrue(resultView.getRelations().isEmpty());
    }

    @Test
    void rejectsOldAndFutureVersionsWithoutSaving() {
        assertThrows(VersionConflictException.class, () -> service.execute("diagram-1",
                operation("op-1", -1, DiagramOperationType.RENAME_CLASS,
                        RenameClassPayload.builder().classId("class-1").name("Client").build())));
        assertThrows(VersionConflictException.class, () -> service.execute("diagram-1",
                operation("op-2", 1, DiagramOperationType.RENAME_CLASS,
                        RenameClassPayload.builder().classId("class-1").name("Client").build())));
        verify(repository, never()).save(any(DiagramEntity.class));
    }

    @Test
    void rejectsMissingDiagram() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        DiagramOperation operation = operation("op-1", 0, DiagramOperationType.RENAME_CLASS,
                RenameClassPayload.builder().classId("class-1").name("Client").build());
        operation.setDiagramId("missing");
        assertThrows(DiagramNotFoundException.class, () -> service.execute("missing", operation));
    }

    @Test
    void rejectsMismatchedOperationDiagramId() {
        DiagramOperation operation = operation("op-1", 0, DiagramOperationType.RENAME_CLASS,
                RenameClassPayload.builder().classId("class-1").name("Client").build());
        operation.setDiagramId("other-diagram");

        assertThrows(OperationApplicationException.class, () -> service.execute("diagram-1", operation));
        verify(repository, never()).findById(any());
    }

    @Test
    void rejectsPersistedVersionInconsistentWithCanonicalModel() {
        entity.setVersion(5);

        assertThrows(VersionConflictException.class, () -> service.execute("diagram-1",
                operation("op-1", 4, DiagramOperationType.RENAME_CLASS,
                        RenameClassPayload.builder().classId("class-1").name("Client").build())));
        verify(repository, never()).save(any(DiagramEntity.class));
    }

    @Test
    void invalidOperationDoesNotPersist() {
        UmlDiagram diagram = serializer.deserializeCanonical(entity.getCanonicalModelJson());
        diagram.getClasses().add(UmlClass.builder().id("class-2").name("Order").build());
        entity.setCanonicalModelJson(serializer.serializeCanonical(diagram));

        assertThrows(OperationApplicationException.class, () -> service.execute("diagram-1",
                operation("op-1", 0, DiagramOperationType.RENAME_CLASS,
                        RenameClassPayload.builder().classId("class-1").name("Order").build())));
        verify(repository, never()).save(any(DiagramEntity.class));
        assertEquals("Customer", serializer.deserializeCanonical(entity.getCanonicalModelJson())
                .getClasses().get(0).getName());
    }

    @Test
    void persistedJsonCanBeDeserializedAfterSaving() {
        service.execute("diagram-1", operation("op-1", 0, DiagramOperationType.CREATE_CLASS,
                CreateClassPayload.builder().classId("class-2").name("Order").build()));

        assertEquals("diagram-1", serializer.deserializeCanonical(entity.getCanonicalModelJson()).getId());
        assertEquals("diagram-1", serializer.deserializeViewState(entity.getViewStateJson()).getDiagramId());
    }

    private UmlDiagram baseDiagram() {
        return UmlDiagram.builder().id("diagram-1").name("CRM")
                .classes(new java.util.ArrayList<>(java.util.List.of(
                        UmlClass.builder().id("class-1").name("Customer").build()))).build();
    }

    private DiagramViewState baseViewState() {
        return DiagramViewState.builder().diagramId("diagram-1")
                .nodes(new java.util.ArrayList<>(java.util.List.of(NodeViewState.builder()
                        .classId("class-1").width(100).height(80).build()))).build();
    }

    private DiagramEntity persistedEntity(UmlDiagram diagram, DiagramViewState viewState) {
        return DiagramEntity.builder().id("diagram-1").projectId("project-1").name("Main")
                .version(0).canonicalModelJson(serializer.serializeCanonical(diagram))
                .viewStateJson(serializer.serializeViewState(viewState)).build();
    }

    private DiagramOperation operation(String id, long version, DiagramOperationType type, Object payload) {
        return DiagramOperation.builder().operationId(id).diagramId("diagram-1").userId("user-1")
                .baseVersion(version).type(type).payload(payload).build();
    }

    private UmlRelation relation(String id, String source, String target) {
        return UmlRelation.builder().id(id).sourceClassId(source).targetClassId(target)
                .type(RelationType.ASSOCIATION)
                .sourceMultiplicity(Multiplicity.builder().lower("0").upper("*").build())
                .targetMultiplicity(Multiplicity.builder().lower("1").upper("1").build()).build();
    }
}
