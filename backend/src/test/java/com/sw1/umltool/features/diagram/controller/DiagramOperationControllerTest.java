package com.sw1.umltool.features.diagram.controller;

import com.sw1.umltool.common.exception.GlobalExceptionHandler;
import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;
import com.sw1.umltool.features.diagram.operation.OperationApplicationException;
import com.sw1.umltool.features.diagram.operation.OperationExecutionResult;
import com.sw1.umltool.features.diagram.operation.VersionConflictException;
import com.sw1.umltool.features.diagram.service.DiagramNotFoundException;
import com.sw1.umltool.features.diagram.service.PersistentDiagramOperationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DiagramOperationControllerTest {

    private PersistentDiagramOperationService operationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        operationService = mock(PersistentDiagramOperationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DiagramOperationController(operationService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void validOperationReturnsOk() throws Exception {
        when(operationService.execute(org.mockito.ArgumentMatchers.eq("diagram-1"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(result());

        mockMvc.perform(post("/api/diagrams/diagram-1/operations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operation\":{\"operationId\":\"op-1\",\"diagramId\":\"diagram-1\",\"baseVersion\":0,\"type\":\"RENAME_CLASS\",\"payload\":{}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newVersion").value(1));
    }

    @Test
    void versionConflictReturns409() throws Exception {
        doThrow(new VersionConflictException("conflict")).when(operationService)
                .execute(org.mockito.ArgumentMatchers.eq("diagram-1"), org.mockito.ArgumentMatchers.any());

        mockMvc.perform(post("/api/diagrams/diagram-1/operations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operation\":{\"operationId\":\"op-1\",\"diagramId\":\"diagram-1\",\"baseVersion\":0,\"type\":\"RENAME_CLASS\",\"payload\":{}}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void applicationErrorReturns400() throws Exception {
        doThrow(new OperationApplicationException("invalid")).when(operationService)
                .execute(org.mockito.ArgumentMatchers.eq("diagram-1"), org.mockito.ArgumentMatchers.any());

        mockMvc.perform(post("/api/diagrams/diagram-1/operations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operation\":{\"operationId\":\"op-1\",\"diagramId\":\"diagram-1\",\"baseVersion\":0,\"type\":\"RENAME_CLASS\",\"payload\":{}}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OPERATION_APPLICATION_ERROR"));
    }

    @Test
    void missingDiagramReturns404() throws Exception {
        doThrow(new DiagramNotFoundException("missing")).when(operationService)
                .execute(org.mockito.ArgumentMatchers.eq("diagram-1"), org.mockito.ArgumentMatchers.any());

        mockMvc.perform(post("/api/diagrams/diagram-1/operations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operation\":{\"operationId\":\"op-1\",\"diagramId\":\"diagram-1\",\"baseVersion\":0,\"type\":\"RENAME_CLASS\",\"payload\":{}}}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DIAGRAM_NOT_FOUND"));
    }

    private OperationExecutionResult result() {
        return OperationExecutionResult.builder().operationId("op-1").diagramId("diagram-1")
                .previousVersion(0).newVersion(1).diagram(UmlDiagram.builder().id("diagram-1").build())
                .viewState(DiagramViewState.builder().diagramId("diagram-1").build()).build();
    }
}
