package com.sw1.umltool.features.ai.voice.controller;

import com.sw1.umltool.common.exception.GlobalExceptionHandler;
import com.sw1.umltool.features.ai.voice.dto.AiVoiceInterpretResponse;
import com.sw1.umltool.features.ai.voice.service.AiVoiceInterpretationService;
import com.sw1.umltool.features.ai.voice.service.AiVoiceTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiVoiceControllerTest {
    private AiVoiceInterpretationService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(AiVoiceInterpretationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AiVoiceController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void acceptsTextAndReturnsValidatedCommandShape() throws Exception {
        when(service.interpret(any())).thenReturn(new AiVoiceInterpretResponse(true,
                new AiVoiceInterpretResponse.AiCommand("CREATE_RELATION", null, null, "Empleado", "Persona", null, null, null, null, "INHERITANCE"),
                0.94, "Empleado hereda de Persona", java.util.List.of()));

        mockMvc.perform(post("/api/ai/voice/interpret").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"haz que Empleado herede de Persona\",\"language\":\"es-BO\",\"diagramContext\":{\"classes\":[{\"id\":\"e\",\"name\":\"Empleado\"},{\"id\":\"p\",\"name\":\"Persona\"}]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.command.type").value("CREATE_RELATION"))
                .andExpect(jsonPath("$.command.relationType").value("INHERITANCE"));
    }

    @Test
    void mapsAiTimeoutToServiceUnavailable() throws Exception {
        when(service.interpret(any())).thenThrow(new AiVoiceTimeoutException(
                "El servicio de interpretaci\u00f3n por IA tard\u00f3 demasiado en responder.", new RuntimeException()));

        mockMvc.perform(post("/api/ai/voice/interpret").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"crear clase Cliente\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("El servicio de interpretaci\u00f3n por IA tard\u00f3 demasiado en responder."));
    }

    @Test
    void mapsInvalidAiResponseToBadRequest() throws Exception {
        when(service.interpret(any())).thenThrow(new IllegalArgumentException(
                "El servicio de IA devolvi\u00f3 una respuesta no v\u00e1lida."));

        mockMvc.perform(post("/api/ai/voice/interpret").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"crear clase Cliente\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}
