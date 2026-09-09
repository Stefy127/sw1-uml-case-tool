package com.sw1.umltool.features.project.service;

import com.sw1.umltool.features.project.model.ProjectMemberEntity;
import com.sw1.umltool.features.project.model.ProjectMemberRole;
import com.sw1.umltool.features.project.model.ProjectShareMode;
import com.sw1.umltool.features.project.repository.ProjectMemberRepository;
import com.sw1.umltool.features.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import com.sw1.umltool.features.diagram.model.persistence.DiagramEntity;
import com.sw1.umltool.features.diagram.repository.DiagramRepository;

@Service
public class ProjectAccessService {
    private final ProjectMemberRepository members;
    private final DiagramRepository diagrams;
    private final ProjectRepository projects;
    public ProjectAccessService(ProjectMemberRepository members, DiagramRepository diagrams) { this(members, diagrams, null); }
    @org.springframework.beans.factory.annotation.Autowired
    public ProjectAccessService(ProjectMemberRepository members, DiagramRepository diagrams, ProjectRepository projects) { this.members = members; this.diagrams = diagrams; this.projects = projects; }
    public ProjectMemberEntity requireMember(String projectId, String userId) { return members.findByProjectIdAndUserId(projectId, userId).orElseThrow(() -> new IllegalArgumentException("No tienes acceso a este proyecto.")); }
    public ProjectMemberEntity requireEditor(String projectId, String userId) { ProjectMemberEntity member=members.findByProjectIdAndUserId(projectId,userId).orElse(null); if (member != null) { if (member.getRole()==ProjectMemberRole.VIEWER) throw new IllegalStateException("Tu rol no permite modificar este proyecto."); return member; } if (linkRole(projectId) == ProjectMemberRole.EDITOR) return null; throw new IllegalArgumentException("No tienes acceso de edición a este proyecto."); }
    public ProjectMemberEntity requireOwner(String projectId, String userId) { ProjectMemberEntity member=requireMember(projectId,userId); if (member.getRole()!=ProjectMemberRole.OWNER) throw new IllegalStateException("Solo el propietario puede administrar miembros."); return member; }
    public ProjectMemberEntity requireDiagramMember(String diagramId, String userId) { DiagramEntity diagram=diagrams.findById(diagramId).orElseThrow(() -> new IllegalArgumentException("Diagrama no encontrado.")); requireRead(diagram.getProjectId(), userId); return members.findByProjectIdAndUserId(diagram.getProjectId(),userId).orElse(null); }
    public ProjectMemberEntity requireDiagramEditor(String diagramId, String userId) { DiagramEntity diagram=diagrams.findById(diagramId).orElseThrow(() -> new IllegalArgumentException("Diagrama no encontrado.")); requireEdit(diagram.getProjectId(), userId); return members.findByProjectIdAndUserId(diagram.getProjectId(),userId).orElse(null); }
    public ProjectMemberRole resolveRole(String projectId, String userId) { ProjectMemberEntity member=members.findByProjectIdAndUserId(projectId,userId).orElse(null); return member != null ? member.getRole() : linkRole(projectId); }
    public void requireRead(String projectId, String userId) { if (resolveRole(projectId,userId) == null) throw new IllegalArgumentException("No tienes acceso a este proyecto."); }
    public void requireEdit(String projectId, String userId) { ProjectMemberRole role=resolveRole(projectId,userId); if (role != ProjectMemberRole.OWNER && role != ProjectMemberRole.EDITOR) throw new IllegalStateException("Tu rol no permite modificar este proyecto."); }
    private ProjectMemberRole linkRole(String projectId) { return projects == null ? null : projects.findById(projectId).filter(project -> project.getShareMode() != null && project.getShareMode() != ProjectShareMode.RESTRICTED && project.getShareToken() != null).map(project -> project.getShareMode() == ProjectShareMode.LINK_EDITOR ? ProjectMemberRole.EDITOR : ProjectMemberRole.VIEWER).orElse(null); }
}
