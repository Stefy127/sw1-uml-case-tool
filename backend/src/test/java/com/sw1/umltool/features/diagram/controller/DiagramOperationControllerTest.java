package com.sw1.umltool.features.diagram.controller;

import com.sw1.umltool.common.exception.GlobalExceptionHandler;
import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;
import com.sw1.umltool.features.diagram.operation.OperationApplicationException;
import com.sw1.umltool.features.diagram.operation.DiagramOperationPayloadMapper;
import com.sw1.umltool.features.diagram.operation.OperationExecutionResult;
import com.sw1.umltool.features.diagram.operation.DiagramOperation;
import com.sw1.umltool.features.diagram.operation.payload.CreateClassPayload;
import com.sw1.umltool.features.diagram.operation.payload.RenameClassPayload;
import com.sw1.umltool.features.diagram.operation.VersionConflictException;
import com.sw1.umltool.features.diagram.service.DiagramNotFoundException;
import com.sw1.umltool.features.diagram.service.PersistentDiagramOperationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

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
        mockMvc = MockMvcBuilders.standaloneSetup(new DiagramOperationController(operationService,
                        new DiagramOperationPayloadMapper(new ObjectMapper())))
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

    @Test
    void httpCreateClassPayloadIsConvertedToConcreteType() throws Exception {
        when(operationService.execute(org.mockito.ArgumentMatchers.eq("diagram-1"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(resultWithClass("class-1", "Clase1", 1));

        mockMvc.perform(post("/api/diagrams/diagram-1/operations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operation\":{\"operationId\":\"op-1\",\"diagramId\":\"diagram-1\",\"userId\":\"dev-user-1\",\"baseVersion\":0,\"type\":\"CREATE_CLASS\",\"payload\":{\"classId\":\"class-1\",\"name\":\"Clase1\",\"isAbstract\":false,\"x\":80,\"y\":120,\"width\":240,\"height\":180}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newVersion").value(1))
                .andExpect(jsonPath("$.canonicalModel.classes[0].name").value("Clase1"))
                .andExpect(jsonPath("$.viewState.nodes[0].classId").value("class-1"));

        org.mockito.ArgumentCaptor<DiagramOperation> captor = org.mockito.ArgumentCaptor.forClass(DiagramOperation.class);
        org.mockito.Mockito.verify(operationService).execute(org.mockito.ArgumentMatchers.eq("diagram-1"), captor.capture());
        org.junit.jupiter.api.Assertions.assertInstanceOf(CreateClassPayload.class, captor.getValue().getPayload());
    }

    @Test
    void httpRenamePayloadIsConvertedToConcreteType() throws Exception {
        when(operationService.execute(org.mockito.ArgumentMatchers.eq("diagram-1"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(resultWithClass("class-1", "Cliente", 2));

        mockMvc.perform(post("/api/diagrams/diagram-1/operations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operation\":{\"operationId\":\"op-2\",\"diagramId\":\"diagram-1\",\"baseVersion\":1,\"type\":\"RENAME_CLASS\",\"payload\":{\"classId\":\"class-1\",\"name\":\"Cliente\"}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newVersion").value(2));

        org.mockito.ArgumentCaptor<DiagramOperation> captor = org.mockito.ArgumentCaptor.forClass(DiagramOperation.class);
        org.mockito.Mockito.verify(operationService).execute(org.mockito.ArgumentMatchers.eq("diagram-1"), captor.capture());
        org.junit.jupiter.api.Assertions.assertInstanceOf(RenameClassPayload.class, captor.getValue().getPayload());
    }

    private OperationExecutionResult result() {
        return OperationExecutionResult.builder().operationId("op-1").diagramId("diagram-1")
                .previousVersion(0).newVersion(1).diagram(UmlDiagram.builder().id("diagram-1").build())
                .viewState(DiagramViewState.builder().diagramId("diagram-1").build()).build();
    }

    private OperationExecutionResult resultWithClass(String id, String name, long version) {
        return OperationExecutionResult.builder().operationId("op").diagramId("diagram-1")
                .previousVersion(version - 1).newVersion(version)
                .diagram(UmlDiagram.builder().id("diagram-1").name("Main").version(version)
                        .classes(java.util.List.of(com.sw1.umltool.features.diagram.model.canonical.UmlClass.builder()
                                .id(id).name(name).build())).build())
                .viewState(DiagramViewState.builder().diagramId("diagram-1")
                        .nodes(java.util.List.of(com.sw1.umltool.features.diagram.model.view.NodeViewState.builder()
                                .classId(id).x(80).y(120).width(240).height(180).build())).build()).build();
    }
}
