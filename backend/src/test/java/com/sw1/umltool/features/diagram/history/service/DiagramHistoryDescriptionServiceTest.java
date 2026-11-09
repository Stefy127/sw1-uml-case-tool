package com.sw1.umltool.features.diagram.history.service;

import com.sw1.umltool.features.diagram.model.canonical.UmlAttribute;
import com.sw1.umltool.features.diagram.model.canonical.UmlClass;
import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.service.DiagramStateSerializer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagramHistoryDescriptionServiceTest {
    private final DiagramStateSerializer serializer = new DiagramStateSerializer(new ObjectMapper());
    private final DiagramHistoryDescriptionService service = new DiagramHistoryDescriptionService(serializer);

    @Test
    void describesAddedAttributeWithoutExposingIds() {
        UmlDiagram before = diagram(List.of());
        UmlDiagram after = diagram(List.of(attribute("a1", "Apellido", "String")));

        var result = service.describe(serializer.serializeCanonical(before), serializer.serializeCanonical(after), "ADD_ATTRIBUTE");

        assertEquals("Se agregó el atributo Apellido: String a Cliente.", result.summary());
        assertTrue(result.details().get(0).contains("Apellido: String"));
        assertTrue(result.details().stream().noneMatch(item -> item.contains("a1")));
    }

    @Test
    void describesUndoAsTheActualReverseEffect() {
        UmlDiagram before = diagram(List.of(attribute("a1", "Apellido", "String")));
        UmlDiagram after = diagram(List.of());

        var result = service.describe(serializer.serializeCanonical(before), serializer.serializeCanonical(after), "ADD_ATTRIBUTE");

        assertEquals("Se eliminó el atributo Apellido: String de Cliente.", result.summary());
    }

    @Test
    void fallsBackToNeutralDescriptionWhenThereIsNoDetectableChange() {
        UmlDiagram value = diagram(List.of());

        var result = service.describe(serializer.serializeCanonical(value), serializer.serializeCanonical(value), "UNKNOWN");

        assertEquals("Se modificó el diagrama.", result.summary());
        assertTrue(result.details().isEmpty());
    }

    private UmlDiagram diagram(List<UmlAttribute> attributes) {
        return UmlDiagram.builder().id("d1").classes(List.of(UmlClass.builder().id("c1").name("Cliente").attributes(attributes).build())).build();
    }

    private UmlAttribute attribute(String id, String name, String type) {
        return UmlAttribute.builder().id(id).name(name).type(type).build();
    }
}
