package com.sw1.umltool.features.project.service;

import com.sw1.umltool.features.project.model.ProjectEntity;
import com.sw1.umltool.features.project.model.ProjectMemberEntity;
import com.sw1.umltool.features.project.model.ProjectMemberRole;
import com.sw1.umltool.features.project.repository.ProjectMemberRepository;
import com.sw1.umltool.features.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.sw1.umltool.features.auth.model.UserEntity;
import com.sw1.umltool.features.auth.repository.UserRepository;
import com.sw1.umltool.features.project.dto.AddProjectMemberRequest;
import com.sw1.umltool.features.project.dto.ProjectMemberResponse;
import com.sw1.umltool.features.project.dto.ProjectMapper;
import com.sw1.umltool.features.project.dto.ProjectRoleResponse;
import com.sw1.umltool.features.project.dto.ShareLinkRequest;
import com.sw1.umltool.features.project.dto.ShareLinkResponse;
import com.sw1.umltool.features.project.dto.SharedProjectResponse;
import com.sw1.umltool.features.project.model.ProjectShareMode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final ProjectAccessService access;

    public ProjectService(ProjectRepository projectRepository, ProjectMemberRepository projectMemberRepository) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.userRepository = null;
        this.access = null;
    }

    @Autowired
    public ProjectService(ProjectRepository projectRepository, ProjectMemberRepository members, UserRepository users, ProjectAccessService access) { this.projectRepository=projectRepository; this.projectMemberRepository=members; this.userRepository=users; this.access=access; }

    @Transactional
    public ProjectEntity createProject(String name, String description, String ownerUserId) {
        if (isBlank(name)) throw new IllegalArgumentException("Project name is required");
        if (isBlank(ownerUserId)) throw new IllegalArgumentException("Owner user id is required");

        LocalDateTime now = LocalDateTime.now();
        ProjectEntity project = ProjectEntity.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .description(description)
                .ownerUserId(ownerUserId)
                .createdAt(now)
                .updatedAt(now)
                .build();
        ProjectEntity savedProject = projectRepository.save(project);

        projectMemberRepository.save(ProjectMemberEntity.builder()
                .id(UUID.randomUUID().toString())
                .projectId(savedProject.getId())
                .userId(ownerUserId)
                .role(ProjectMemberRole.OWNER)
                .createdAt(LocalDateTime.now())
                .build());

        return savedProject;
    }

    public List<ProjectEntity> findByOwnerUserId(String ownerUserId) {
        if (isBlank(ownerUserId)) throw new IllegalArgumentException("Owner user id is required");
        return projectRepository.findByOwnerUserId(ownerUserId);
    }

    public Optional<ProjectEntity> findById(String id) {
        if (isBlank(id)) throw new IllegalArgumentException("Project id is required");
        return projectRepository.findById(id);
    }
    public Optional<ProjectEntity> findById(String id, String userId) { access.requireRead(id, userId); return findById(id); }
    public List<ProjectMemberResponse> members(String projectId, String userId) { access.requireMember(projectId,userId); return projectMemberRepository.findByProjectId(projectId).stream().map(m -> userRepository.findById(m.getUserId()).map(u -> ProjectMemberResponse.from(m,u)).orElse(null)).filter(java.util.Objects::nonNull).toList(); }
    @Transactional public ProjectMemberResponse addMember(String projectId, String userId, AddProjectMemberRequest request) { access.requireOwner(projectId,userId); UserEntity user=userRepository.findByEmailIgnoreCase(request.getEmail().trim()).orElseThrow(() -> new IllegalArgumentException("No existe un usuario con ese correo.")); if(projectMemberRepository.existsByProjectIdAndUserId(projectId,user.getId())) throw new IllegalStateException("El usuario ya pertenece al proyecto."); try { ProjectMemberEntity member=projectMemberRepository.save(ProjectMemberEntity.builder().id(UUID.randomUUID().toString()).projectId(projectId).userId(user.getId()).role(request.getRole()==ProjectMemberRole.OWNER?ProjectMemberRole.EDITOR:request.getRole()).createdAt(LocalDateTime.now()).build()); return ProjectMemberResponse.from(member,user); } catch(DataIntegrityViolationException e) { throw new IllegalStateException("El usuario ya pertenece al proyecto."); } }
    @Transactional public void removeMember(String projectId, String userId, String memberId) { access.requireOwner(projectId,userId); ProjectMemberEntity member=projectMemberRepository.findById(memberId).orElseThrow(() -> new IllegalArgumentException("Miembro no encontrado.")); if(!projectId.equals(member.getProjectId())) throw new IllegalArgumentException("Miembro no encontrado."); if(member.getRole()==ProjectMemberRole.OWNER && projectMemberRepository.findByProjectId(projectId).stream().filter(m->m.getRole()==ProjectMemberRole.OWNER).count()<=1) throw new IllegalStateException("El proyecto debe conservar al menos un propietario."); projectMemberRepository.delete(member); }
    @Transactional public ProjectMemberResponse changeRole(String projectId,String userId,String memberId,ProjectMemberRole role) { access.requireOwner(projectId,userId); ProjectMemberEntity member=projectMemberRepository.findById(memberId).orElseThrow(() -> new IllegalArgumentException("Miembro no encontrado.")); if(!projectId.equals(member.getProjectId())) throw new IllegalArgumentException("Miembro no encontrado."); if(member.getRole()==ProjectMemberRole.OWNER && role!=ProjectMemberRole.OWNER && projectMemberRepository.findByProjectId(projectId).stream().filter(m->m.getRole()==ProjectMemberRole.OWNER).count()<=1) throw new IllegalStateException("El proyecto debe conservar al menos un propietario."); member.setRole(role); UserEntity user=userRepository.findById(member.getUserId()).orElseThrow(); return ProjectMemberResponse.from(projectMemberRepository.save(member),user); }
    public List<ProjectEntity> shared(String userId) { return projectMemberRepository.findByUserIdAndRoleNot(userId,ProjectMemberRole.OWNER).stream().map(m->projectRepository.findById(m.getProjectId()).orElse(null)).filter(java.util.Objects::nonNull).toList(); }
    public ProjectRoleResponse role(String projectId, String userId) { ProjectMemberRole role = access.resolveRole(projectId, userId); if (role == null) throw new IllegalArgumentException("No tienes acceso a este proyecto."); return new ProjectRoleResponse(role); }
    @Transactional public ShareLinkResponse shareLink(String projectId, String userId, ShareLinkRequest request, String baseUrl) { access.requireOwner(projectId, userId); if (request == null || request.mode() == null) throw new IllegalArgumentException("El modo de enlace es obligatorio."); ProjectEntity project=projectRepository.findById(projectId).orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado.")); if (request.mode() == ProjectShareMode.RESTRICTED) project.setShareToken(null); else if (project.getShareToken() == null) project.setShareToken(UUID.randomUUID().toString()); project.setShareMode(request.mode()); projectRepository.save(project); return new ShareLinkResponse(project.getShareMode(), project.getShareMode() == ProjectShareMode.RESTRICTED ? null : baseUrl + "/shared/" + project.getShareToken()); }
    public ShareLinkResponse getShareLink(String projectId, String userId, String baseUrl) { access.requireOwner(projectId,userId); ProjectEntity project=projectRepository.findById(projectId).orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado.")); return new ShareLinkResponse(project.getShareMode() == null ? ProjectShareMode.RESTRICTED : project.getShareMode(), project.getShareToken() == null ? null : baseUrl + "/shared/" + project.getShareToken()); }
    @Transactional public ShareLinkResponse regenerateShareLink(String projectId, String userId, String baseUrl) { access.requireOwner(projectId,userId); ProjectEntity project=projectRepository.findById(projectId).orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado.")); project.setShareToken(UUID.randomUUID().toString()); if (project.getShareMode() == null || project.getShareMode() == ProjectShareMode.RESTRICTED) project.setShareMode(ProjectShareMode.LINK_VIEWER); projectRepository.save(project); return new ShareLinkResponse(project.getShareMode(), baseUrl + "/shared/" + project.getShareToken()); }
    @Transactional public void disableShareLink(String projectId, String userId) { access.requireOwner(projectId,userId); ProjectEntity project=projectRepository.findById(projectId).orElseThrow(() -> new IllegalArgumentException("Proyecto no encontrado.")); project.setShareMode(ProjectShareMode.RESTRICTED); project.setShareToken(null); projectRepository.save(project); }
    public SharedProjectResponse resolveShareToken(String token, String userId) { ProjectEntity project=projectRepository.findByShareToken(token).orElseThrow(() -> new IllegalArgumentException("Enlace no válido.")); if (project.getShareMode() == null || project.getShareMode() == ProjectShareMode.RESTRICTED) throw new IllegalArgumentException("Este enlace ya no está activo."); return new SharedProjectResponse(ProjectMapper.toResponse(project), access.resolveRole(project.getId(),userId)); }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
