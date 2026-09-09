package com.sw1.umltool.features.collaboration.websocket;

import com.sw1.umltool.features.diagram.dto.OperationExecutionResponse;
import com.sw1.umltool.features.diagram.operation.DiagramOperation;
import com.sw1.umltool.features.diagram.operation.DiagramOperationPayloadMapper;
import com.sw1.umltool.features.diagram.operation.OperationApplicationException;
import com.sw1.umltool.features.diagram.operation.VersionConflictException;
import com.sw1.umltool.features.diagram.repository.DiagramRepository;
import com.sw1.umltool.features.diagram.service.PersistentDiagramOperationService;
import com.sw1.umltool.features.project.model.ProjectMemberRole;
import com.sw1.umltool.features.project.service.ProjectAccessService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class CollaborationWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(CollaborationWebSocketHandler.class);
    private final ObjectMapper objectMapper;
    private final DiagramRepository diagrams;
    private final ProjectAccessService access;
    private final PersistentDiagramOperationService operations;
    private final DiagramOperationPayloadMapper payloadMapper;
    private final Map<String, Set<WebSocketSession>> sessionsByDiagram = new ConcurrentHashMap<>();
    private final Map<String, OperationExecutionResponse> appliedOperations = new ConcurrentHashMap<>();

    public CollaborationWebSocketHandler(ObjectMapper objectMapper, DiagramRepository diagrams,
                                         ProjectAccessService access,
                                         PersistentDiagramOperationService operations,
                                         DiagramOperationPayloadMapper payloadMapper) {
        this.objectMapper = objectMapper;
        this.diagrams = diagrams;
        this.access = access;
        this.operations = operations;
        this.payloadMapper = payloadMapper;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode root = objectMapper.readTree(message.getPayload());
        String type = text(root, "type");
        if ("JOIN_DIAGRAM".equals(type)) join(session, text(root, "diagramId"), root.path("knownVersion").asLong(0));
        else if ("LEAVE_DIAGRAM".equals(type)) leave(session);
        else if ("APPLY_OPERATION".equals(type)) apply(session, root);
        else send(session, Map.of("type", "ERROR", "message", "Mensaje WebSocket no soportado."));
    }

    private void join(WebSocketSession session, String diagramId, long knownVersion) throws IOException {
        String userId = userId(session);
        var diagram = diagrams.findById(diagramId).orElseThrow(() -> new IllegalArgumentException("Diagrama no encontrado."));
        access.requireRead(diagram.getProjectId(), userId);
        leave(session);
        session.getAttributes().put("diagramId", diagramId);
        sessionsByDiagram.computeIfAbsent(diagramId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
        if (knownVersion != diagram.getVersion()) send(session, Map.of("type", "RESYNC_REQUIRED", "diagramId", diagramId, "serverVersion", diagram.getVersion()));
        send(session, Map.of("type", "JOINED", "diagramId", diagramId, "version", diagram.getVersion()));
        broadcastPresence(diagramId);
    }

    private void leave(WebSocketSession session) throws IOException {
        Object diagramId = session.getAttributes().remove("diagramId");
        if (!(diagramId instanceof String id)) return;
        Set<WebSocketSession> sessions = sessionsByDiagram.get(id);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) sessionsByDiagram.remove(id);
            else broadcastPresence(id);
        }
    }

    private void apply(WebSocketSession session, JsonNode root) throws IOException {
        String diagramId = text(root, "diagramId");
        String userId = userId(session);
        log.debug("[WS RECEIVE] APPLY_OPERATION diagramId={} operationId={} baseVersion={} userId={} sessions={}", diagramId, text(root, "operationId"), root.path("baseVersion").asLong(), userId, sessionsByDiagram.getOrDefault(diagramId, Set.of()).size());
        var entity = diagrams.findById(diagramId).orElseThrow(() -> new IllegalArgumentException("Diagrama no encontrado."));
        ProjectMemberRole role = access.resolveRole(entity.getProjectId(), userId);
        if (role != ProjectMemberRole.OWNER && role != ProjectMemberRole.EDITOR) {
            send(session, Map.of("type", "OPERATION_REJECTED", "operationId", text(root, "operationId"), "reason", "FORBIDDEN", "serverVersion", entity.getVersion()));
            return;
        }
        String operationId = text(root, "operationId");
        OperationExecutionResponse duplicate = appliedOperations.get(operationId);
        if (duplicate != null) { sendApplied(session, duplicate, userId); return; }
        DiagramOperation operation = objectMapper.convertValue(root.path("operation"), DiagramOperation.class);
        operation.setOperationId(operationId);
        operation.setDiagramId(diagramId);
        operation.setUserId(userId);
        operation.setBaseVersion(root.path("baseVersion").asLong(operation.getBaseVersion()));
        operation = payloadMapper.map(operation);
        try {
            OperationExecutionResponse response = OperationExecutionResponse.from(operations.execute(diagramId, operation, userId));
            appliedOperations.put(operationId, response);
            log.debug("[WS ACCEPT] operationId={} previousVersion={} resultingVersion={}", operationId, response.getPreviousVersion(), response.getNewVersion());
            send(session, Map.of("type", "OPERATION_ACK", "operationId", operationId, "diagramId", diagramId, "version", response.getNewVersion()));
            sendAppliedToDiagram(diagramId, response, operation, userId);
        } catch (VersionConflictException conflict) {
            send(session, Map.of("type", "OPERATION_REJECTED", "operationId", operationId, "reason", "VERSION_CONFLICT", "serverVersion", entity.getVersion()));
        } catch (IllegalArgumentException | IllegalStateException | OperationApplicationException error) {
            send(session, Map.of("type", "OPERATION_REJECTED", "operationId", operationId, "reason", "INVALID_OPERATION", "message", error.getMessage()));
        }
    }

    private void sendAppliedToDiagram(String diagramId, OperationExecutionResponse response, DiagramOperation operation, String actor) throws IOException {
        Map<String, Object> message = Map.of("type", "OPERATION_APPLIED", "operationId", response.getOperationId(), "diagramId", diagramId, "actor", Map.of("userId", actor), "version", response.getNewVersion(), "operation", operation, "result", response);
        Set<WebSocketSession> sessions = sessionsByDiagram.getOrDefault(diagramId, Set.of());
        log.debug("[WS BROADCAST] operationId={} destinations={}", response.getOperationId(), sessions.size());
        for (WebSocketSession session : sessions) send(session, message);
    }

    private void sendApplied(WebSocketSession session, OperationExecutionResponse response, String actor) throws IOException { send(session, Map.of("type", "OPERATION_APPLIED", "operationId", response.getOperationId(), "diagramId", response.getDiagramId(), "actor", Map.of("userId", actor), "version", response.getNewVersion(), "result", response)); }

    private void broadcastPresence(String diagramId) throws IOException {
        Set<WebSocketSession> sessions = sessionsByDiagram.getOrDefault(diagramId, Set.of());
        Map<String, Map<String, Object>> unique = new ConcurrentHashMap<>();
        for (WebSocketSession session : sessions) unique.put(userId(session), Map.of("userId", userId(session)));
        sendToSessions(sessions, Map.of("type", "PRESENCE", "diagramId", diagramId, "users", new ArrayList<>(unique.values())));
    }

    private void sendToSessions(Set<WebSocketSession> sessions, Object value) throws IOException { for (WebSocketSession session : sessions) send(session, value); }
    private void send(WebSocketSession session, Object value) throws IOException { if (session.isOpen()) session.sendMessage(new TextMessage(objectMapper.writeValueAsString(value))); }
    private String userId(WebSocketSession session) { return String.valueOf(session.getAttributes().get("userId")); }
    private String text(JsonNode node, String field) { String value = node.path(field).asText(null); if (value == null || value.isBlank()) throw new OperationApplicationException(field + " is required"); return value; }

    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception { leave(session); }
    @Override public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception { if (session.isOpen()) session.close(CloseStatus.SERVER_ERROR); }
}
