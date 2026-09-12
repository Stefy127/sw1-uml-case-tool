package com.sw1.umltool.features.generator.service;

import com.sw1.umltool.features.diagram.model.canonical.UmlClass;
import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.validation.CanonicalModelValidator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeneratorValidatorTest {
    private final GeneratorValidator validator = new GeneratorValidator(new CanonicalModelValidator());

    @Test
    void acceptsValidCanonicalDiagramForGeneration() {
        UmlDiagram diagram = UmlDiagram.builder().id("d1").name("Ventas")
                .classes(List.of(UmlClass.builder().id("c1").name("Cliente").build())).build();

        assertTrue(validator.validate(diagram).isEmpty());
    }

    @Test
    void rejectsJavaKeywordsAsClassNames() {
        UmlDiagram diagram = UmlDiagram.builder().id("d1").name("Test")
                .classes(List.of(UmlClass.builder().id("c1").name("class").build())).build();

        assertTrue(validator.validate(diagram).stream().anyMatch(error -> error.contains("no válido")));
    }
}
