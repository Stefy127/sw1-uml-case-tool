package com.sw1.umltool.features.diagram.history.controller;

import com.sw1.umltool.features.diagram.history.dto.DiagramHistoryAvailabilityResponse;
import com.sw1.umltool.features.diagram.history.dto.DiagramHistoryResponse;
import com.sw1.umltool.features.diagram.history.service.DiagramHistoryService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/diagrams/{diagramId}/history")
public class DiagramHistoryController {
    private final DiagramHistoryService history;

    public DiagramHistoryController(DiagramHistoryService history) {
        this.history = history;
    }

    @GetMapping
    public Page<DiagramHistoryResponse> list(@PathVariable String diagramId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "25") int size,
                                             Authentication authentication) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize);
        return history.list(diagramId, authentication.getName(), pageable);
    }

    @GetMapping("/availability")
    public DiagramHistoryAvailabilityResponse availability(@PathVariable String diagramId,
                                                           Authentication authentication) {
        return history.availability(diagramId, authentication.getName());
    }

    @PostMapping("/undo")
    public DiagramHistoryResponse undo(@PathVariable String diagramId, Authentication authentication) {
        return history.undo(diagramId, authentication.getName());
    }

    @PostMapping("/redo")
    public DiagramHistoryResponse redo(@PathVariable String diagramId, Authentication authentication) {
        return history.redo(diagramId, authentication.getName());
    }
}
