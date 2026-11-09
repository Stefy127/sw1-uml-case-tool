package com.sw1.umltool.features.diagram.service;

import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.persistence.DiagramEntity;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;
import com.sw1.umltool.features.diagram.repository.DiagramRepository;
import com.sw1.umltool.features.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.sw1.umltool.features.project.service.ProjectAccessService;
import com.sw1.umltool.features.diagram.validation.CanonicalModelValidator;

@Service
public class DiagramService {

    private final DiagramRepository diagramRepository;
    private final ProjectRepository projectRepository;
    private final DiagramStateSerializer diagramStateSerializer;
    private final ProjectAccessService access;
    private final CanonicalModelValidator canonicalModelValidator;

    public DiagramService(
            DiagramRepository diagramRepository,
            ProjectRepository projectRepository,
            DiagramStateSerializer diagramStateSerializer) {
        this.diagramRepository = diagramRepository;
        this.projectRepository = projectRepository;
        this.diagramStateSerializer = diagramStateSerializer;
        this.access = null;
        this.canonicalModelValidator = null;
    }
    @org.springframework.beans.factory.annotation.Autowired
    public DiagramService(DiagramRepository repository, ProjectRepository projects, DiagramStateSerializer serializer, ProjectAccessService access, CanonicalModelValidator canonicalModelValidator) { this.diagramRepository=repository; this.projectRepository=projects; this.diagramStateSerializer=serializer; this.access=access; this.canonicalModelValidator=canonicalModelValidator; }

    public DiagramEntity createDiagram(String projectId, String name) {
        return createDiagram(projectId, name, null, null);
    }

    public DiagramEntity createDiagram(String projectId, String name, UmlDiagram initialCanonicalModel, DiagramViewState initialViewState) {
        if (isBlank(projectId)) throw new IllegalArgumentException("Project id is required");
        if (isBlank(name)) throw new IllegalArgumentException("Diagram name is required");
        if (!projectRepository.existsById(projectId)) throw new IllegalArgumentException("Project not found: " + projectId);

        String diagramId = UUID.randomUUID().toString();
        UmlDiagram canonical = initialCanonicalModel == null ? UmlDiagram.builder().build() : initialCanonicalModel;
        canonical.setId(diagramId);
        canonical.setName(name);
        canonical.setVersion(0);
        if (canonical.getClasses() == null) canonical.setClasses(new java.util.ArrayList<>());
        if (canonical.getRelations() == null) canonical.setRelations(new java.util.ArrayList<>());
        if (canonical.getAssociationClassLinks() == null) canonical.setAssociationClassLinks(new java.util.ArrayList<>());
        if (canonicalModelValidator != null) {
            var validation = canonicalModelValidator.validate(canonical);
            if (!validation.isValid()) throw new IllegalArgumentException("El modelo inicial del diagrama no es válido: " + validation.getErrors().get(0).getMessage());
        }
        DiagramViewState viewState = initialViewState == null ? DiagramViewState.builder().build() : initialViewState;
        viewState.setDiagramId(diagramId);
        if (viewState.getNodes() == null) viewState.setNodes(new java.util.ArrayList<>());
        if (viewState.getRelations() == null) viewState.setRelations(new java.util.ArrayList<>());
        LocalDateTime now = LocalDateTime.now();
        return diagramRepository.save(DiagramEntity.builder()
                .id(diagramId)
                .projectId(projectId)
                .name(name)
                .version(0)
                .canonicalModelJson(diagramStateSerializer.serializeCanonical(canonical))
                .viewStateJson(diagramStateSerializer.serializeViewState(viewState))
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    public Optional<DiagramEntity> findById(String id) {
        return diagramRepository.findById(id);
    }

    public List<DiagramEntity> findByProjectId(String projectId) {
        return diagramRepository.findByProjectId(projectId);
    }
    public DiagramEntity createDiagram(String projectId,String name,String userId) { access.requireEditor(projectId,userId); return createDiagram(projectId,name); }
    public DiagramEntity createDiagram(String projectId, String name, String userId, UmlDiagram initialCanonicalModel, DiagramViewState initialViewState) { access.requireEditor(projectId, userId); return createDiagram(projectId, name, initialCanonicalModel, initialViewState); }
    public List<DiagramEntity> findByProjectId(String projectId,String userId) { access.requireRead(projectId,userId); return findByProjectId(projectId); }
    public Optional<DiagramEntity> findById(String id,String userId) { access.requireDiagramMember(id,userId); return findById(id); }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
