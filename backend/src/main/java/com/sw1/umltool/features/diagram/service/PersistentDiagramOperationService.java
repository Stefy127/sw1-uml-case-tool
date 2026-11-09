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
import java.util.List;

@Service
public class PersistentDiagramOperationService {

    private final DiagramRepository diagramRepository;
    private final DiagramStateSerializer diagramStateSerializer;
    private final DiagramOperationService diagramOperationService;
    private final com.sw1.umltool.features.project.service.ProjectAccessService access;
    private final com.sw1.umltool.features.diagram.history.service.DiagramHistoryService history;

    public PersistentDiagramOperationService(
            DiagramRepository diagramRepository,
            DiagramStateSerializer diagramStateSerializer,
            DiagramOperationService diagramOperationService) {
        this.diagramRepository = diagramRepository;
        this.diagramStateSerializer = diagramStateSerializer;
        this.diagramOperationService = diagramOperationService;
        this.access = null;
        this.history = null;
    }
    public PersistentDiagramOperationService(DiagramRepository repository, DiagramStateSerializer serializer, DiagramOperationService operationService, com.sw1.umltool.features.project.service.ProjectAccessService access) { this(repository, serializer, operationService, access, null); }
    @org.springframework.beans.factory.annotation.Autowired
    public PersistentDiagramOperationService(DiagramRepository repository, DiagramStateSerializer serializer, DiagramOperationService operationService, com.sw1.umltool.features.project.service.ProjectAccessService access, com.sw1.umltool.features.diagram.history.service.DiagramHistoryService history) { this.diagramRepository=repository; this.diagramStateSerializer=serializer; this.diagramOperationService=operationService; this.access=access; this.history=history; }

    @Transactional
    public OperationExecutionResult execute(String diagramId, DiagramOperation operation) {
        return executeInternal(diagramId, operation, null);
    }
    @Transactional
    public OperationExecutionResult execute(String diagramId, DiagramOperation operation, String userId) {
        return executeInternal(diagramId, operation, userId);
    }

    @Transactional
    public OperationExecutionResult executeBatch(String diagramId, List<DiagramOperation> operations, String userId) {
        return executeBatch(diagramId, operations, userId, "MANUAL");
    }
    @Transactional
    public OperationExecutionResult executeBatch(String diagramId, List<DiagramOperation> operations, String userId, String origin) {
        if (isBlank(diagramId)) throw new OperationApplicationException("Diagram id is required");
        if (operations == null || operations.isEmpty()) throw new OperationApplicationException("At least one operation is required");
        DiagramEntity entity = diagramRepository.findByIdForUpdate(diagramId)
                .orElseThrow(() -> new DiagramNotFoundException("Diagram not found: " + diagramId));
        if (userId != null && access != null) access.requireDiagramEditor(diagramId, userId);
        UmlDiagram diagram = diagramStateSerializer.deserializeCanonical(entity.getCanonicalModelJson());
        DiagramViewState viewState = diagramStateSerializer.deserializeViewState(entity.getViewStateJson());
        alignLegacyCanonicalVersion(entity, diagram, operations.get(0).getBaseVersion());
        if (entity.getVersion() != diagram.getVersion()) {
            throw new VersionConflictException("Persisted version " + entity.getVersion()
                    + " does not match canonical model version " + diagram.getVersion());
        }
        String beforeCanonical = entity.getCanonicalModelJson();
        String beforeViewState = entity.getViewStateJson();
        OperationExecutionResult result = diagramOperationService.executeBatch(operations, diagram, viewState);
        entity.setCanonicalModelJson(diagramStateSerializer.serializeCanonical(result.getDiagram()));
        entity.setViewStateJson(diagramStateSerializer.serializeViewState(result.getViewState()));
        entity.setVersion(result.getNewVersion());
        entity.setUpdatedAt(LocalDateTime.now());
        diagramRepository.save(entity);
        recordHistory(entity, userId, result.getOperationId(), operationType(operations), origin,
                result.getPreviousVersion(), beforeCanonical, beforeViewState,
                entity.getCanonicalModelJson(), entity.getViewStateJson());
        return result;
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

        DiagramEntity entity = diagramRepository.findByIdForUpdate(diagramId)
                .orElseThrow(() -> new DiagramNotFoundException("Diagram not found: " + diagramId));
        if (userId != null) access.requireDiagramEditor(diagramId, userId);
        UmlDiagram diagram = diagramStateSerializer.deserializeCanonical(entity.getCanonicalModelJson());
        DiagramViewState viewState = diagramStateSerializer.deserializeViewState(entity.getViewStateJson());
        alignLegacyCanonicalVersion(entity, diagram, operation.getBaseVersion());
        String beforeCanonical = entity.getCanonicalModelJson();
        String beforeViewState = entity.getViewStateJson();

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
        recordHistory(entity, userId, operation.getOperationId(), operation.getType().name(), "MANUAL",
                result.getPreviousVersion(), beforeCanonical, beforeViewState,
                entity.getCanonicalModelJson(), entity.getViewStateJson());

        return result;
    }

    private void recordHistory(DiagramEntity entity, String userId, String operationId, String operationType,
                               String origin, long previousVersion, String beforeCanonical, String beforeViewState,
                               String afterCanonical, String afterViewState) {
        if (history != null) history.recordApplied(entity, userId, operationId, operationType, origin,
                previousVersion, beforeCanonical, beforeViewState, afterCanonical, afterViewState);
    }

    private String operationType(List<DiagramOperation> operations) {
        return operations.size() == 1 ? operations.get(0).getType().name() : "BATCH:" + operations.stream()
                .map(value -> value.getType().name()).collect(java.util.stream.Collectors.joining(","));
    }

    /** Repairs only the historical undo/redo version drift when the client targets the persisted version. */
    private void alignLegacyCanonicalVersion(DiagramEntity entity, UmlDiagram diagram, long requestedVersion) {
        if (entity.getVersion() == diagram.getVersion()) return;
        if (entity.getVersion() == requestedVersion && diagram.getVersion() < entity.getVersion()) {
            diagram.setVersion(entity.getVersion());
            return;
        }
        throw new VersionConflictException("Persisted version " + entity.getVersion()
                + " does not match canonical model version " + diagram.getVersion());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
