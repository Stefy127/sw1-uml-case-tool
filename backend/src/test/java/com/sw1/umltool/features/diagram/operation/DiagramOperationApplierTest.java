package com.sw1.umltool.features.diagram.operation;

import com.sw1.umltool.features.diagram.model.canonical.UmlAttribute;
import com.sw1.umltool.features.diagram.model.canonical.UmlClass;
import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.canonical.UmlMethod;
import com.sw1.umltool.features.diagram.model.canonical.UmlParameter;
import com.sw1.umltool.features.diagram.model.canonical.UmlRelation;
import com.sw1.umltool.features.diagram.model.canonical.AssociationClassLink;
import com.sw1.umltool.features.diagram.model.canonical.enums.RelationType;
import com.sw1.umltool.features.diagram.model.canonical.enums.Visibility;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;
import com.sw1.umltool.features.diagram.model.view.NodeViewState;
import com.sw1.umltool.features.diagram.model.view.RelationViewState;
import com.sw1.umltool.features.diagram.operation.payload.AddAttributePayload;
import com.sw1.umltool.features.diagram.operation.payload.AddMethodPayload;
import com.sw1.umltool.features.diagram.operation.payload.AddParameterPayload;
import com.sw1.umltool.features.diagram.operation.payload.ChangeRelationTypePayload;
import com.sw1.umltool.features.diagram.operation.payload.CreateClassPayload;
import com.sw1.umltool.features.diagram.operation.payload.CreateRelationPayload;
import com.sw1.umltool.features.diagram.operation.payload.CreateAssociationClassPayload;
import com.sw1.umltool.features.diagram.operation.payload.DeleteAssociationClassLinkPayload;
import com.sw1.umltool.features.diagram.operation.payload.DeleteClassPayload;
import com.sw1.umltool.features.diagram.operation.payload.DeleteRelationPayload;
import com.sw1.umltool.features.diagram.operation.payload.MoveClassPayload;
import com.sw1.umltool.features.diagram.operation.payload.RenameClassPayload;
import com.sw1.umltool.features.diagram.operation.payload.ResizeClassPayload;
import com.sw1.umltool.features.diagram.operation.payload.UpdateClassStylePayload;
import com.sw1.umltool.features.diagram.operation.payload.UpdateAttributePayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagramOperationApplierTest {

    private DiagramOperationApplier applier;
    private UmlDiagram diagram;
    private DiagramViewState viewState;

    @BeforeEach
    void setUp() {
        applier = new DiagramOperationApplier();
        diagram = UmlDiagram.builder().id("diagram-1").build();
        viewState = DiagramViewState.builder().diagramId("diagram-1").build();
    }

    @Test
    void createClassAddsCanonicalClassAndNode() {
        apply(DiagramOperationType.CREATE_CLASS, CreateClassPayload.builder().classId("class-1")
                .name("Customer").x(10).y(20).width(200).height(100).build());

        assertEquals("Customer", diagram.getClasses().get(0).getName());
        assertEquals("class-1", viewState.getNodes().get(0).getClassId());
        assertEquals(10, viewState.getNodes().get(0).getX());
    }

    @Test
    void renameClassChangesName() {
        addClass("class-1", "OldName");

        apply(DiagramOperationType.RENAME_CLASS, RenameClassPayload.builder().classId("class-1")
                .name("NewName").build());

        assertEquals("NewName", diagram.getClasses().get(0).getName());
    }

    @Test
    void deleteClassRemovesNodeAndAssociatedRelations() {
        addClass("class-1", "Customer");
        addClass("class-2", "Order");
        UmlRelation relation = UmlRelation.builder().id("relation-1").sourceClassId("class-1")
                .targetClassId("class-2").build();
        diagram.getRelations().add(relation);
        viewState.getRelations().add(RelationViewState.builder().relationId("relation-1").build());

        apply(DiagramOperationType.DELETE_CLASS, DeleteClassPayload.builder().classId("class-1").build());

        assertTrue(diagram.getClasses().stream().noneMatch(umlClass -> umlClass.getId().equals("class-1")));
        assertTrue(viewState.getNodes().stream().noneMatch(node -> node.getClassId().equals("class-1")));
        assertTrue(diagram.getRelations().isEmpty());
        assertTrue(viewState.getRelations().isEmpty());
    }

    @Test
    void addAttributeAddsAttribute() {
        addClass("class-1", "Customer");
        UmlAttribute attribute = UmlAttribute.builder().id("attribute-1").name("name").build();

        apply(DiagramOperationType.ADD_ATTRIBUTE, AddAttributePayload.builder().classId("class-1")
                .attribute(attribute).build());

        assertEquals(attribute, diagram.getClasses().get(0).getAttributes().get(0));
    }

    @Test
    void updateAttributeChangesOnlyProvidedFields() {
        addClass("class-1", "Customer");
        UmlAttribute attribute = UmlAttribute.builder().id("attribute-1").name("oldName")
                .type("String").visibility(Visibility.PRIVATE).build();
        diagram.getClasses().get(0).getAttributes().add(attribute);

        apply(DiagramOperationType.UPDATE_ATTRIBUTE, UpdateAttributePayload.builder().classId("class-1")
                .attributeId("attribute-1").name("newName").build());

        assertEquals("newName", attribute.getName());
        assertEquals("String", attribute.getType());
        assertEquals(Visibility.PRIVATE, attribute.getVisibility());
    }

    @Test
    void addMethodAndParameterAddNestedElements() {
        addClass("class-1", "Customer");
        UmlMethod method = UmlMethod.builder().id("method-1").name("find").build();
        UmlParameter parameter = UmlParameter.builder().id("parameter-1").name("id").type("Long").build();

        apply(DiagramOperationType.ADD_METHOD, AddMethodPayload.builder().classId("class-1")
                .method(method).build());
        apply(DiagramOperationType.ADD_PARAMETER, AddParameterPayload.builder().classId("class-1")
                .methodId("method-1").parameter(parameter).build());

        assertEquals(method, diagram.getClasses().get(0).getMethods().get(0));
        assertEquals(parameter, method.getParameters().get(0));
    }

    @Test
    void createRelationAddsRelationAndViewState() {
        addClass("class-1", "Customer");
        addClass("class-2", "Order");
        UmlRelation relation = UmlRelation.builder().id("relation-1").sourceClassId("class-1")
                .targetClassId("class-2").build();

        apply(DiagramOperationType.CREATE_RELATION, CreateRelationPayload.builder().relation(relation).build());

        assertEquals(relation, diagram.getRelations().get(0));
        assertEquals("relation-1", viewState.getRelations().get(0).getRelationId());
    }

    @Test
    void createRelationAllowsRecursiveRelation() {
        addClass("class-1", "Employee");
        UmlRelation relation = UmlRelation.builder().id("relation-self")
                .sourceClassId("class-1").targetClassId("class-1").build();

        apply(DiagramOperationType.CREATE_RELATION,
                CreateRelationPayload.builder().relation(relation).build());

        assertEquals("class-1", diagram.getRelations().get(0).getSourceClassId());
        assertEquals("class-1", diagram.getRelations().get(0).getTargetClassId());
        assertEquals("relation-self", viewState.getRelations().get(0).getRelationId());
    }

    @Test
    void deleteRelationRemovesRelationAndViewState() {
        addClass("class-1", "Customer");
        addClass("class-2", "Order");
        diagram.getRelations().add(UmlRelation.builder().id("relation-1").sourceClassId("class-1")
                .targetClassId("class-2").build());
        viewState.getRelations().add(RelationViewState.builder().relationId("relation-1").build());

        apply(DiagramOperationType.DELETE_RELATION, DeleteRelationPayload.builder().relationId("relation-1").build());

        assertTrue(diagram.getRelations().isEmpty());
        assertTrue(viewState.getRelations().isEmpty());
    }

    @Test
    void createsAssociationClassAndLinkAtomicallyForRecursiveRelation() {
        addClass("class-1", "Employee");
        UmlRelation relation = UmlRelation.builder().id("relation-1").sourceClassId("class-1")
                .targetClassId("class-1").type(RelationType.ASSOCIATION).build();
        diagram.getRelations().add(relation);

        apply(DiagramOperationType.CREATE_ASSOCIATION_CLASS, CreateAssociationClassPayload.builder()
                .linkId("link-1").relationId("relation-1").classId("class-2").name("Supervision")
                .width(240).height(180).build());

        assertEquals("Supervision", diagram.getClasses().get(1).getName());
        assertEquals("class-2", viewState.getNodes().get(1).getClassId());
        assertEquals(new AssociationClassLink("link-1", "relation-1", "class-2"),
                diagram.getAssociationClassLinks().get(0));
    }

    @Test
    void unlinkingAssociationClassKeepsTheClass() {
        addClass("class-1", "Employee");
        addClass("class-2", "Supervision");
        diagram.getRelations().add(UmlRelation.builder().id("relation-1").sourceClassId("class-1")
                .targetClassId("class-1").type(RelationType.ASSOCIATION).build());
        diagram.getAssociationClassLinks().add(new AssociationClassLink("link-1", "relation-1", "class-2"));

        apply(DiagramOperationType.DELETE_ASSOCIATION_CLASS_LINK,
                DeleteAssociationClassLinkPayload.builder().linkId("link-1").build());

        assertEquals(2, diagram.getClasses().size());
        assertTrue(diagram.getAssociationClassLinks().isEmpty());
    }

    @Test
    void deletingRelationCleansAssociationClassLinkWithoutDeletingClass() {
        addClass("class-1", "Employee");
        addClass("class-2", "Supervision");
        diagram.getRelations().add(UmlRelation.builder().id("relation-1").sourceClassId("class-1")
                .targetClassId("class-1").type(RelationType.ASSOCIATION).build());
        diagram.getAssociationClassLinks().add(new AssociationClassLink("link-1", "relation-1", "class-2"));

        apply(DiagramOperationType.DELETE_RELATION, DeleteRelationPayload.builder().relationId("relation-1").build());

        assertEquals(2, diagram.getClasses().size());
        assertTrue(diagram.getAssociationClassLinks().isEmpty());
    }

    @Test
    void moveClassChangesNodeOnly() {
        addClass("class-1", "Customer");
        UmlClass umlClass = diagram.getClasses().get(0);

        apply(DiagramOperationType.MOVE_CLASS, MoveClassPayload.builder().classId("class-1")
                .x(50).y(60).build());

        assertEquals(50, viewState.getNodes().get(0).getX());
        assertEquals(60, viewState.getNodes().get(0).getY());
        assertEquals("Customer", umlClass.getName());
    }

    @Test
    void resizeClassChangesDimensions() {
        addClass("class-1", "Customer");

        apply(DiagramOperationType.RESIZE_CLASS, ResizeClassPayload.builder().classId("class-1")
                .width(300).height(150).build());

        NodeViewState node = viewState.getNodes().get(0);
        assertEquals(300, node.getWidth());
        assertEquals(150, node.getHeight());
    }

    @Test
    void updateClassStyleChangesViewStateOnly() {
        addClass("class-1", "Customer");
        UmlClass umlClass = diagram.getClasses().get(0);

        apply(DiagramOperationType.UPDATE_CLASS_STYLE, UpdateClassStylePayload.builder()
                .classId("class-1").headerColor("#eee8ff").bodyColor("#ffffff")
                .borderColor("#8a7be8").build());

        NodeViewState node = viewState.getNodes().get(0);
        assertEquals("#eee8ff", node.getHeaderColor());
        assertEquals("#ffffff", node.getBodyColor());
        assertEquals("#8a7be8", node.getBorderColor());
        assertEquals("Customer", umlClass.getName());
    }

    @Test
    void failsForMissingClass() {
        assertThrows(OperationApplicationException.class, () -> apply(DiagramOperationType.RENAME_CLASS,
                RenameClassPayload.builder().classId("missing").name("Name").build()));
    }

    @Test
    void failsForDuplicateClassId() {
        addClass("class-1", "Customer");

        assertThrows(OperationApplicationException.class, () -> apply(DiagramOperationType.CREATE_CLASS,
                CreateClassPayload.builder().classId("class-1").name("Other").build()));
    }

    @Test
    void failsForRelationWithMissingClass() {
        addClass("class-1", "Customer");
        UmlRelation relation = UmlRelation.builder().id("relation-1").sourceClassId("class-1")
                .targetClassId("missing").build();

        assertThrows(OperationApplicationException.class, () -> apply(DiagramOperationType.CREATE_RELATION,
                CreateRelationPayload.builder().relation(relation).build()));
        assertFalse(diagram.getRelations().contains(relation));
    }

    private void addClass(String id, String name) {
        diagram.getClasses().add(UmlClass.builder().id(id).name(name).build());
        viewState.getNodes().add(NodeViewState.builder().classId(id).width(100).height(80).build());
    }

    private void apply(DiagramOperationType type, Object payload) {
        applier.apply(DiagramOperation.builder().type(type).payload(payload).build(), diagram, viewState);
    }
}
