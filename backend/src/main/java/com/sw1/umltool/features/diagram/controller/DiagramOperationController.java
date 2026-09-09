package com.sw1.umltool.features.diagram.controller;

import com.sw1.umltool.features.diagram.dto.ExecuteDiagramOperationRequest;
import com.sw1.umltool.features.diagram.dto.OperationExecutionResponse;
import com.sw1.umltool.features.diagram.service.PersistentDiagramOperationService;
import com.sw1.umltool.features.diagram.operation.DiagramOperationPayloadMapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/diagrams/{diagramId}/operations")
public class DiagramOperationController {

    private final PersistentDiagramOperationService persistentDiagramOperationService;
    private final DiagramOperationPayloadMapper payloadMapper;

    public DiagramOperationController(PersistentDiagramOperationService persistentDiagramOperationService,
                                      DiagramOperationPayloadMapper payloadMapper) {
        this.persistentDiagramOperationService = persistentDiagramOperationService;
        this.payloadMapper = payloadMapper;
    }

    @PostMapping
    public ResponseEntity<OperationExecutionResponse> execute(
            @PathVariable String diagramId,
            @Valid @RequestBody ExecuteDiagramOperationRequest request, Authentication authentication) {
        var operation = payloadMapper.map(request.getOperation());
        if (authentication != null) operation.setUserId(authentication.getName());
        return ResponseEntity.ok(OperationExecutionResponse.from(
                authentication == null ? persistentDiagramOperationService.execute(diagramId, operation) : persistentDiagramOperationService.execute(diagramId, operation, authentication.getName())));
    }
}
