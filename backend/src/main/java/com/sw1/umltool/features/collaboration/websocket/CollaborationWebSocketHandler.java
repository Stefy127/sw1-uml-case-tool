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
import com.sw1.umltool.features.auth.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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
    private final UserRepository users;
    private final PersistentDiagramOperationService operations;
    private final DiagramOperationPayloadMapper payloadMapper;
    private final Map<String, OperationExecutionResponse> appliedOperations = new ConcurrentHashMap<>();
    private final Map<String, Object> diagramLocks = new ConcurrentHashMap<>();
    private final CollaborationBroadcastService broadcaster;

    public CollaborationWebSocketHandler(ObjectMapper objectMapper, DiagramRepository diagrams,
                                         ProjectAccessService access,
                                         UserRepository users,
                                         CollaborationBroadcastService broadcaster,
                                         PersistentDiagramOperationService operations,
                                         DiagramOperationPayloadMapper payloadMapper) {
        this.objectMapper = objectMapper;
        this.diagrams = diagrams;
        this.access = access;
        this.users = users;
        this.broadcaster = broadcaster;
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
        else if ("CURSOR_MOVE".equals(type)) cursorMove(session, root);
        else if ("SELECTION_CHANGE".equals(type)) selectionChange(session, root);
        else send(session, Map.of("type", "ERROR", "message", "Mensaje WebSocket no soportado."));
    }

    private void cursorMove(WebSocketSession session, JsonNode root) throws IOException {
        String diagramId = text(root, "diagramId");
        if (!joinedOn(session, diagramId) || !root.path("x").isNumber() || !root.path("y").isNumber()) return;
        double x = root.path("x").asDouble();
        double y = root.path("y").asDouble();
        if (!Double.isFinite(x) || !Double.isFinite(y)) return;
        sendToOthers(diagramId, session, Map.of("type", "REMOTE_CURSOR", "diagramId", diagramId, "user", presenceUser(session, diagramId), "x", x, "y", y));
    }

    private void selectionChange(WebSocketSession session, JsonNode root) throws IOException {
        String diagramId = text(root, "diagramId");
        if (!joinedOn(session, diagramId)) return;
        String elementType = root.path("elementType").isNull() ? null : root.path("elementType").asText(null);
        String elementId = root.path("elementId").isNull() ? null : root.path("elementId").asText(null);
        if (elementType != null && !elementType.equals("CLASS") && !elementType.equals("RELATION")) return;
        if (elementType == null) elementId = null;
        Map<String, Object> message = new HashMap<>();
        message.put("type", "REMOTE_SELECTION"); message.put("diagramId", diagramId); message.put("user", presenceUser(session, diagramId)); message.put("elementType", elementType); message.put("elementId", elementId);
        sendToOthers(diagramId, session, message);
    }

    private boolean joinedOn(WebSocketSession session, String diagramId) { return diagramId.equals(session.getAttributes().get("diagramId")); }
    private void sendToOthers(String diagramId, WebSocketSession sender, Object value) throws IOException {
        for (WebSocketSession target : broadcaster.sessions(diagramId)) if (!target.getId().equals(sender.getId())) send(target, value);
    }
    private Map<String, Object> presenceUser(WebSocketSession session, String diagramId) {
        String id = userId(session);
        var diagram = diagrams.findById(diagramId).orElse(null);
        ProjectMemberRole role = diagram == null ? null : access.resolveRole(diagram.getProjectId(), id);
        var user = users.findById(id).orElse(null);
        return Map.of("userId", id, "firstName", user == null ? "" : user.getFirstName(), "lastName", user == null ? "" : user.getLastName(), "role", role == null ? "VIEWER" : role.name());
    }

    private void join(WebSocketSession session, String diagramId, long knownVersion) throws IOException {
        String userId = userId(session);
        var diagram = diagrams.findById(diagramId).orElseThrow(() -> new IllegalArgumentException("Diagrama no encontrado."));
        access.requireRead(diagram.getProjectId(), userId);
        log.debug("[WS VERSION CHECK] diagramId={} clientBaseVersion={} serverVersion={}", diagramId, knownVersion, diagram.getVersion());
        leave(session);
        session.getAttributes().put("diagramId", diagramId);
        broadcaster.add(diagramId, session);
        if (knownVersion != diagram.getVersion()) send(session, Map.of("type", "RESYNC_REQUIRED", "diagramId", diagramId, "serverVersion", diagram.getVersion()));
        send(session, Map.of("type", "JOINED", "diagramId", diagramId, "version", diagram.getVersion()));
        broadcastPresence(diagramId);
    }

    private void leave(WebSocketSession session) throws IOException {
        Object diagramId = session.getAttributes().remove("diagramId");
        if (!(diagramId instanceof String id)) return;
        broadcaster.remove(id, session);
        broadcastPresence(id);
    }

    private void apply(WebSocketSession session, JsonNode root) throws IOException {
        String diagramId = text(root, "diagramId");
        String userId = userId(session);
        log.debug("[WS RECEIVE] APPLY_OPERATION diagramId={} operationId={} baseVersion={} userId={} sessions={}", diagramId, text(root, "operationId"), root.path("baseVersion").asLong(), userId, broadcaster.sessions(diagramId).size());
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
        synchronized (diagramLocks.computeIfAbsent(diagramId, ignored -> new Object())) {
          try {
            OperationExecutionResponse response = OperationExecutionResponse.from(operations.execute(diagramId, operation, userId));
            appliedOperations.put(operationId, response);
            log.debug("[WS ACCEPT] operationId={} previousVersion={} resultingVersion={}", operationId, response.getPreviousVersion(), response.getNewVersion());
            send(session, Map.of("type", "OPERATION_ACK", "operationId", operationId, "diagramId", diagramId, "version", response.getNewVersion()));
            sendAppliedToDiagram(diagramId, response, operation, userId);
          } catch (VersionConflictException conflict) {
            log.debug("[WS VERSION CONFLICT] operationId={} clientBaseVersion={} serverVersion={}", operationId, operation.getBaseVersion(), entity.getVersion());
            send(session, Map.of("type", "OPERATION_REJECTED", "operationId", operationId, "reason", "VERSION_CONFLICT", "serverVersion", entity.getVersion()));
          } catch (IllegalArgumentException | IllegalStateException | OperationApplicationException error) {
            send(session, Map.of("type", "OPERATION_REJECTED", "operationId", operationId, "reason", "INVALID_OPERATION", "message", error.getMessage()));
          }
        }
    }

    private void sendAppliedToDiagram(String diagramId, OperationExecutionResponse response, DiagramOperation operation, String actor) throws IOException {
        Map<String, Object> message = Map.of("type", "OPERATION_APPLIED", "operationId", response.getOperationId(), "diagramId", diagramId, "actor", Map.of("userId", actor), "version", response.getNewVersion(), "operation", operation, "result", response);
        Set<WebSocketSession> sessions = broadcaster.sessions(diagramId);
        log.debug("[WS BROADCAST] operationId={} destinations={}", response.getOperationId(), sessions.size());
        for (WebSocketSession session : sessions) send(session, message);
    }

    private void sendApplied(WebSocketSession session, OperationExecutionResponse response, String actor) throws IOException { send(session, Map.of("type", "OPERATION_APPLIED", "operationId", response.getOperationId(), "diagramId", response.getDiagramId(), "actor", Map.of("userId", actor), "version", response.getNewVersion(), "result", response)); }

    private void broadcastPresence(String diagramId) throws IOException {
        broadcaster.broadcastPresence(diagramId);
    }

    private void send(WebSocketSession session, Object value) throws IOException { if (session.isOpen()) session.sendMessage(new TextMessage(objectMapper.writeValueAsString(value))); }
    private String userId(WebSocketSession session) { return String.valueOf(session.getAttributes().get("userId")); }
    private String text(JsonNode node, String field) { String value = node.path(field).asText(null); if (value == null || value.isBlank()) throw new OperationApplicationException(field + " is required"); return value; }

    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception { leave(session); }
    @Override public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception { if (session.isOpen()) session.close(CloseStatus.SERVER_ERROR); }
}
