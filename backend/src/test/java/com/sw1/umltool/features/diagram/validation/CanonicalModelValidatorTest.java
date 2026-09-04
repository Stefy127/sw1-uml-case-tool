package com.sw1.umltool.features.diagram.validation;

import com.sw1.umltool.features.diagram.model.canonical.Multiplicity;
import com.sw1.umltool.features.diagram.model.canonical.UmlAttribute;
import com.sw1.umltool.features.diagram.model.canonical.UmlClass;
import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.canonical.UmlRelation;
import com.sw1.umltool.features.diagram.model.canonical.enums.RelationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CanonicalModelValidatorTest {

    private CanonicalModelValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CanonicalModelValidator();
    }

    @Test
    void shouldAcceptValidDiagram() {

        UmlClass cliente = UmlClass.builder()
                .id("c1")
                .name("Cliente")
                .attributes(List.of(
                        UmlAttribute.builder()
                                .id("a1")
                                .name("id")
                                .type("Long")
                                .primaryKey(true)
                                .build()
                ))
                .build();

        UmlClass pedido = UmlClass.builder()
                .id("c2")
                .name("Pedido")
                .build();

        UmlRelation relation = UmlRelation.builder()
                .id("r1")
                .sourceClassId("c1")
                .targetClassId("c2")
                .type(RelationType.ASSOCIATION)
                .sourceMultiplicity(new Multiplicity("1", "1"))
                .targetMultiplicity(new Multiplicity("0", "*"))
                .build();

        UmlDiagram diagram = UmlDiagram.builder()
                .id("d1")
                .name("Ventas")
                .classes(List.of(cliente, pedido))
                .relations(List.of(relation))
                .build();

        ValidationResult result = validator.validate(diagram);

        assertTrue(result.isValid());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void shouldRejectDuplicateClassNames() {

        UmlClass c1 = UmlClass.builder()
                .id("c1")
                .name("Cliente")
                .build();

        UmlClass c2 = UmlClass.builder()
                .id("c2")
                .name("cliente")
                .build();

        UmlDiagram diagram = UmlDiagram.builder()
                .id("d1")
                .name("Test")
                .classes(List.of(c1, c2))
                .build();

        ValidationResult result = validator.validate(diagram);

        assertFalse(result.isValid());

        assertTrue(
                result.getErrors().stream()
                        .anyMatch(e ->
                                "DUPLICATE_CLASS_NAME".equals(e.getCode())
                        )
        );
    }

    @Test
    void shouldRejectDuplicateAttributeNames() {

        UmlAttribute a1 = UmlAttribute.builder()
                .id("a1")
                .name("email")
                .type("String")
                .build();

        UmlAttribute a2 = UmlAttribute.builder()
                .id("a2")
                .name("Email")
                .type("String")
                .build();

        UmlClass cliente = UmlClass.builder()
                .id("c1")
                .name("Cliente")
                .attributes(List.of(a1, a2))
                .build();

        UmlDiagram diagram = UmlDiagram.builder()
                .id("d1")
                .name("Test")
                .classes(List.of(cliente))
                .build();

        ValidationResult result = validator.validate(diagram);

        assertFalse(result.isValid());

        assertTrue(
                result.getErrors().stream()
                        .anyMatch(e ->
                                "DUPLICATE_ATTRIBUTE_NAME".equals(e.getCode())
                        )
        );
    }

    @Test
    void shouldRejectRelationToMissingClass() {

        UmlClass cliente = UmlClass.builder()
                .id("c1")
                .name("Cliente")
                .build();

        UmlRelation relation = UmlRelation.builder()
                .id("r1")
                .sourceClassId("c1")
                .targetClassId("c999")
                .type(RelationType.ASSOCIATION)
                .sourceMultiplicity(new Multiplicity("1", "1"))
                .targetMultiplicity(new Multiplicity("0", "*"))
                .build();

        UmlDiagram diagram = UmlDiagram.builder()
                .id("d1")
                .name("Test")
                .classes(List.of(cliente))
                .relations(List.of(relation))
                .build();

        ValidationResult result = validator.validate(diagram);

        assertFalse(result.isValid());

        assertTrue(
                result.getErrors().stream()
                        .anyMatch(e ->
                                "INVALID_TARGET_CLASS".equals(e.getCode())
                        )
        );
    }

    @Test
    void shouldRejectInvalidMultiplicityRange() {

        UmlClass c1 = UmlClass.builder()
                .id("c1")
                .name("Cliente")
                .build();

        UmlClass c2 = UmlClass.builder()
                .id("c2")
                .name("Pedido")
                .build();

        UmlRelation relation = UmlRelation.builder()
                .id("r1")
                .sourceClassId("c1")
                .targetClassId("c2")
                .type(RelationType.ASSOCIATION)
                .sourceMultiplicity(new Multiplicity("5", "2"))
                .targetMultiplicity(new Multiplicity("0", "*"))
                .build();

        UmlDiagram diagram = UmlDiagram.builder()
                .id("d1")
                .name("Test")
                .classes(List.of(c1, c2))
                .relations(List.of(relation))
                .build();

        ValidationResult result = validator.validate(diagram);

        assertFalse(result.isValid());

        assertTrue(
                result.getErrors().stream()
                        .anyMatch(e ->
                                "INVALID_SOURCE_MULTIPLICITY_RANGE".equals(e.getCode())
                        )
        );
    }

    @Test
    void shouldRejectNegativeMultiplicity() {

        UmlClass c1 = UmlClass.builder()
                .id("c1")
                .name("Cliente")
                .build();

        UmlClass c2 = UmlClass.builder()
                .id("c2")
                .name("Pedido")
                .build();

        UmlRelation relation = UmlRelation.builder()
                .id("r1")
                .sourceClassId("c1")
                .targetClassId("c2")
                .type(RelationType.ASSOCIATION)
                .sourceMultiplicity(new Multiplicity("-1", "1"))
                .targetMultiplicity(new Multiplicity("0", "*"))
                .build();

        UmlDiagram diagram = UmlDiagram.builder()
                .id("d1")
                .name("Test")
                .classes(List.of(c1, c2))
                .relations(List.of(relation))
                .build();

        ValidationResult result = validator.validate(diagram);

        assertFalse(result.isValid());

        assertTrue(
                result.getErrors().stream()
                        .anyMatch(e ->
                                "INVALID_SOURCE_LOWER_MULTIPLICITY".equals(e.getCode())
                        )
        );
    }

    @Test
    void shouldAllowReflexiveRelation() {

        UmlClass empleado = UmlClass.builder()
                .id("c1")
                .name("Empleado")
                .build();

        UmlRelation relation = UmlRelation.builder()
                .id("r1")
                .sourceClassId("c1")
                .targetClassId("c1")
                .type(RelationType.ASSOCIATION)
                .sourceMultiplicity(new Multiplicity("0", "1"))
                .targetMultiplicity(new Multiplicity("0", "*"))
                .build();

        UmlDiagram diagram = UmlDiagram.builder()
                .id("d1")
                .name("Organizacion")
                .classes(List.of(empleado))
                .relations(List.of(relation))
                .build();

        ValidationResult result = validator.validate(diagram);

        assertTrue(result.isValid());
    }
}