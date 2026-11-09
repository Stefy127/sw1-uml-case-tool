package com.sw1.umltool.features.ai.changeproposal.service;

import com.sw1.umltool.features.ai.changeproposal.dto.AiChangeProposalApplyOperation;
import com.sw1.umltool.features.ai.changeproposal.dto.AiChangeProposalApplyRequest;
import com.sw1.umltool.features.ai.changeproposal.dto.AiChangeProposalApplyResponse;
import com.sw1.umltool.features.collaboration.websocket.CollaborationBroadcastService;
import com.sw1.umltool.features.ai.changeproposal.model.persistence.AiChangeProposalApplicationEntity;
import com.sw1.umltool.features.ai.changeproposal.repository.AiChangeProposalApplicationRepository;
import com.sw1.umltool.features.diagram.model.canonical.UmlAttribute;
import com.sw1.umltool.features.diagram.model.canonical.enums.Visibility;
import com.sw1.umltool.features.diagram.model.persistence.DiagramEntity;
import com.sw1.umltool.features.diagram.operation.DiagramOperation;
import com.sw1.umltool.features.diagram.operation.DiagramOperationType;
import com.sw1.umltool.features.diagram.operation.OperationExecutionResult;
import com.sw1.umltool.features.diagram.operation.payload.AddAttributePayload;
import com.sw1.umltool.features.diagram.operation.payload.UpdateAttributePayload;
import com.sw1.umltool.features.diagram.repository.DiagramRepository;
import com.sw1.umltool.features.diagram.service.DiagramNotFoundException;
import com.sw1.umltool.features.diagram.service.PersistentDiagramOperationService;
import com.sw1.umltool.features.diagram.service.DiagramStateSerializer;
import com.sw1.umltool.features.project.exception.ProjectForbiddenException;
import com.sw1.umltool.features.project.model.ProjectMemberRole;
import com.sw1.umltool.features.project.service.ProjectAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AiChangeProposalApplyService {
    private static final int MAX_PROPOSALS = 3;
    private static final Set<String> OPERATIONS = Set.of("ADD_ATTRIBUTE", "RENAME_ATTRIBUTE", "CHANGE_ATTRIBUTE_TYPE");
    private static final Set<String> TYPES = Set.of("string", "integer", "int", "long", "boolean", "double", "float",
            "date", "localdate", "datetime", "localdatetime", "time", "localtime", "instant", "offsetdatetime",
            "bigdecimal", "uuid");

    private final DiagramRepository diagrams;
    private final ProjectAccessService access;
    private final PersistentDiagramOperationService operations;
    private final CollaborationBroadcastService broadcaster;
    private final AiChangeProposalApplicationRepository applications;
    private final DiagramStateSerializer serializer;
    private final ConcurrentHashMap<String, CachedApplication> completed = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> requestLocks = new ConcurrentHashMap<>();

    public AiChangeProposalApplyService(DiagramRepository diagrams, ProjectAccessService access,
                                        PersistentDiagramOperationService operations,
                                        CollaborationBroadcastService broadcaster,
                                        AiChangeProposalApplicationRepository applications,
                                        DiagramStateSerializer serializer) {
        this.diagrams = diagrams;
        this.access = access;
        this.operations = operations;
        this.broadcaster = broadcaster;
        this.applications = applications;
        this.serializer = serializer;
    }

    @Transactional
    public AiChangeProposalApplyResponse apply(String projectId, String userId, AiChangeProposalApplyRequest request) {
        validateRequest(request);
        String diagramId = request.diagramId().trim();
        String requestKey = projectId + "|" + userId + "|" + request.requestId().trim();
        String fingerprint = fingerprint(request);
        CachedApplication cached = completed.get(requestKey);
        if (cached != null) return cached.responseFor(fingerprint);
        Object lock = requestLocks.computeIfAbsent(requestKey, ignored -> new Object());
        synchronized (lock) {
            cached = completed.get(requestKey);
            if (cached != null) return cached.responseFor(fingerprint);
            DiagramEntity lockedDiagram = diagrams.findByIdForUpdate(diagramId).orElse(null);
            if (lockedDiagram == null || !projectId.equals(lockedDiagram.getProjectId())) {
                throw new DiagramNotFoundException("El diagrama no existe o no pertenece al proyecto.");
            }
            AiChangeProposalApplicationEntity previous = applications
                    .findByProjectIdAndUserIdAndDiagramIdAndRequestId(projectId, userId, diagramId, request.requestId().trim())
                    .orElse(null);
            if (previous != null) {
                AiChangeProposalApplyResponse response = responseFor(previous, fingerprint);
                completed.put(requestKey, new CachedApplication(previous.getFingerprint(), response));
                return response;
            }
            ProjectMemberRole role = access.resolveRole(projectId, userId);
            if (role != ProjectMemberRole.OWNER && role != ProjectMemberRole.EDITOR) {
                throw new ProjectForbiddenException();
            }
            List<DiagramOperation> internalOperations = toOperations(request);
            OperationExecutionResult result = operations.executeBatch(diagramId, internalOperations, userId, "AI");
            AiChangeProposalApplyResponse response = new AiChangeProposalApplyResponse(
                    request.requestId().trim(), projectId, diagramId, result.getPreviousVersion(),
                    result.getNewVersion(), internalOperations.size(), result.getDiagram(), result.getViewState());
            applications.save(AiChangeProposalApplicationEntity.builder()
                    .id(UUID.randomUUID().toString())
                    .requestId(request.requestId().trim())
                    .projectId(projectId)
                    .userId(userId)
                    .diagramId(diagramId)
                    .fingerprint(fingerprint)
                    .previousVersion(result.getPreviousVersion())
                    .newVersion(result.getNewVersion())
                    .appliedOperations(internalOperations.size())
                    .canonicalModelJson(serializer.serializeCanonical(result.getDiagram()))
                    .viewStateJson(serializer.serializeViewState(result.getViewState()))
                    .createdAt(java.time.LocalDateTime.now())
                    .build());
            afterCommit(() -> {
                completed.put(requestKey, new CachedApplication(fingerprint, response));
                broadcaster.broadcastSnapshot(diagramId, userId, "AI_CHANGE_PROPOSALS_APPLIED", result.getDiagram(), result.getViewState());
            });
            return response;
        }
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private List<DiagramOperation> toOperations(AiChangeProposalApplyRequest request) {
        List<DiagramOperation> result = new ArrayList<>();
        Set<String> conflictKeys = new HashSet<>();
        for (AiChangeProposalApplyOperation proposed : request.operations()) {
            String type = normalizeOperation(proposed.operation());
            String classId = required(proposed.targetElementId(), "La clase objetivo es obligatoria.");
            String key;
            DiagramOperation operation;
            switch (type) {
                case "ADD_ATTRIBUTE" -> {
                    if (proposed.targetAttributeId() != null && !proposed.targetAttributeId().isBlank())
                        throw new IllegalArgumentException("ADD_ATTRIBUTE no puede tener un atributo objetivo.");
                    String name = validName(proposed.newAttributeName());
                    String attributeType = validType(proposed.attributeType());
                    key = type + "|" + classId + "|" + normalize(name);
                    if (!conflictKeys.add(key)) throw new IllegalArgumentException("La solicitud contiene operaciones en conflicto.");
                    operation = DiagramOperation.builder().operationId(UUID.randomUUID().toString())
                            .diagramId(request.diagramId().trim()).baseVersion(request.baseVersion()).type(DiagramOperationType.ADD_ATTRIBUTE)
                            .payload(new AddAttributePayload(classId, UmlAttribute.builder().id(UUID.randomUUID().toString())
                                    .name(name).type(attributeType).visibility(Visibility.PRIVATE).build())).build();
                }
                case "RENAME_ATTRIBUTE" -> {
                    String attributeId = required(proposed.targetAttributeId(), "El atributo objetivo es obligatorio.");
                    String name = validName(proposed.newAttributeName());
                    key = type + "|" + attributeId;
                    if (!conflictKeys.add(key)) throw new IllegalArgumentException("La solicitud contiene operaciones en conflicto.");
                    operation = DiagramOperation.builder().operationId(UUID.randomUUID().toString())
                            .diagramId(request.diagramId().trim()).baseVersion(request.baseVersion()).type(DiagramOperationType.UPDATE_ATTRIBUTE)
                            .payload(UpdateAttributePayload.builder().classId(classId).attributeId(attributeId).name(name).build()).build();
                }
                case "CHANGE_ATTRIBUTE_TYPE" -> {
                    String attributeId = required(proposed.targetAttributeId(), "El atributo objetivo es obligatorio.");
                    String attributeType = validType(proposed.attributeType());
                    key = type + "|" + attributeId;
                    if (!conflictKeys.add(key)) throw new IllegalArgumentException("La solicitud contiene operaciones en conflicto.");
                    operation = DiagramOperation.builder().operationId(UUID.randomUUID().toString())
                            .diagramId(request.diagramId().trim()).baseVersion(request.baseVersion()).type(DiagramOperationType.UPDATE_ATTRIBUTE)
                            .payload(UpdateAttributePayload.builder().classId(classId).attributeId(attributeId).type(attributeType).build()).build();
                }
                default -> throw new IllegalArgumentException("La operación de propuesta no está permitida.");
            }
            result.add(operation);
        }
        return result;
    }

    private void validateRequest(AiChangeProposalApplyRequest request) {
        if (request == null || request.diagramId() == null || request.diagramId().isBlank()
                || request.requestId() == null || request.requestId().isBlank()) {
            throw new IllegalArgumentException("diagramId y requestId son obligatorios.");
        }
        if (!request.confirmed()) throw new IllegalArgumentException("La aplicación requiere confirmación explícita.");
        if (request.operations() == null || request.operations().isEmpty() || request.operations().size() > MAX_PROPOSALS) {
            throw new IllegalArgumentException("La solicitud debe contener entre 1 y 3 propuestas.");
        }
    }

    private AiChangeProposalApplyResponse responseFor(AiChangeProposalApplicationEntity entity, String fingerprint) {
        if (!entity.getFingerprint().equals(fingerprint)) {
            throw new IllegalArgumentException("El requestId ya fue utilizado con un contenido diferente.");
        }
        return new AiChangeProposalApplyResponse(entity.getRequestId(), entity.getProjectId(), entity.getDiagramId(),
                entity.getPreviousVersion(), entity.getNewVersion(), entity.getAppliedOperations(),
                serializer.deserializeCanonical(entity.getCanonicalModelJson()),
                serializer.deserializeViewState(entity.getViewStateJson()));
    }

    private String fingerprint(AiChangeProposalApplyRequest request) {
        String value = request.diagramId().trim() + "|" + request.baseVersion() + "|" + request.confirmed() + "|"
                + request.operations().stream().map(operation -> String.join("|",
                        safe(operation.proposalId()), safe(operation.operation()), safe(operation.targetElementId()),
                        safe(operation.targetAttributeId()), safe(operation.newAttributeName()), safe(operation.attributeType())))
                .collect(java.util.stream.Collectors.joining(";"));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("No se pudo calcular la huella de la solicitud.", exception);
        }
    }

    private String safe(String value) { return value == null ? "" : value.trim(); }

    private String normalizeOperation(String value) {
        String operation = required(value, "La operación es obligatoria.").toUpperCase(Locale.ROOT);
        if (!OPERATIONS.contains(operation)) throw new IllegalArgumentException("La operación de propuesta no está permitida.");
        return operation;
    }

    private String validName(String value) {
        String name = required(value, "El nombre del atributo es obligatorio.").trim();
        if (name.length() > 100 || !Character.isJavaIdentifierStart(name.charAt(0)))
            throw new IllegalArgumentException("El nombre del atributo no es válido.");
        for (int i = 1; i < name.length(); i++) if (!Character.isJavaIdentifierPart(name.charAt(i)))
            throw new IllegalArgumentException("El nombre del atributo no es válido.");
        return name;
    }

    private String validType(String value) {
        String type = required(value, "El tipo del atributo es obligatorio.").trim();
        if (!TYPES.contains(type.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("El tipo del atributo no está soportado.");
        return type;
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private record CachedApplication(String fingerprint, AiChangeProposalApplyResponse response) {
        private AiChangeProposalApplyResponse responseFor(String requestedFingerprint) {
            if (!fingerprint.equals(requestedFingerprint)) {
                throw new IllegalArgumentException("El requestId ya fue utilizado con un contenido diferente.");
            }
            return response;
        }
    }
}
