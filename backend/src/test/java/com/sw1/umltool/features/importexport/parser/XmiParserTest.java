package com.sw1.umltool.features.importexport.parser;

import com.sw1.umltool.features.diagram.model.canonical.enums.RelationType;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class XmiParserTest {
    private final XmiParser parser = new XmiParser();

    @Test
    void importsClassesWithAttributesMethodsAndDeterministicNodes() throws Exception {
        var result = parser.parse(read("attributes-methods.xmi"), "diagram-1", "Import");
        var umlClass = result.getCanonicalModel().getClasses().get(0);
        assertEquals("Cliente", umlClass.getName());
        assertEquals(2, umlClass.getAttributes().size());
        assertEquals("Long", umlClass.getAttributes().get(0).getType());
        assertEquals("buscar", umlClass.getMethods().get(0).getName());
        assertEquals("Cliente", umlClass.getMethods().get(0).getReturnType());
        assertEquals("Long", umlClass.getMethods().get(0).getParameters().get(0).getType());
        assertNotEquals("class-client", umlClass.getId());
        assertDoesNotThrow(() -> UUID.fromString(umlClass.getId()));
        assertEquals(result.getCanonicalModel().getClasses().size(), result.getViewState().getNodes().size());
    }

    @Test
    void importsAssociationInheritanceAndRecursiveReferences() throws Exception {
        var association = parser.parse(read("association.xmi"), "d", "D").getCanonicalModel();
        assertEquals(RelationType.ASSOCIATION, association.getRelations().get(0).getType());
        assertEquals("1", association.getRelations().get(0).getSourceMultiplicity().getLower());
        assertEquals("*", association.getRelations().get(0).getTargetMultiplicity().getUpper());
        var inheritance = parser.parse(read("inheritance.xmi"), "d", "D").getCanonicalModel().getRelations().get(0);
        assertEquals(RelationType.INHERITANCE, inheritance.getType());
        var recursive = parser.parse(read("recursive-association.xmi"), "d", "D").getCanonicalModel().getRelations().get(0);
        assertEquals(recursive.getSourceClassId(), recursive.getTargetClassId());
    }

    @Test
    void mapsAggregationCompositionAndDependency() throws Exception {
        assertEquals(RelationType.AGGREGATION, parser.parse(read("aggregation.xmi"), "d", "D").getCanonicalModel().getRelations().get(0).getType());
        assertEquals(RelationType.COMPOSITION, parser.parse(read("composition.xmi"), "d", "D").getCanonicalModel().getRelations().get(0).getType());
        assertEquals(RelationType.DEPENDENCY, parser.parse(read("dependency.xmi"), "d", "D").getCanonicalModel().getRelations().get(0).getType());
    }

    @Test
    void importsEnterpriseArchitectBasicAssociation() throws Exception {
        var result = parser.parse(read("enterprise-architect-basic-association.xmi"), "d", "D");
        var diagram = result.getCanonicalModel();

        assertEquals(2, diagram.getClasses().size());
        assertEquals(3, diagram.getClasses().stream().mapToInt(c -> c.getAttributes().size()).sum());
        assertEquals(1, diagram.getRelations().size());
        var relation = diagram.getRelations().get(0);
        assertEquals(RelationType.ASSOCIATION, relation.getType());
        assertEquals("1", relation.getSourceMultiplicity().getLower());
        assertEquals("1", relation.getSourceMultiplicity().getUpper());
        assertEquals("0", relation.getTargetMultiplicity().getLower());
        assertEquals("*", relation.getTargetMultiplicity().getUpper());
        assertTrue(diagram.getClasses().stream().anyMatch(c -> c.getName().equals("Cliente") &&
                c.getAttributes().stream().anyMatch(a -> a.getName().equals("ID") && a.getType().equals("Long"))));
        assertTrue(diagram.getClasses().stream().anyMatch(c -> c.getName().equals("Pedido") &&
                c.getAttributes().stream().anyMatch(a -> a.getName().equals("Fecha") && a.getType().equals("date"))));
        assertTrue(diagram.getClasses().stream().anyMatch(c -> c.getId().equals(relation.getSourceClassId())));
        assertTrue(diagram.getClasses().stream().anyMatch(c -> c.getId().equals(relation.getTargetClassId())));
    }

    @Test
    void rejectsInvalidXmlAndDoctype() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(read("invalid.xmi"), "d", "D"));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(read("xxe.xmi"), "d", "D"));
    }

    private byte[] read(String name) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/xmi/" + name)) {
            assertNotNull(stream);
            return stream.readAllBytes();
        }
    }
}
