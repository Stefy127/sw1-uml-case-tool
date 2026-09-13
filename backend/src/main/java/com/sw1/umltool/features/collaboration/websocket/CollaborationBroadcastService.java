package com.sw1.umltool.features.collaboration.websocket;

import com.sw1.umltool.features.auth.repository.UserRepository;
import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.persistence.DiagramEntity;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;
import com.sw1.umltool.features.diagram.repository.DiagramRepository;
import com.sw1.umltool.features.project.model.ProjectMemberRole;
import com.sw1.umltool.features.project.service.ProjectAccessService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CollaborationBroadcastService {
    private static final Logger log = LoggerFactory.getLogger(CollaborationBroadcastService.class);
    private final ObjectMapper mapper;
    private final DiagramRepository diagrams;
    private final ProjectAccessService access;
    private final UserRepository users;
    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> safeSessions = new ConcurrentHashMap<>();
    private static final int SEND_TIME_LIMIT_MILLIS = 10_000;
    private static final int BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;

    public CollaborationBroadcastService(ObjectMapper mapper, DiagramRepository diagrams, ProjectAccessService access, UserRepository users) {
        this.mapper = mapper; this.diagrams = diagrams; this.access = access; this.users = users;
    }
    public void add(String diagramId, WebSocketSession session) {
        WebSocketSession safe = safeSessions.computeIfAbsent(session.getId(), ignored ->
                new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MILLIS, BUFFER_SIZE_LIMIT_BYTES));
        sessions.computeIfAbsent(diagramId, ignored -> ConcurrentHashMap.newKeySet()).add(safe);
    }

    public void remove(String diagramId, WebSocketSession session) {
        Set<WebSocketSession> current = sessions.get(diagramId);
        if (current != null) {
            current.removeIf(candidate -> candidate.getId().equals(session.getId()));
            if (current.isEmpty()) sessions.remove(diagramId, current);
        }
        safeSessions.remove(session.getId());
    }

    public void removeEverywhere(WebSocketSession session) {
        sessions.forEach((diagramId, current) -> remove(diagramId, session));
    }

    public Set<WebSocketSession> sessions(String diagramId) { return sessions.getOrDefault(diagramId, Set.of()); }
    public boolean send(WebSocketSession session, Object value) {
        if (session == null) return false;
        WebSocketSession safe = safeSessions.getOrDefault(session.getId(), session);
        if (!safe.isOpen()) {
            removeEverywhere(safe);
            return false;
        }
        try {
            safe.sendMessage(new TextMessage(mapper.writeValueAsString(value)));
            return true;
        } catch (Exception error) {
            String diagramId = String.valueOf(safe.getAttributes().get("diagramId"));
            logSendFailure(safe, diagramId, value, error);
            removeEverywhere(safe);
            return false;
        }
    }

    public void broadcastSnapshot(String diagramId, String actor, String reason, UmlDiagram diagram, DiagramViewState viewState) {
        Map<String, Object> message = Map.of("type", "DIAGRAM_SNAPSHOT_UPDATED", "diagramId", diagramId, "version", diagram.getVersion(), "actor", Map.of("userId", actor), "reason", reason, "canonicalModel", diagram, "viewState", viewState);
        for (WebSocketSession session : sessions(diagramId)) send(session, message);
    }
    public void broadcastPresence(String diagramId) {
        DiagramEntity diagram = diagrams.findById(diagramId).orElse(null);
        Map<String, Map<String, Object>> unique = new ConcurrentHashMap<>();
        for (WebSocketSession session : sessions(diagramId)) {
            String userId = String.valueOf(session.getAttributes().get("userId"));
            ProjectMemberRole role = diagram == null ? null : access.resolveRole(diagram.getProjectId(), userId);
            var user = users.findById(userId).orElse(null);
            unique.put(userId, Map.of("userId", userId, "firstName", user == null ? "" : user.getFirstName(), "lastName", user == null ? "" : user.getLastName(), "role", role == null ? "VIEWER" : role.name()));
        }
        Map<String, Object> message = Map.of("type", "PRESENCE", "diagramId", diagramId, "users", unique.values());
        for (WebSocketSession session : sessions(diagramId)) send(session, message);
    }

    private void logSendFailure(WebSocketSession session, String diagramId, Object message, Exception error) {
        String type = message instanceof Map<?, ?> map ? String.valueOf(map.get("type")) : message.getClass().getSimpleName();
        log.warn(
                "[WS SEND FAILED] sessionId={} diagramId={} messageType={} error={}",
                session.getId(), diagramId, type, error.toString(), error);
    }
}
