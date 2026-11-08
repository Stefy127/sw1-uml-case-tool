package com.sw1.umltool.features.ai.chat.service;

import com.sw1.umltool.features.ai.chat.model.AiContextScope;
import com.sw1.umltool.features.diagram.model.canonical.UmlAttribute;
import com.sw1.umltool.features.diagram.model.canonical.UmlClass;
import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.persistence.DiagramEntity;
import com.sw1.umltool.features.diagram.repository.DiagramRepository;
import com.sw1.umltool.features.diagram.service.DiagramStateSerializer;
import com.sw1.umltool.features.project.service.ProjectAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiFactualQueryServiceTest {
    private final DiagramRepository diagrams = mock(DiagramRepository.class);
    private final DiagramStateSerializer serializer = mock(DiagramStateSerializer.class);
    private final ProjectAccessService access = mock(ProjectAccessService.class);
    private AiFactualQueryService service;

    @BeforeEach
    void setUp() {
        service = new AiFactualQueryService(diagrams, serializer, access);
        when(diagrams.countByProjectId("project-1")).thenReturn(2L);
        when(diagrams.findByProjectId(eq("project-1"), any())).thenReturn(new PageImpl<>(List.of(diagram("d1", "Clases"), diagram("d2", "Casos"))));
    }

    @Test
    void answersDiagramInventoryWithoutLoadingCanonicalModels() {
        var answer = service.answer("project-1", "user-1", "¿Qué diagramas existen en este proyecto?", null, AiContextScope.PROJECT);

        assertTrue(answer.isPresent());
        assertTrue(answer.get().contains("Clases") && answer.get().contains("Casos"));
    }

    @Test
    void answersClassAttributesFromAuthorizedDiagramWithoutExposingInternalIds() {
        DiagramEntity entity = diagram("d1", "Clases");
        UmlClass client = UmlClass.builder().id("class-1").name("Cliente")
                .attributes(List.of(UmlAttribute.builder().id("attribute-1").name("nombre").type("String").build())).build();
        when(diagrams.findByIdAndProjectId("d1", "project-1")).thenReturn(Optional.of(entity));
        when(serializer.deserializeCanonical(entity.getCanonicalModelJson())).thenReturn(UmlDiagram.builder().id("d1").classes(List.of(client)).build());

        var answer = service.answer("project-1", "user-1", "¿Qué atributos tiene la clase cliente?", "d1", AiContextScope.DIAGRAM);

        assertTrue(answer.isPresent());
        assertTrue(answer.get().contains("nombre : String"));
        assertTrue(!answer.get().contains("class-1") && !answer.get().contains("attribute-1"));
    }

    private DiagramEntity diagram(String id, String name) {
        return DiagramEntity.builder().id(id).projectId("project-1").name(name)
                .canonicalModelJson("{}").viewStateJson("{}").build();
    }
}
