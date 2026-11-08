package com.sw1.umltool.features.ai.suggestions.service;

import com.sw1.umltool.features.ai.analysis.exception.AiAnalysisException;
import com.sw1.umltool.features.ai.analysis.service.AiAnalysisContextService;
import com.sw1.umltool.features.ai.chat.service.AiOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiSuggestionServiceTest {
    private AiAnalysisContextService context;
    private AiOrchestrator orchestrator;
    private AiSuggestionService service;

    @BeforeEach
    void setUp() {
        context = mock(AiAnalysisContextService.class);
        orchestrator = mock(AiOrchestrator.class);
        service = new AiSuggestionService(context, orchestrator, new ObjectMapper(), 2);
    }

    @Test
    void validatesIdsDeduplicatesSuggestionsAndDoesNotMutateTheModel() {
        var prepared = new AiAnalysisContextService.PreparedContext("p1", "context",
                Map.of("class-1", new AiAnalysisContextService.ElementDescriptor(
                        "class-1", AiAnalysisContextService.ElementKind.CLASS, "Cliente")));
        when(context.prepare("p1", "u1", "d1")).thenReturn(prepared);
        when(orchestrator.submitPrompt(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(
                "{\"suggestions\":[" +
                        "{\"category\":\"ATTRIBUTE\",\"elementId\":\"class-1\",\"title\":\"Revisar apellido\",\"reason\":\"Falta contexto\",\"proposal\":\"Evaluar su significado\",\"requiresBusinessContext\":true}," +
                        "{\"category\":\"ATTRIBUTE\",\"elementId\":\"class-1\",\"title\":\"Revisar apellido\",\"reason\":\"Duplicada\",\"proposal\":\"Revisar\",\"requiresBusinessContext\":true}]}"));

        var response = service.suggest("p1", "u1", "d1");

        assertEquals(1, response.suggestions().size());
        assertEquals("d1", response.diagramId());
    }

    @Test
    void rejectsUnknownElementIds() {
        when(context.prepare("p1", "u1", null)).thenReturn(
                new AiAnalysisContextService.PreparedContext("p1", "context", Map.of()));
        when(orchestrator.submitPrompt(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(
                "{\"suggestions\":[{\"category\":\"GENERAL\",\"elementId\":\"missing\",\"title\":\"x\",\"reason\":\"x\",\"proposal\":\"x\"}]}"));

        assertThrows(AiAnalysisException.class, () -> service.suggest("p1", "u1", null));
    }

    @Test
    void acceptsAnEmptySuggestionList() {
        when(context.prepare("p1", "u1", null)).thenReturn(
                new AiAnalysisContextService.PreparedContext("p1", "context", Map.of()));
        when(orchestrator.submitPrompt(anyString(), anyString())).thenReturn(
                CompletableFuture.completedFuture("{\"suggestions\":[]}"));

        assertEquals(0, service.suggest("p1", "u1", null).suggestions().size());
    }

    @Test
    void sendsFocusedConditionalGuidanceAndKeepsAtMostThreeSimulatedSuggestions() {
        when(context.prepare("p1", "u1", null)).thenReturn(
                new AiAnalysisContextService.PreparedContext("p1", "context", Map.of()));
        when(orchestrator.submitPrompt(anyString(), eq("context"))).thenReturn(CompletableFuture.completedFuture(
                "{\"suggestions\":[" +
                        "{\"category\":\"GENERAL\",\"elementId\":null,\"title\":\"A\",\"reason\":\"r\",\"proposal\":\"p\"}," +
                        "{\"category\":\"GENERAL\",\"elementId\":null,\"title\":\"B\",\"reason\":\"r\",\"proposal\":\"p\"}," +
                        "{\"category\":\"GENERAL\",\"elementId\":null,\"title\":\"C\",\"reason\":\"r\",\"proposal\":\"p\"}]}"));

        var response = service.suggest("p1", "u1", null);
        var prompt = forClass(String.class);
        verify(orchestrator).submitPrompt(prompt.capture(), eq("context"));

        assertEquals(3, response.suggestions().size());
        assertTrue(prompt.getValue().contains("maximo 3"));
        assertTrue(prompt.getValue().contains("ciclo de vida"));
        assertTrue(prompt.getValue().contains("no propongas metodos solo porque no existan"));
    }

    @Test
    void dropsUnsafeGenericModificationSuggestionsForAnEmptyVentasModel() {
        var descriptors = Map.of(
                "cliente-id", new AiAnalysisContextService.ElementDescriptor(
                        "cliente-id", AiAnalysisContextService.ElementKind.CLASS, "Cliente"),
                "pedido-id", new AiAnalysisContextService.ElementDescriptor(
                        "pedido-id", AiAnalysisContextService.ElementKind.CLASS, "Pedido"));
        when(context.prepare("ventas", "u1", "d1")).thenReturn(
                new AiAnalysisContextService.PreparedContext("ventas", "Cliente; Pedido", descriptors));
        when(orchestrator.submitPrompt(anyString(), eq("Cliente; Pedido"))).thenReturn(CompletableFuture.completedFuture(
                "{\"suggestions\":[" +
                        "{\"category\":\"ATTRIBUTE\",\"elementId\":\"cliente-id\",\"title\":\"Agregar id\",\"reason\":\"La clase está vacía\",\"proposal\":\"Crear un atributo\",\"requiresBusinessContext\":false}," +
                        "{\"category\":\"OPERATION\",\"elementId\":\"pedido-id\",\"title\":\"Agregar cancelar\",\"reason\":\"No hay métodos\",\"proposal\":\"Crear cancelar()\",\"requiresBusinessContext\":true}," +
                        "{\"category\":\"RELATIONSHIP\",\"elementId\":null,\"title\":\"Agregar composiciÃ³n\",\"reason\":\"No hay relaciones\",\"proposal\":\"Crear una composiciÃ³n\",\"requiresBusinessContext\":true}]}"));

        var response = service.suggest("ventas", "u1", "d1");

        assertEquals(0, response.suggestions().size());
        assertEquals("No se encontraron sugerencias para revisar.", response.summary());
    }
}
