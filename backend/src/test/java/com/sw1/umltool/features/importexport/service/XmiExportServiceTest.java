package com.sw1.umltool.features.importexport.service;

import com.sw1.umltool.features.diagram.model.canonical.Multiplicity;
import com.sw1.umltool.features.diagram.model.canonical.AssociationClassLink;
import com.sw1.umltool.features.diagram.model.canonical.UmlClass;
import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.canonical.UmlRelation;
import com.sw1.umltool.features.diagram.model.canonical.enums.RelationType;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;
import com.sw1.umltool.features.diagram.service.DiagramStateSerializer;
import com.sw1.umltool.features.importexport.parser.XmiParser;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmiExportServiceTest {
    private final DiagramStateSerializer serializer = new DiagramStateSerializer(new ObjectMapper());
    private final XmiExportService exporter = new XmiExportService(serializer);

    @Test
    void exportsXmiThatCanBeImportedAgainWithSemanticsPreserved() {
        UmlClass cliente = UmlClass.builder().id("class-client").name("Cliente").build();
        UmlClass pedido = UmlClass.builder().id("class-order").name("Pedido").build();
        UmlRelation relation = UmlRelation.builder().id("relation-1").sourceClassId(cliente.getId()).targetClassId(pedido.getId())
                .type(RelationType.ASSOCIATION).sourceMultiplicity(Multiplicity.builder().lower("1").upper("1").build())
                .targetMultiplicity(Multiplicity.builder().lower("0").upper("*").build()).build();
        UmlDiagram original = UmlDiagram.builder().id("diagram-1").name("Ventas & Clientes").classes(java.util.List.of(cliente, pedido)).relations(java.util.List.of(relation)).build();

        byte[] xml = exporter.export(original, DiagramViewState.builder().diagramId("diagram-1").build());
        String text = new String(xml, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(text.contains("uml:Class"));
        assertTrue(text.contains("uml:Package"));
        assertTrue(text.contains("xmi:type=\"uml:Model\""));
        assertTrue(text.contains("<memberEnd"));
        assertTrue(text.contains("Ventas &amp; Clientes"));

        UmlDiagram imported = new XmiParser().parse(xml, "d", "D").getCanonicalModel();
        assertEquals(2, imported.getClasses().size());
        assertEquals(1, imported.getRelations().size());
        assertEquals(RelationType.ASSOCIATION, imported.getRelations().getFirst().getType());
        assertEquals("1", imported.getRelations().getFirst().getSourceMultiplicity().getLower());
        assertEquals("*", imported.getRelations().getFirst().getTargetMultiplicity().getUpper());
    }

    @Test
    void exportsAssociationAggregationAndCompositionWithoutWritingAttributesAfterChildren() {
        UmlClass whole = UmlClass.builder().id("whole").name("Whole").build();
        UmlClass part = UmlClass.builder().id("part").name("Part").build();
        for (RelationType type : java.util.List.of(RelationType.ASSOCIATION, RelationType.AGGREGATION, RelationType.COMPOSITION)) {
            UmlRelation relation = UmlRelation.builder().id("relation-" + type).sourceClassId(whole.getId()).targetClassId(part.getId())
                    .type(type).sourceRole("whole").targetRole("part")
                    .sourceMultiplicity(Multiplicity.builder().lower("1").upper("1").build())
                    .targetMultiplicity(Multiplicity.builder().lower("0").upper("*").build()).build();

            byte[] xml = exporter.export(UmlDiagram.builder().id("diagram-" + type).name("Relations").classes(java.util.List.of(whole, part)).relations(java.util.List.of(relation)).build(), DiagramViewState.builder().build());
            assertTrue(xml.length > 0);
            assertDoesNotThrow(() -> new XmiParser().parse(xml, "d", "D"));
        }
    }

    @Test
    void exportsEnterpriseArchitectGeneralizationConnectorsWithChildAndParentOrientation() {
        UmlClass alumno = UmlClass.builder().id("alumno").name("Alumno").build();
        UmlClass profesor = UmlClass.builder().id("profesor").name("Profesor").build();
        UmlClass persona = UmlClass.builder().id("persona").name("Persona").build();
        UmlRelation alumnoInheritance = UmlRelation.builder().id("alumno-persona").sourceClassId(alumno.getId()).targetClassId(persona.getId()).type(RelationType.INHERITANCE).build();
        UmlRelation profesorInheritance = UmlRelation.builder().id("profesor-persona").sourceClassId(profesor.getId()).targetClassId(persona.getId()).type(RelationType.INHERITANCE).build();

        String text = new String(exporter.export(UmlDiagram.builder().id("inheritance").name("Herencia")
                .classes(java.util.List.of(alumno, profesor, persona)).relations(java.util.List.of(alumnoInheritance, profesorInheritance)).build(),
                DiagramViewState.builder().build()), java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(text.contains("xmi:type=\"uml:Generalization\""));
        assertTrue(text.contains("specific=\"EAID_ALUMNO\" general=\"EAID_PERSONA\""));
        assertTrue(text.contains("specific=\"EAID_PROFESOR\" general=\"EAID_PERSONA\""));
        assertTrue(text.contains("ea_type=\"Generalization\""));
        UmlDiagram imported = new XmiParser().parse(text.getBytes(java.nio.charset.StandardCharsets.UTF_8), "d", "D").getCanonicalModel();
        assertEquals(2, imported.getRelations().stream().filter(r -> r.getType() == RelationType.INHERITANCE).count());
    }

    @Test
    void exportsAssociationClassAsUmlAssociationClassAndEaConnector() {
        UmlClass alumno = UmlClass.builder().id("alumno").name("Alumno").build();
        UmlClass materia = UmlClass.builder().id("materia").name("Materia").build();
        UmlClass inscripcion = UmlClass.builder().id("inscripcion").name("ClaseAsociacion1").build();
        UmlRelation relation = UmlRelation.builder().id("alumno-materia").sourceClassId(alumno.getId()).targetClassId(materia.getId())
                .type(RelationType.ASSOCIATION).sourceMultiplicity(Multiplicity.builder().lower("1").upper("1").build())
                .targetMultiplicity(Multiplicity.builder().lower("0").upper("*").build()).build();
        UmlDiagram diagram = UmlDiagram.builder().id("association-class").name("Inscripciones")
                .classes(java.util.List.of(alumno, materia, inscripcion)).relations(java.util.List.of(relation))
                .associationClassLinks(java.util.List.of(new AssociationClassLink("link-1", relation.getId(), inscripcion.getId()))).build();

        String text = new String(exporter.export(diagram, DiagramViewState.builder().build()), java.nio.charset.StandardCharsets.UTF_8);

        assertTrue(text.contains("xmi:type=\"uml:AssociationClass\""));
        assertTrue(text.contains("ea_type=\"Association\""));
        assertTrue(text.contains("subtype=\"Class\""));
        assertTrue(text.contains("associationclass=\"EAID_INSCRIPCION\""));
        UmlDiagram imported = new XmiParser().parse(text.getBytes(java.nio.charset.StandardCharsets.UTF_8), "d", "D").getCanonicalModel();
        assertEquals(1, imported.getAssociationClassLinks().size());
        assertEquals(3, imported.getClasses().size());
    }

    @Test
    void exportsAggregationAndCompositionOnSourceWholeEnd() {
        UmlClass whole = UmlClass.builder().id("whole").name("Carrera").build();
        UmlClass part = UmlClass.builder().id("part").name("Materia").build();
        UmlClass course = UmlClass.builder().id("course").name("Curso").build();
        UmlClass schedule = UmlClass.builder().id("schedule").name("Horario").build();
        UmlRelation aggregation = UmlRelation.builder().id("aggregation").sourceClassId(whole.getId()).targetClassId(part.getId()).type(RelationType.AGGREGATION).build();
        UmlRelation composition = UmlRelation.builder().id("composition").sourceClassId(course.getId()).targetClassId(schedule.getId()).type(RelationType.COMPOSITION).build();
        String text = new String(exporter.export(UmlDiagram.builder().id("ownership").name("Ownership")
                .classes(java.util.List.of(whole, part, course, schedule)).relations(java.util.List.of(aggregation, composition)).build(),
                DiagramViewState.builder().build()), java.nio.charset.StandardCharsets.UTF_8);

        String aggregationSourceEnd = ownedEnd(text, "EAID_AGGREGATIONSOURCE");
        String aggregationTargetEnd = ownedEnd(text, "EAID_AGGREGATIONTARGET");
        String compositionSourceEnd = ownedEnd(text, "EAID_COMPOSITIONSOURCE");
        String compositionTargetEnd = ownedEnd(text, "EAID_COMPOSITIONTARGET");
        assertTrue(aggregationSourceEnd.contains("aggregation=\"none\""));
        assertTrue(aggregationTargetEnd.contains("aggregation=\"shared\""));
        assertTrue(text.contains("ea_type=\"Aggregation\""));
        assertTrue(text.contains("<source xmi:idref=\"EAID_WHOLE\""));
        assertTrue(text.contains("<target xmi:idref=\"EAID_PART\""));
        assertTrue(compositionSourceEnd.contains("aggregation=\"composite\""));
        assertTrue(compositionTargetEnd.contains("aggregation=\"none\""));
        UmlDiagram imported = new XmiParser().parse(text.getBytes(java.nio.charset.StandardCharsets.UTF_8), "d", "D").getCanonicalModel();
        UmlRelation importedAggregation = imported.getRelations().stream().filter(r -> r.getType() == RelationType.AGGREGATION).findFirst().orElseThrow();
        UmlRelation importedComposition = imported.getRelations().stream().filter(r -> r.getType() == RelationType.COMPOSITION).findFirst().orElseThrow();
        assertEquals("Carrera", imported.getClasses().stream().filter(c -> c.getId().equals(importedAggregation.getSourceClassId())).findFirst().orElseThrow().getName());
        assertEquals("Curso", imported.getClasses().stream().filter(c -> c.getId().equals(importedComposition.getSourceClassId())).findFirst().orElseThrow().getName());
    }

    private String ownedEnd(String xml, String id) {
        int idPosition = xml.indexOf("xmi:id=\"" + id);
        int start = xml.lastIndexOf("<ownedEnd", idPosition);
        int end = xml.indexOf("</ownedEnd>", start);
        assertFalse(start < 0 || end < 0);
        return xml.substring(start, end);
    }
}
