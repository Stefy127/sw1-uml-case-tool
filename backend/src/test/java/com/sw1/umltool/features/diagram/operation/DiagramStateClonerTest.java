package com.sw1.umltool.features.diagram.operation;

import com.sw1.umltool.features.diagram.model.canonical.Multiplicity;
import com.sw1.umltool.features.diagram.model.canonical.UmlAttribute;
import com.sw1.umltool.features.diagram.model.canonical.UmlClass;
import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.canonical.UmlMethod;
import com.sw1.umltool.features.diagram.model.canonical.UmlParameter;
import com.sw1.umltool.features.diagram.model.canonical.UmlRelation;
import com.sw1.umltool.features.diagram.model.canonical.enums.RelationType;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;
import com.sw1.umltool.features.diagram.model.view.NodeViewState;
import com.sw1.umltool.features.diagram.model.view.RelationViewState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class DiagramStateClonerTest {

    private final DiagramStateCloner cloner = new DiagramStateCloner();

    @Test
    void cloneDiagramIsDeep() {
        UmlParameter parameter = UmlParameter.builder().id("parameter-1").name("id").type("Long").build();
        UmlMethod method = UmlMethod.builder().id("method-1").name("find").parameters(java.util.List.of(parameter)).build();
        UmlAttribute attribute = UmlAttribute.builder().id("attribute-1").name("name").type("String").build();
        UmlClass source = UmlClass.builder().id("class-1").name("Customer").attributes(java.util.List.of(attribute))
                .methods(java.util.List.of(method)).build();
        UmlClass target = UmlClass.builder().id("class-2").name("Order").build();
        Multiplicity multiplicity = Multiplicity.builder().lower("0").upper("*").build();
        UmlRelation relation = UmlRelation.builder().id("relation-1").sourceClassId("class-1")
                .targetClassId("class-2").type(RelationType.ASSOCIATION)
                .sourceMultiplicity(multiplicity).targetMultiplicity(multiplicity).build();
        UmlDiagram original = UmlDiagram.builder().id("diagram-1").name("CRM")
                .classes(new java.util.ArrayList<>(java.util.List.of(source, target)))
                .relations(new java.util.ArrayList<>(java.util.List.of(relation))).build();

        UmlDiagram copy = cloner.cloneDiagram(original);

        assertNotSame(original, copy);
        assertNotSame(original.getClasses(), copy.getClasses());
        assertNotSame(source, copy.getClasses().get(0));
        assertNotSame(attribute, copy.getClasses().get(0).getAttributes().get(0));
        assertNotSame(method, copy.getClasses().get(0).getMethods().get(0));
        assertNotSame(parameter, copy.getClasses().get(0).getMethods().get(0).getParameters().get(0));
        assertNotSame(relation, copy.getRelations().get(0));
        assertNotSame(multiplicity, copy.getRelations().get(0).getSourceMultiplicity());

        copy.getClasses().get(0).setName("Changed");
        copy.getClasses().get(0).getAttributes().get(0).setName("changed");
        copy.getRelations().get(0).getSourceMultiplicity().setLower("1");

        assertEquals("Customer", original.getClasses().get(0).getName());
        assertEquals("name", original.getClasses().get(0).getAttributes().get(0).getName());
        assertEquals("0", original.getRelations().get(0).getSourceMultiplicity().getLower());
    }

    @Test
    void cloneViewStateIsDeep() {
        NodeViewState node = NodeViewState.builder().classId("class-1").x(10).y(20).width(100).height(80).build();
        RelationViewState relation = RelationViewState.builder().relationId("relation-1").build();
        DiagramViewState original = DiagramViewState.builder().diagramId("diagram-1")
                .nodes(new java.util.ArrayList<>(java.util.List.of(node)))
                .relations(new java.util.ArrayList<>(java.util.List.of(relation))).build();

        DiagramViewState copy = cloner.cloneViewState(original);

        assertNotSame(original, copy);
        assertNotSame(original.getNodes(), copy.getNodes());
        assertNotSame(node, copy.getNodes().get(0));
        assertNotSame(relation, copy.getRelations().get(0));
        copy.getNodes().get(0).setX(99);
        assertEquals(10, original.getNodes().get(0).getX());
    }
}
