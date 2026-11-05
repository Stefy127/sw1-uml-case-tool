package com.sw1.umltool.features.ai.memory.controller;

import com.sw1.umltool.features.ai.memory.dto.AiMemoryResponse;
import com.sw1.umltool.features.ai.memory.dto.CreateAiMemoryRequest;
import com.sw1.umltool.features.ai.memory.dto.UpdateAiMemoryRequest;
import com.sw1.umltool.features.ai.memory.model.AiMemoryScope;
import com.sw1.umltool.features.ai.memory.service.AiMemoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/ai/memory")
public class AiMemoryController {
    private final AiMemoryService service;

    public AiMemoryController(AiMemoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<AiMemoryResponse> list(@PathVariable String projectId,
                                       @RequestParam AiMemoryScope scope,
                                       Authentication authentication) {
        return service.list(projectId, authentication.getName(), scope);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AiMemoryResponse create(@PathVariable String projectId,
                                   @Valid @RequestBody CreateAiMemoryRequest request,
                                   Authentication authentication) {
        return service.create(projectId, authentication.getName(), request);
    }

    @PutMapping("/{memoryId}")
    public AiMemoryResponse update(@PathVariable String projectId, @PathVariable String memoryId,
                                   @Valid @RequestBody UpdateAiMemoryRequest request,
                                   Authentication authentication) {
        return service.update(projectId, authentication.getName(), memoryId, request);
    }

    @DeleteMapping("/{memoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String projectId, @PathVariable String memoryId,
                       Authentication authentication) {
        service.delete(projectId, authentication.getName(), memoryId);
    }
}
