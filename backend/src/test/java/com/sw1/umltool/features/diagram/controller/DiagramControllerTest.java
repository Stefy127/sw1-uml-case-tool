package com.sw1.umltool.features.diagram.controller;

import com.sw1.umltool.common.exception.GlobalExceptionHandler;
import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.persistence.DiagramEntity;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;
import com.sw1.umltool.features.diagram.service.DiagramService;
import com.sw1.umltool.features.diagram.service.DiagramStateSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DiagramControllerTest {

    private DiagramService diagramService;
    private DiagramStateSerializer serializer;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        diagramService = mock(DiagramService.class);
        serializer = mock(DiagramStateSerializer.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DiagramController(diagramService, serializer))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        when(serializer.deserializeCanonical("canonical-json")).thenReturn(UmlDiagram.builder()
                .id("diagram-1").name("Main").build());
        when(serializer.deserializeViewState("view-json")).thenReturn(DiagramViewState.builder()
                .diagramId("diagram-1").build());
    }

    @Test
    void validPostReturnsCreatedDetail() throws Exception {
        when(diagramService.createDiagram("project-1", "Main")).thenReturn(entity());

        mockMvc.perform(post("/api/diagrams").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"project-1\",\"name\":\"Main\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("diagram-1"))
                .andExpect(jsonPath("$.canonicalModel.id").value("diagram-1"));
    }

    @Test
    void getByProjectReturnsSummaries() throws Exception {
        when(diagramService.findByProjectId("project-1")).thenReturn(List.of(entity()));

        mockMvc.perform(get("/api/diagrams").param("projectId", "project-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].version").value(0));
    }

    @Test
    void getDetailDeserializesStates() throws Exception {
        when(diagramService.findById("diagram-1")).thenReturn(Optional.of(entity()));

        mockMvc.perform(get("/api/diagrams/diagram-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewState.diagramId").value("diagram-1"));
    }

    @Test
    void getMissingDiagramReturnsNotFound() throws Exception {
        when(diagramService.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/diagrams/missing"))
                .andExpect(status().isNotFound());
    }

    private DiagramEntity entity() {
        return DiagramEntity.builder().id("diagram-1").projectId("project-1").name("Main")
                .canonicalModelJson("canonical-json").viewStateJson("view-json").build();
    }
}
