package com.sw1.umltool.features.ai.voice.controller;

import com.sw1.umltool.features.ai.voice.dto.AiVoiceInterpretRequest;
import com.sw1.umltool.features.ai.voice.dto.AiVoiceInterpretResponse;
import com.sw1.umltool.features.ai.voice.service.AiVoiceInterpretationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/voice")
public class AiVoiceController {
    private final AiVoiceInterpretationService service;
    public AiVoiceController(AiVoiceInterpretationService service) { this.service = service; }

    @PostMapping("/interpret")
    public ResponseEntity<AiVoiceInterpretResponse> interpret(@Valid @RequestBody AiVoiceInterpretRequest request) {
        return ResponseEntity.ok(service.interpret(request));
    }
}
