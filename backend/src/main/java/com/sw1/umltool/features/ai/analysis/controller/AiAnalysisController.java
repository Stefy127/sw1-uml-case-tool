package com.sw1.umltool.features.ai.analysis.controller;

import com.sw1.umltool.features.ai.analysis.dto.AnalysisResponse;
import com.sw1.umltool.features.ai.analysis.service.AiAnalysisService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/ai")
public class AiAnalysisController {
    private final AiAnalysisService service;

    public AiAnalysisController(AiAnalysisService service) { this.service = service; }

    @PostMapping("/analyze")
    public AnalysisResponse analyze(@PathVariable String projectId,
                                    @RequestParam(required = false) String diagramId,
                                    Authentication authentication) {
        return service.analyze(projectId, authentication.getName(), diagramId);
    }
}
