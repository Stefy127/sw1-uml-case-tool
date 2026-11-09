package com.sw1.umltool.features.diagram.history.service;

import com.sw1.umltool.features.collaboration.websocket.CollaborationBroadcastService;
import com.sw1.umltool.features.diagram.history.dto.DiagramHistoryAvailabilityResponse;
import com.sw1.umltool.features.diagram.history.dto.DiagramHistoryResponse;
import com.sw1.umltool.features.diagram.history.model.DiagramHistoryEntity;
import com.sw1.umltool.features.diagram.history.repository.DiagramHistoryRepository;
import com.sw1.umltool.features.diagram.model.persistence.DiagramEntity;
import com.sw1.umltool.features.diagram.operation.VersionConflictException;
import com.sw1.umltool.features.diagram.repository.DiagramRepository;
import com.sw1.umltool.features.diagram.service.DiagramNotFoundException;
import com.sw1.umltool.features.diagram.service.DiagramStateSerializer;
import com.sw1.umltool.features.project.service.ProjectAccessService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class DiagramHistoryService {
    private static final String ACTIVE = "ACTIVE";
    private static final String UNDONE = "UNDONE";

    private final DiagramHistoryRepository history;
    private final DiagramRepository diagrams;
    private final DiagramStateSerializer serializer;
    private final ProjectAccessService access;
    private final CollaborationBroadcastService broadcaster;
    private final DiagramHistoryDescriptionService descriptions;

    public DiagramHistoryService(DiagramHistoryRepository history, DiagramRepository diagrams,
                                 DiagramStateSerializer serializer, ProjectAccessService access,
                                 CollaborationBroadcastService broadcaster,
                                 DiagramHistoryDescriptionService descriptions) {
        this.history = history;
        this.diagrams = diagrams;
        this.serializer = serializer;
        this.access = access;
        this.broadcaster = broadcaster;
        this.descriptions = descriptions;
    }

    public void recordApplied(DiagramEntity entity, String userId, String operationId,
                              String operationType, String origin, long previousVersion,
                              String beforeCanonical, String beforeViewState,
                              String afterCanonical, String afterViewState) {
        history.findByDiagramIdAndStateAndAction(entity.getId(), UNDONE, "APPLY").forEach(value -> value.setState("INVALIDATED"));
        history.flush();
        DiagramHistoryEntity value = new DiagramHistoryEntity();
        value.setId(UUID.randomUUID().toString());
        value.setProjectId(entity.getProjectId());
        value.setDiagramId(entity.getId());
        value.setUserId(userId == null ? "system" : userId);
        value.setOperationId(operationId);
        value.setOperationType(operationType);
        value.setOrigin(origin);
        value.setAction("APPLY");
        value.setState(ACTIVE);
        value.setPreviousVersion(previousVersion);
        value.setNewVersion(entity.getVersion());
        value.setRelatedHistoryId("");
        value.setBeforeCanonicalModelJson(beforeCanonical);
        value.setAfterCanonicalModelJson(afterCanonical);
        value.setBeforeViewStateJson(beforeViewState);
        value.setAfterViewStateJson(afterViewState);
        value.setCreatedAt(LocalDateTime.now());
        history.save(value);
    }

    @Transactional(readOnly = true)
    public Page<com.sw1.umltool.features.diagram.history.dto.DiagramHistoryResponse> list(
            String diagramId, String userId, Pageable pageable) {
        DiagramEntity diagram = requireReadableDiagram(diagramId, userId);
        return history.findByDiagramIdOrderByCreatedAtDesc(diagram.getId(), pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public DiagramHistoryAvailabilityResponse availability(String diagramId, String userId) {
        DiagramEntity diagram = requireReadableDiagram(diagramId, userId);
        boolean canUndo = latest(diagramId, ACTIVE, "APPLY") != null;
        boolean canRedo = history.findFirstByDiagramIdAndStateAndActionOrderByNewVersionDesc(
                diagramId, UNDONE, "APPLY").isPresent();
        return new DiagramHistoryAvailabilityResponse(diagramId, diagram.getVersion(), canUndo, canRedo);
    }

    @Transactional
    public DiagramHistoryResponse undo(String diagramId, String userId) {
        access.requireDiagramEditor(diagramId, userId);
        DiagramEntity diagram = lock(diagramId);
        DiagramHistoryEntity target = latest(diagramId, ACTIVE, "APPLY");
        if (target == null) throw new IllegalArgumentException("No hay cambios reversibles para este diagrama.");
        requireCurrentVersion(diagram, target.getNewVersion());
        String beforeCanonical = diagram.getCanonicalModelJson();
        String beforeView = diagram.getViewStateJson();
        applySnapshot(diagram, target.getBeforeCanonicalModelJson(), target.getBeforeViewStateJson());
        target.setState(UNDONE);
        history.save(target);
        DiagramHistoryEntity event = saveTransition(diagram, userId, target, "UNDO", beforeCanonical, beforeView);
        broadcastAfterCommit(diagram, userId, "HISTORY_UNDO");
        return toResponse(event);
    }

    @Transactional
    public DiagramHistoryResponse redo(String diagramId, String userId) {
        access.requireDiagramEditor(diagramId, userId);
        DiagramEntity diagram = lock(diagramId);
        DiagramHistoryEntity target = history.findFirstByDiagramIdAndStateAndActionOrderByNewVersionDesc(
                diagramId, UNDONE, "APPLY").orElseThrow(() ->
                new IllegalArgumentException("No hay cambios para rehacer en este diagrama."));
        DiagramHistoryEntity undo = history.findFirstByDiagramIdAndRelatedHistoryIdAndActionOrderByNewVersionDesc(
                diagramId, target.getId(), "UNDO").orElseThrow(() ->
                new IllegalArgumentException("No existe una reversión compatible para rehacer."));
        requireCurrentVersion(diagram, undo.getNewVersion());
        String beforeCanonical = diagram.getCanonicalModelJson();
        String beforeView = diagram.getViewStateJson();
        applySnapshot(diagram, target.getAfterCanonicalModelJson(), target.getAfterViewStateJson());
        target.setState(ACTIVE);
        history.save(target);
        DiagramHistoryEntity event = saveTransition(diagram, userId, target, "REDO", beforeCanonical, beforeView);
        broadcastAfterCommit(diagram, userId, "HISTORY_REDO");
        return toResponse(event);
    }

    private DiagramHistoryEntity saveTransition(DiagramEntity diagram, String userId,
                                                DiagramHistoryEntity target, String action,
                                                String beforeCanonical, String beforeView) {
        DiagramHistoryEntity event = new DiagramHistoryEntity();
        event.setId(UUID.randomUUID().toString());
        event.setProjectId(diagram.getProjectId());
        event.setDiagramId(diagram.getId());
        event.setUserId(userId);
        event.setOperationId(action.toLowerCase() + "-" + UUID.randomUUID());
        event.setOperationType(target.getOperationType());
        event.setOrigin(target.getOrigin());
        event.setAction(action);
        event.setState(ACTIVE);
        event.setPreviousVersion(diagram.getVersion() - 1);
        event.setNewVersion(diagram.getVersion());
        event.setRelatedHistoryId(target.getId());
        event.setBeforeCanonicalModelJson(beforeCanonical);
        event.setAfterCanonicalModelJson(diagram.getCanonicalModelJson());
        event.setBeforeViewStateJson(beforeView);
        event.setAfterViewStateJson(diagram.getViewStateJson());
        event.setCreatedAt(LocalDateTime.now());
        return history.save(event);
    }

    private DiagramHistoryResponse toResponse(DiagramHistoryEntity value) {
        DiagramHistoryDescriptionService.Description description = descriptions.describe(
                value.getBeforeCanonicalModelJson(), value.getAfterCanonicalModelJson(), value.getOperationType());
        return DiagramHistoryResponse.from(value, description.summary(), description.details());
    }

    private void applySnapshot(DiagramEntity diagram, String canonical, String viewState) {
        long nextVersion = diagram.getVersion() + 1;
        var restored = serializer.deserializeCanonical(canonical);
        restored.setVersion(nextVersion);
        diagram.setCanonicalModelJson(serializer.serializeCanonical(restored));
        diagram.setViewStateJson(viewState);
        diagram.setVersion(nextVersion);
        diagram.setUpdatedAt(LocalDateTime.now());
        diagrams.save(diagram);
    }

    private DiagramHistoryEntity latest(String diagramId, String state, String action) {
        return history.findFirstByDiagramIdAndStateAndActionOrderByNewVersionDesc(diagramId, state, action).orElse(null);
    }

    private DiagramEntity requireReadableDiagram(String diagramId, String userId) {
        DiagramEntity diagram = lockWithoutTransaction(diagramId);
        access.requireRead(diagram.getProjectId(), userId);
        return diagram;
    }

    private DiagramEntity lockWithoutTransaction(String diagramId) {
        return diagrams.findById(diagramId).orElseThrow(() -> new DiagramNotFoundException("Diagrama no encontrado."));
    }

    private DiagramEntity lock(String diagramId) {
        return diagrams.findByIdForUpdate(diagramId).orElseThrow(() -> new DiagramNotFoundException("Diagrama no encontrado."));
    }

    private void requireCurrentVersion(DiagramEntity diagram, long expected) {
        if (diagram.getVersion() != expected) {
            throw new VersionConflictException("El diagrama cambió; no se puede revertir sin sobrescribir cambios.");
        }
    }

    private void broadcastAfterCommit(DiagramEntity diagram, String userId, String reason) {
        Runnable action = () -> broadcaster.broadcastSnapshot(diagram.getId(), userId, reason,
                serializer.deserializeCanonical(diagram.getCanonicalModelJson()),
                serializer.deserializeViewState(diagram.getViewStateJson()));
        if (!TransactionSynchronizationManager.isSynchronizationActive()) action.run();
        else TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }
}
