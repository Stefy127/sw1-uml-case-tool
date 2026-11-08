package com.sw1.umltool.features.ai.analysis.service;

import com.sw1.umltool.features.diagram.model.canonical.AssociationClassLink;
import com.sw1.umltool.features.diagram.model.canonical.UmlClass;
import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.canonical.UmlRelation;
import com.sw1.umltool.features.diagram.model.canonical.enums.RelationType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UmlKnowledgeServiceTest {
    private final UmlKnowledgeService service = new UmlKnowledgeService(6000);

    @Test
    void loadsOnlyTopicsRelevantToTheCanonicalModel() {
        UmlDiagram diagram = UmlDiagram.builder()
                .classes(List.of(UmlClass.builder().id("cliente").name("Cliente").build()))
                .build();

        var result = service.retrieve(diagram, false);

        assertEquals(List.of("classes"), result.selectedTopics().stream().toList());
        assertTrue(result.prompt().contains("SW1-UML-CLASSES"));
        assertFalse(result.prompt().contains("SW1-UML-ASSOCIATIONS_MULTIPLICITY"));
    }

    @Test
    void loadsRelationshipInheritanceAndAssociationClassKnowledge() {
        UmlRelation inheritance = UmlRelation.builder().id("inheritance").type(RelationType.INHERITANCE).build();
        UmlRelation association = UmlRelation.builder().id("association").type(RelationType.ASSOCIATION).build();
        UmlDiagram diagram = UmlDiagram.builder()
                .classes(List.of(UmlClass.builder().id("a").name("A").build()))
                .relations(List.of(inheritance, association))
                .associationClassLinks(List.of(AssociationClassLink.builder()
                        .id("link").relationId("association").classId("a").build()))
                .build();

        var result = service.retrieve(diagram, false);

        assertTrue(result.selectedTopics().contains("associations-multiplicity"));
        assertTrue(result.selectedTopics().contains("inheritance"));
        assertTrue(result.selectedTopics().contains("association-classes"));
        assertTrue(result.prompt().contains("SW1-UML-INHERITANCE"));
    }

    @Test
    void namingKnowledgeRequiresAnExplicitNamingConventionRequest() {
        UmlDiagram diagram = UmlDiagram.builder()
                .classes(List.of(UmlClass.builder().id("a").name("A").build()))
                .build();

        assertFalse(service.retrieve(diagram, false).selectedTopics().contains("naming-conventions"));
        assertTrue(service.retrieve(diagram, true).selectedTopics().contains("naming-conventions"));
    }

    @Test
    void enforcesTheKnowledgeBudget() {
        UmlDiagram diagram = UmlDiagram.builder()
                .classes(List.of(UmlClass.builder().id("a").name("A").build()))
                .build();

        var result = new UmlKnowledgeService(1000).retrieve(diagram, true);

        assertTrue(result.usedCharacters() <= 1000);
    }

    @Test
    void conversationalGuidanceKeepsSuggestionsEvidenceBasedAndBrief() {
        String guidance = service.conversationalGuidance();

        assertTrue(guidance.contains("maximo 3"));
        assertTrue(guidance.contains("No propongas metodos solo porque no existan"));
        assertTrue(guidance.contains("multiplicidades que ya sean validas"));
        assertTrue(guidance.contains("pertenencia o ciclo de vida"));
        assertTrue(guidance.contains("lenguaje condicional"));
    }
}
