package com.sw1.umltool.features.diagram.service;

import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.persistence.DiagramEntity;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;
import com.sw1.umltool.features.diagram.operation.DiagramOperation;
import com.sw1.umltool.features.diagram.service.DiagramOperationService;
import com.sw1.umltool.features.diagram.operation.OperationApplicationException;
import com.sw1.umltool.features.diagram.operation.OperationExecutionResult;
import com.sw1.umltool.features.diagram.operation.VersionConflictException;
import com.sw1.umltool.features.diagram.repository.DiagramRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PersistentDiagramOperationService {

    private final DiagramRepository diagramRepository;
    private final DiagramStateSerializer diagramStateSerializer;
    private final DiagramOperationService diagramOperationService;
    private final com.sw1.umltool.features.project.service.ProjectAccessService access;

    public PersistentDiagramOperationService(
            DiagramRepository diagramRepository,
            DiagramStateSerializer diagramStateSerializer,
            DiagramOperationService diagramOperationService) {
        this.diagramRepository = diagramRepository;
        this.diagramStateSerializer = diagramStateSerializer;
        this.diagramOperationService = diagramOperationService;
        this.access = null;
    }
    @org.springframework.beans.factory.annotation.Autowired
    public PersistentDiagramOperationService(DiagramRepository repository, DiagramStateSerializer serializer, DiagramOperationService operationService, com.sw1.umltool.features.project.service.ProjectAccessService access) { this.diagramRepository=repository; this.diagramStateSerializer=serializer; this.diagramOperationService=operationService; this.access=access; }

    @Transactional
    public OperationExecutionResult execute(String diagramId, DiagramOperation operation) {
        return executeInternal(diagramId, operation, null);
    }
    public OperationExecutionResult execute(String diagramId, DiagramOperation operation, String userId) {
        return executeInternal(diagramId, operation, userId);
    }
    private OperationExecutionResult executeInternal(String diagramId, DiagramOperation operation, String userId) {
        if (isBlank(diagramId)) {
            throw new OperationApplicationException("Diagram id is required");
        }
        if (operation == null) {
            throw new OperationApplicationException("Operation is required");
        }
        if (!diagramId.equals(operation.getDiagramId())) {
            throw new OperationApplicationException("Operation diagram id does not match requested diagram id");
        }

        DiagramEntity entity = diagramRepository.findById(diagramId)
                .orElseThrow(() -> new DiagramNotFoundException("Diagram not found: " + diagramId));
        if (userId != null) access.requireDiagramEditor(diagramId, userId);
        UmlDiagram diagram = diagramStateSerializer.deserializeCanonical(entity.getCanonicalModelJson());
        DiagramViewState viewState = diagramStateSerializer.deserializeViewState(entity.getViewStateJson());

        if (entity.getVersion() != diagram.getVersion()) {
            throw new VersionConflictException("Persisted version " + entity.getVersion()
                    + " does not match canonical model version " + diagram.getVersion());
        }

        OperationExecutionResult result = diagramOperationService.execute(operation, diagram, viewState);

        entity.setCanonicalModelJson(diagramStateSerializer.serializeCanonical(result.getDiagram()));
        entity.setViewStateJson(diagramStateSerializer.serializeViewState(result.getViewState()));
        entity.setVersion(result.getNewVersion());
        entity.setUpdatedAt(LocalDateTime.now());
        diagramRepository.save(entity);

        return result;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
