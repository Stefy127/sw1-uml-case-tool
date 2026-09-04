package com.sw1.umltool.features.diagram.service;

import tools.jackson.databind.ObjectMapper;
import com.sw1.umltool.features.diagram.model.canonical.Multiplicity;
import com.sw1.umltool.features.diagram.model.canonical.UmlAttribute;
import com.sw1.umltool.features.diagram.model.canonical.UmlClass;
import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.canonical.UmlMethod;
import com.sw1.umltool.features.diagram.model.canonical.UmlParameter;
import com.sw1.umltool.features.diagram.model.canonical.UmlRelation;
import com.sw1.umltool.features.diagram.model.canonical.enums.RelationType;
import com.sw1.umltool.features.diagram.model.canonical.enums.Visibility;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;
import com.sw1.umltool.features.diagram.model.view.NodeViewState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiagramStateSerializerTest {

    private final DiagramStateSerializer serializer = new DiagramStateSerializer(new ObjectMapper());

    @Test
    void serializesAndDeserializesCanonicalModel() {
        UmlParameter parameter = UmlParameter.builder().id("parameter-1").name("id").type("Long").build();
        UmlMethod method = UmlMethod.builder().id("method-1").name("find").returnType("Customer")
                .parameters(java.util.List.of(parameter)).build();
        UmlAttribute attribute = UmlAttribute.builder().id("attribute-1").name("id").type("Long")
                .visibility(Visibility.PRIVATE).primaryKey(true).build();
        UmlClass source = UmlClass.builder().id("class-1").name("Customer")
                .attributes(java.util.List.of(attribute)).methods(java.util.List.of(method)).build();
        UmlClass target = UmlClass.builder().id("class-2").name("Order").build();
        Multiplicity sourceMultiplicity = Multiplicity.builder().lower("0").upper("*").build();
        Multiplicity targetMultiplicity = Multiplicity.builder().lower("1").upper("1").build();
        UmlRelation relation = UmlRelation.builder().id("relation-1").sourceClassId("class-1")
                .targetClassId("class-2").type(RelationType.ASSOCIATION)
                .sourceMultiplicity(sourceMultiplicity).targetMultiplicity(targetMultiplicity).build();
        UmlDiagram original = UmlDiagram.builder().id("diagram-1").name("CRM").version(4)
                .classes(java.util.List.of(source, target)).relations(java.util.List.of(relation)).build();

        UmlDiagram copy = serializer.deserializeCanonical(serializer.serializeCanonical(original));

        assertEquals("diagram-1", copy.getId());
        assertEquals("CRM", copy.getName());
        assertEquals(4, copy.getVersion());
        assertEquals("Customer", copy.getClasses().get(0).getName());
        assertEquals("id", copy.getClasses().get(0).getAttributes().get(0).getName());
        assertEquals("find", copy.getClasses().get(0).getMethods().get(0).getName());
        assertEquals("id", copy.getClasses().get(0).getMethods().get(0).getParameters().get(0).getName());
        assertEquals(RelationType.ASSOCIATION, copy.getRelations().get(0).getType());
        assertEquals("0", copy.getRelations().get(0).getSourceMultiplicity().getLower());
        assertEquals("1", copy.getRelations().get(0).getTargetMultiplicity().getUpper());
    }

    @Test
    void serializesAndDeserializesViewState() {
        DiagramViewState original = DiagramViewState.builder().diagramId("diagram-1")
                .nodes(java.util.List.of(NodeViewState.builder().classId("class-1")
                        .x(10).y(20).width(200).height(100).build())).build();

        DiagramViewState copy = serializer.deserializeViewState(serializer.serializeViewState(original));

        assertEquals("diagram-1", copy.getDiagramId());
        assertEquals("class-1", copy.getNodes().get(0).getClassId());
        assertEquals(10, copy.getNodes().get(0).getX());
        assertEquals(20, copy.getNodes().get(0).getY());
        assertEquals(200, copy.getNodes().get(0).getWidth());
        assertEquals(100, copy.getNodes().get(0).getHeight());
    }

    @Test
    void invalidJsonThrowsDiagramSerializationException() {
        assertThrows(DiagramSerializationException.class,
                () -> serializer.deserializeCanonical("{invalid-json"));
    }
}
