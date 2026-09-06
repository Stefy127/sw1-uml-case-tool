package com.sw1.umltool.features.importexport.mapper;

import com.sw1.umltool.features.diagram.model.canonical.enums.RelationType;
import com.sw1.umltool.features.diagram.validation.CanonicalModelValidator;
import com.sw1.umltool.features.importexport.dto.AiUmlDetectionResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ImageUmlCanonicalMapperTest {
    private final ImageUmlCanonicalMapper mapper = new ImageUmlCanonicalMapper();

    @Test
    void mapsClassesAttributesMethodsRelationsAndDeterministicLayout() {
        var detection = detection(List.of(clazz("cliente", "Cliente"), clazz("pedido", "Pedido")),
                List.of(relation("r1", "ASSOCIATION", "cliente", "pedido", "1", "0..*")));
        var result = mapper.map(detection, "diagram-1", "Importado");
        var diagram = result.getCanonicalModel();
        assertEquals(2, diagram.getClasses().size());
        assertEquals(1, diagram.getRelations().size());
        assertEquals(RelationType.ASSOCIATION, diagram.getRelations().getFirst().getType());
        assertEquals("1", diagram.getRelations().getFirst().getSourceMultiplicity().getLower());
        assertEquals("1", diagram.getRelations().getFirst().getSourceMultiplicity().getUpper());
        assertEquals("0", diagram.getRelations().getFirst().getTargetMultiplicity().getLower());
        assertEquals("*", diagram.getRelations().getFirst().getTargetMultiplicity().getUpper());
        assertNotEquals("cliente", diagram.getClasses().getFirst().getId());
        assertEquals(100, result.getViewState().getNodes().getFirst().getX());
        assertTrue(new CanonicalModelValidator().validate(diagram).isValid());
    }

    @Test
    void keepsRecursiveRelationsValid() {
        var result = mapper.map(detection(List.of(clazz("employee", "Empleado")),
                List.of(relation("r1", "ASSOCIATION", "employee", "employee", "0..1", "0..*"))), "d", "D");
        var relation = result.getCanonicalModel().getRelations().getFirst();
        assertEquals(relation.getSourceClassId(), relation.getTargetClassId());
    }

    @Test
    void usesFallbackWhenMultiplicitiesAreAbsentAndKeepsModelValid() {
        var result = mapper.map(detection(List.of(clazz("cliente", "Cliente"), clazz("pedido", "Pedido")),
                List.of(relation("r1", "ASSOCIATION", "cliente", "pedido", null, null))), "d", "D");
        var relation = result.getCanonicalModel().getRelations().getFirst();
        assertEquals("1", relation.getSourceMultiplicity().getLower());
        assertEquals("1", relation.getSourceMultiplicity().getUpper());
        assertEquals("1", relation.getTargetMultiplicity().getLower());
        assertEquals("1", relation.getTargetMultiplicity().getUpper());
        assertTrue(new CanonicalModelValidator().validate(result.getCanonicalModel()).isValid());
        assertEquals(2, result.getWarnings().size());
    }

    @Test
    void mapsAssociationClassLinkToCanonicalStructure() {
        var base = detection(List.of(clazz("a", "Alumno"), clazz("m", "Materia"), clazz("i", "Inscripcion")),
                List.of(relation("enrollment", "ASSOCIATION", "a", "m", "*", "*")));
        var enriched = new AiUmlDetectionResponse(true,
                new AiUmlDetectionResponse.DiagramDetection(base.diagram().classes(), base.diagram().relations(),
                        List.of(new AiUmlDetectionResponse.AssociationClassDetection("enrollment", null, "i"))), List.of(), .9, "");
        assertEquals(1, mapper.map(enriched, "d", "D").getCanonicalModel().getAssociationClassLinks().size());
    }

    @Test
    void rejectsUnknownRelationEndpointAndDuplicateClassName() {
        assertThrows(IllegalArgumentException.class, () -> mapper.map(detection(List.of(clazz("a", "A")),
                List.of(relation("r", "ASSOCIATION", "a", "missing", "1", "1"))), "d", "D"));
        assertThrows(IllegalArgumentException.class, () -> mapper.map(detection(
                List.of(clazz("a", "Cliente"), clazz("b", "cliente")), List.of()), "d", "D"));
    }

    @Test
    void usesSafeDefaultsAndWarningsForIllegibleTypesAndMultiplicity() {
        var attribute = new AiUmlDetectionResponse.AttributeDetection("dato", null, null, false, false, null, false);
        var item = new AiUmlDetectionResponse.ClassDetection("a", "A", false, List.of(attribute), List.of());
        var result = mapper.map(detection(List.of(item), List.of(relation("r", "ASSOCIATION", "a", "a", "?", "1"))), "d", "D");
        assertEquals("Object", result.getCanonicalModel().getClasses().getFirst().getAttributes().getFirst().getType());
        assertFalse(result.getWarnings().isEmpty());
    }

    private AiUmlDetectionResponse detection(List<AiUmlDetectionResponse.ClassDetection> classes,
            List<AiUmlDetectionResponse.RelationDetection> relations) {
        return new AiUmlDetectionResponse(true,
                new AiUmlDetectionResponse.DiagramDetection(classes, relations, List.of()), List.of(), .92, "");
    }
    private AiUmlDetectionResponse.ClassDetection clazz(String ref, String name) {
        return new AiUmlDetectionResponse.ClassDetection(ref, name, false, List.of(), List.of());
    }
    private AiUmlDetectionResponse.RelationDetection relation(String ref, String type, String source, String target,
            String sourceMultiplicity, String targetMultiplicity) {
        return new AiUmlDetectionResponse.RelationDetection(ref, type, null, null, source, target,
                sourceMultiplicity, targetMultiplicity, null, null, false, false);
    }
}
