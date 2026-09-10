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
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CollaborationBroadcastService {
    private final ObjectMapper mapper;
    private final DiagramRepository diagrams;
    private final ProjectAccessService access;
    private final UserRepository users;
    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public CollaborationBroadcastService(ObjectMapper mapper, DiagramRepository diagrams, ProjectAccessService access, UserRepository users) {
        this.mapper = mapper; this.diagrams = diagrams; this.access = access; this.users = users;
    }
    public void add(String diagramId, WebSocketSession session) { sessions.computeIfAbsent(diagramId, ignored -> ConcurrentHashMap.newKeySet()).add(session); }
    public void remove(String diagramId, WebSocketSession session) { Set<WebSocketSession> current = sessions.get(diagramId); if (current != null) { current.remove(session); if (current.isEmpty()) sessions.remove(diagramId); } }
    public Set<WebSocketSession> sessions(String diagramId) { return sessions.getOrDefault(diagramId, Set.of()); }
    public void broadcastSnapshot(String diagramId, String actor, String reason, UmlDiagram diagram, DiagramViewState viewState) throws IOException {
        Map<String, Object> message = Map.of("type", "DIAGRAM_SNAPSHOT_UPDATED", "diagramId", diagramId, "version", diagram.getVersion(), "actor", Map.of("userId", actor), "reason", reason, "canonicalModel", diagram, "viewState", viewState);
        for (WebSocketSession session : sessions(diagramId)) if (session.isOpen()) session.sendMessage(new TextMessage(mapper.writeValueAsString(message)));
    }
    public void broadcastPresence(String diagramId) throws IOException {
        DiagramEntity diagram = diagrams.findById(diagramId).orElse(null);
        Map<String, Map<String, Object>> unique = new ConcurrentHashMap<>();
        for (WebSocketSession session : sessions(diagramId)) {
            String userId = String.valueOf(session.getAttributes().get("userId"));
            ProjectMemberRole role = diagram == null ? null : access.resolveRole(diagram.getProjectId(), userId);
            var user = users.findById(userId).orElse(null);
            unique.put(userId, Map.of("userId", userId, "firstName", user == null ? "" : user.getFirstName(), "lastName", user == null ? "" : user.getLastName(), "role", role == null ? "VIEWER" : role.name()));
        }
        Map<String, Object> message = Map.of("type", "PRESENCE", "diagramId", diagramId, "users", unique.values());
        for (WebSocketSession session : sessions(diagramId)) if (session.isOpen()) session.sendMessage(new TextMessage(mapper.writeValueAsString(message)));
    }
}
