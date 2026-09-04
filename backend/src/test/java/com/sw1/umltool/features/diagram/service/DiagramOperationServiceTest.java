package com.sw1.umltool.features.diagram.service;

import com.sw1.umltool.features.diagram.model.canonical.Multiplicity;
import com.sw1.umltool.features.diagram.model.canonical.UmlClass;
import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.canonical.UmlRelation;
import com.sw1.umltool.features.diagram.model.canonical.enums.RelationType;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;
import com.sw1.umltool.features.diagram.model.view.NodeViewState;
import com.sw1.umltool.features.diagram.operation.DiagramOperation;
import com.sw1.umltool.features.diagram.operation.DiagramOperationApplier;
import com.sw1.umltool.features.diagram.operation.DiagramOperationType;
import com.sw1.umltool.features.diagram.operation.DiagramStateCloner;
import com.sw1.umltool.features.diagram.operation.OperationApplicationException;
import com.sw1.umltool.features.diagram.operation.OperationExecutionResult;
import com.sw1.umltool.features.diagram.operation.VersionConflictException;
import com.sw1.umltool.features.diagram.operation.payload.CreateClassPayload;
import com.sw1.umltool.features.diagram.operation.payload.CreateRelationPayload;
import com.sw1.umltool.features.diagram.operation.payload.DeleteClassPayload;
import com.sw1.umltool.features.diagram.operation.payload.MoveClassPayload;
import com.sw1.umltool.features.diagram.operation.payload.RenameClassPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagramOperationServiceTest {

    private DiagramOperationService service;
    private UmlDiagram diagram;
    private DiagramViewState viewState;

    @BeforeEach
    void setUp() {
        service = new DiagramOperationService(new DiagramOperationApplier(),
                new com.sw1.umltool.features.diagram.validation.CanonicalModelValidator(),
                new DiagramStateCloner());
        diagram = UmlDiagram.builder().id("diagram-1").name("CRM").build();
        viewState = DiagramViewState.builder().diagramId("diagram-1").build();
        addClass("class-1", "Customer");
        addClass("class-2", "Order");
    }

    @Test
    void renameExecutesOnCopyAndIncrementsVersion() {
        OperationExecutionResult result = execute(0, DiagramOperationType.RENAME_CLASS,
                RenameClassPayload.builder().classId("class-1").name("Client").build());

        assertEquals("Customer", diagram.getClasses().get(0).getName());
        assertEquals("Client", result.getDiagram().getClasses().get(0).getName());
        assertEquals(0, result.getPreviousVersion());
        assertEquals(1, result.getNewVersion());
    }

    @Test
    void moveChangesOnlyCopiedViewState() {
        OperationExecutionResult result = execute(0, DiagramOperationType.MOVE_CLASS,
                MoveClassPayload.builder().classId("class-1").x(50).y(60).build());

        assertEquals(0, viewState.getNodes().get(0).getX());
        assertEquals(50, result.getViewState().getNodes().get(0).getX());
        assertEquals(60, result.getViewState().getNodes().get(0).getY());
        assertEquals(1, result.getNewVersion());
    }

    @Test
    void sequentialOperationsUseResultVersion() {
        OperationExecutionResult first = execute(0, DiagramOperationType.RENAME_CLASS,
                RenameClassPayload.builder().classId("class-1").name("Client").build());
        OperationExecutionResult second = service.execute(operation("operation-2", 1,
                DiagramOperationType.MOVE_CLASS, MoveClassPayload.builder().classId("class-1").x(10).y(20).build()),
                first.getDiagram(), first.getViewState());

        assertEquals(2, second.getNewVersion());
        assertEquals("Client", second.getDiagram().getClasses().get(0).getName());
    }

    @Test
    void rejectsOldAndFutureBaseVersions() {
        assertThrows(VersionConflictException.class, () -> execute(1, DiagramOperationType.RENAME_CLASS,
                RenameClassPayload.builder().classId("class-1").name("Client").build()));
        assertThrows(VersionConflictException.class, () -> execute(-1, DiagramOperationType.RENAME_CLASS,
                RenameClassPayload.builder().classId("class-1").name("Client").build()));
    }

    @Test
    void rejectsMismatchedDiagramIdAndMissingMetadata() {
        DiagramOperation wrongDiagram = operation("operation-1", 0, DiagramOperationType.RENAME_CLASS,
                RenameClassPayload.builder().classId("class-1").name("Client").build());
        wrongDiagram.setDiagramId("other-diagram");
        assertThrows(OperationApplicationException.class, () -> service.execute(wrongDiagram, diagram, viewState));

        DiagramOperation noId = operation("", 0, DiagramOperationType.RENAME_CLASS,
                RenameClassPayload.builder().classId("class-1").name("Client").build());
        assertThrows(OperationApplicationException.class, () -> service.execute(noId, diagram, viewState));

        DiagramOperation noPayload = operation("operation-1", 0, DiagramOperationType.RENAME_CLASS, null);
        assertThrows(OperationApplicationException.class, () -> service.execute(noPayload, diagram, viewState));
    }

    @Test
    void invalidResultLeavesOriginalUnchanged() {
        OperationApplicationException exception = assertThrows(OperationApplicationException.class,
                () -> execute(0, DiagramOperationType.RENAME_CLASS,
                        RenameClassPayload.builder().classId("class-1").name("Order").build()));

        assertTrue(exception.getMessage().contains("DUPLICATE_CLASS_NAME"));
        assertEquals("Customer", diagram.getClasses().get(0).getName());
    }

    @Test
    void createClassCreatesNodeAndIncrementsVersion() {
        OperationExecutionResult result = execute(0, DiagramOperationType.CREATE_CLASS,
                CreateClassPayload.builder().classId("class-3").name("Product").width(100).height(80).build());

        assertEquals(3, result.getDiagram().getClasses().size());
        assertEquals("class-3", result.getViewState().getNodes().get(2).getClassId());
        assertEquals(1, result.getNewVersion());
    }

    @Test
    void deleteClassReturnsCopyWithoutClassAndRelation() {
        UmlRelation relation = relation("relation-1", "class-1", "class-2");
        diagram.getRelations().add(relation);
        viewState.getRelations().add(com.sw1.umltool.features.diagram.model.view.RelationViewState.builder()
                .relationId("relation-1").build());

        OperationExecutionResult result = execute(0, DiagramOperationType.DELETE_CLASS,
                DeleteClassPayload.builder().classId("class-1").build());

        assertEquals(2, diagram.getClasses().size());
        assertEquals(1, result.getDiagram().getClasses().size());
        assertTrue(result.getDiagram().getRelations().isEmpty());
        assertTrue(result.getViewState().getRelations().isEmpty());
    }

    @Test
    void rejectsRelationPointingToMissingClass() {
        UmlRelation relation = relation("relation-1", "class-1", "missing");
        assertThrows(OperationApplicationException.class, () -> execute(0, DiagramOperationType.CREATE_RELATION,
                CreateRelationPayload.builder().relation(relation).build()));
        assertTrue(diagram.getRelations().isEmpty());
    }

    private OperationExecutionResult execute(long baseVersion, DiagramOperationType type, Object payload) {
        return service.execute(operation("operation-1", baseVersion, type, payload), diagram, viewState);
    }

    private DiagramOperation operation(String id, long version, DiagramOperationType type, Object payload) {
        return DiagramOperation.builder().operationId(id).diagramId("diagram-1").userId("user-1")
                .baseVersion(version).type(type).payload(payload).build();
    }

    private void addClass(String id, String name) {
        diagram.getClasses().add(UmlClass.builder().id(id).name(name).build());
        viewState.getNodes().add(NodeViewState.builder().classId(id).build());
    }

    private UmlRelation relation(String id, String source, String target) {
        Multiplicity multiplicity = Multiplicity.builder().lower("0").upper("*").build();
        return UmlRelation.builder().id(id).sourceClassId(source).targetClassId(target)
                .type(RelationType.ASSOCIATION).sourceMultiplicity(multiplicity)
                .targetMultiplicity(Multiplicity.builder().lower("1").upper("1").build()).build();
    }
}
