package com.sw1.umltool.features.ai.suggestions.controller;

import com.sw1.umltool.features.ai.suggestions.dto.AiSuggestionsResponse;
import com.sw1.umltool.features.ai.suggestions.service.AiSuggestionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/ai")
public class AiSuggestionController {
    private final AiSuggestionService service;

    public AiSuggestionController(AiSuggestionService service) { this.service = service; }

    @PostMapping("/suggestions")
    public AiSuggestionsResponse suggestions(@org.springframework.web.bind.annotation.PathVariable String projectId,
                                             @RequestParam(required = false) String diagramId,
                                             Authentication authentication) {
        return service.suggest(projectId, authentication.getName(), diagramId);
    }
}
