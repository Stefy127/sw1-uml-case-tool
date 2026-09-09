package com.sw1.umltool.features.project.service;

import com.sw1.umltool.features.project.model.ProjectMemberEntity;
import com.sw1.umltool.features.project.model.ProjectMemberRole;
import com.sw1.umltool.features.project.repository.ProjectMemberRepository;
import org.springframework.stereotype.Service;
import com.sw1.umltool.features.diagram.model.persistence.DiagramEntity;
import com.sw1.umltool.features.diagram.repository.DiagramRepository;

@Service
public class ProjectAccessService {
    private final ProjectMemberRepository members;
    private final DiagramRepository diagrams;
    public ProjectAccessService(ProjectMemberRepository members, DiagramRepository diagrams) { this.members = members; this.diagrams = diagrams; }
    public ProjectMemberEntity requireMember(String projectId, String userId) { return members.findByProjectIdAndUserId(projectId, userId).orElseThrow(() -> new IllegalArgumentException("No tienes acceso a este proyecto.")); }
    public ProjectMemberEntity requireEditor(String projectId, String userId) { ProjectMemberEntity member=requireMember(projectId,userId); if (member.getRole()==ProjectMemberRole.VIEWER) throw new IllegalStateException("Tu rol no permite modificar este proyecto."); return member; }
    public ProjectMemberEntity requireOwner(String projectId, String userId) { ProjectMemberEntity member=requireMember(projectId,userId); if (member.getRole()!=ProjectMemberRole.OWNER) throw new IllegalStateException("Solo el propietario puede administrar miembros."); return member; }
    public ProjectMemberEntity requireDiagramMember(String diagramId, String userId) { DiagramEntity diagram=diagrams.findById(diagramId).orElseThrow(() -> new IllegalArgumentException("Diagrama no encontrado.")); return requireMember(diagram.getProjectId(),userId); }
    public ProjectMemberEntity requireDiagramEditor(String diagramId, String userId) { DiagramEntity diagram=diagrams.findById(diagramId).orElseThrow(() -> new IllegalArgumentException("Diagrama no encontrado.")); return requireEditor(diagram.getProjectId(),userId); }
}
