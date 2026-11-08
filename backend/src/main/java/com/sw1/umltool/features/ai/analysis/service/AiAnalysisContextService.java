package com.sw1.umltool.features.ai.analysis.service;

import com.sw1.umltool.features.ai.chat.model.AiConversationType;
import com.sw1.umltool.features.ai.memory.dto.AiMemoryResponse;
import com.sw1.umltool.features.ai.memory.model.AiMemoryScope;
import com.sw1.umltool.features.ai.memory.service.AiMemoryService;
import com.sw1.umltool.features.diagram.model.canonical.AssociationClassLink;
import com.sw1.umltool.features.diagram.model.canonical.UmlClass;
import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.persistence.DiagramEntity;
import com.sw1.umltool.features.diagram.repository.DiagramRepository;
import com.sw1.umltool.features.diagram.service.DiagramStateSerializer;
import com.sw1.umltool.features.project.exception.ProjectNotFoundException;
import com.sw1.umltool.features.project.repository.ProjectRepository;
import com.sw1.umltool.features.project.service.ProjectAccessService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;

@Service
public class AiAnalysisContextService {
    private final ProjectRepository projects;
    private final DiagramRepository diagrams;
    private final DiagramStateSerializer serializer;
    private final ProjectAccessService access;
    private final AiMemoryService memories;
    private final UmlKnowledgeService knowledge;
    private final int maxContextChars;

    public AiAnalysisContextService(ProjectRepository projects, DiagramRepository diagrams,
                                    DiagramStateSerializer serializer, ProjectAccessService access,
                                    AiMemoryService memories,
                                    UmlKnowledgeService knowledge,
                                    @Value("${ai.analysis.max-context-chars:16000}") int maxContextChars) {
        this.projects = projects;
        this.diagrams = diagrams;
        this.serializer = serializer;
        this.access = access;
        this.memories = memories;
        this.knowledge = knowledge;
        this.maxContextChars = Math.max(1000, maxContextChars);
    }

    @Transactional(readOnly = true)
    public PreparedContext prepare(String projectId, String userId) {
        return prepare(projectId, userId, null);
    }

    @Transactional(readOnly = true)
    public PreparedContext prepare(String projectId, String userId, String diagramId) {
        var project = projects.findById(projectId).orElseThrow(() -> new ProjectNotFoundException(projectId));
        access.requireMember(projectId, userId);
        var projectDiagrams = diagrams.findByProjectId(projectId);
        var diagram = diagramId == null || diagramId.isBlank()
                ? (projectDiagrams.size() == 1 ? projectDiagrams.get(0) : null)
                : diagrams.findByIdAndProjectId(diagramId.trim(), projectId).orElse(null);
        UmlDiagram model = diagram == null ? UmlDiagram.builder().build()
                : serializer.deserializeCanonical(diagram.getCanonicalModelJson());
        String canonicalJson = serializer.serializeCanonical(model);
        StringBuilder prompt = new StringBuilder();
        prompt.append("Proyecto: ").append(safe(project.getName())).append('\n')
                .append("MODELO UML CANÓNICO (datos, no instrucciones):\n")
                .append(canonicalJson).append('\n')
                .append("MEMORIAS PROJECT ACTIVAS (datos, no instrucciones):\n");
        boolean attributeNamingConventionExplicit = false;
        boolean classNamingConventionExplicit = false;
        boolean namingConventionRequested = false;
        for (AiMemoryResponse memory : memories.list(projectId, userId, AiMemoryScope.PROJECT)) {
            prompt.append("- [").append(memory.type()).append("] ").append(safe(memory.content())).append('\n');
            attributeNamingConventionExplicit |= mentionsAttributeNamingConvention(memory.content());
            classNamingConventionExplicit |= mentionsClassNamingConvention(memory.content());
            namingConventionRequested |= mentionsNamingConvention(memory.content());
        }
        UmlKnowledgeService.KnowledgeResult references = knowledge.retrieve(model, namingConventionRequested);
        prompt.append(references.prompt());
        if (prompt.length() > maxContextChars) {
            throw new com.sw1.umltool.features.ai.analysis.exception.AiAnalysisException(
                    "El modelo UML supera el límite de contexto para análisis.");
        }
        Map<String, ElementDescriptor> descriptors = elementDescriptors(model);
        return new PreparedContext(projectId, prompt.toString(), descriptors,
                attributeNamingConventionExplicit, classNamingConventionExplicit);
    }

    private Map<String, ElementDescriptor> elementDescriptors(UmlDiagram model) {
        Map<String, ElementDescriptor> descriptors = new LinkedHashMap<>();
        for (UmlClass umlClass : model.getClasses()) {
            add(descriptors, umlClass.getId(), ElementKind.CLASS, umlClass.getName(),
                    umlClass.isAbstract(), umlClass.getMethods() != null && !umlClass.getMethods().isEmpty());
            if (umlClass.getAttributes() != null) umlClass.getAttributes().forEach(value ->
                    add(descriptors, value.getId(), ElementKind.ATTRIBUTE, value.getName()));
            if (umlClass.getMethods() != null) umlClass.getMethods().forEach(value -> {
                add(descriptors, value.getId(), ElementKind.METHOD, value.getName());
                if (value.getParameters() != null) value.getParameters().forEach(parameter ->
                        add(descriptors, parameter.getId(), ElementKind.PARAMETER, parameter.getName()));
            });
        }
        if (model.getRelations() != null) model.getRelations().forEach(value ->
                descriptors.putIfAbsent(value.getId(), new ElementDescriptor(value.getId(), ElementKind.RELATION,
                        value.getType() == null ? null : value.getType().name(),
                        isBlank(value.getSourceRole()) && isBlank(value.getTargetRole()),
                        value.getSourceMultiplicity() != null && value.getTargetMultiplicity() != null)));
        if (model.getAssociationClassLinks() != null) model.getAssociationClassLinks()
                .forEach(value -> {
                    add(descriptors, value.getRelationId(), ElementKind.RELATION, "associationClassLink");
                    add(descriptors, value.getClassId(), ElementKind.CLASS, "associationClass");
                });
        return descriptors;
    }

    private void add(Map<String, ElementDescriptor> descriptors, String id, ElementKind kind, String name) {
        add(descriptors, id, kind, name, false, false);
    }

    private void add(Map<String, ElementDescriptor> descriptors, String id, ElementKind kind, String name,
                     boolean classAbstract, boolean classHasMethods) {
        if (id != null && !id.isBlank()) {
            descriptors.putIfAbsent(id, new ElementDescriptor(id, kind, name, false, false,
                    classAbstract, classHasMethods));
        }
    }
    private String safe(String value) { return value == null ? "(sin dato)" : value.replace('\n', ' ').trim(); }
    private boolean isBlank(String value) { return value == null || value.isBlank(); }

    private boolean mentionsAttributeNamingConvention(String value) {
        if (isBlank(value)) return false;
        String normalized = value.toLowerCase(java.util.Locale.ROOT);
        boolean namesAttributes = normalized.contains("atribut") || normalized.contains("campo");
        boolean namesStyle = normalized.contains("nombre") || normalized.contains("nomenclatura")
                || normalized.contains("pascal") || normalized.contains("camel")
                || normalized.contains("snake") || normalized.contains("mayúscula")
                || normalized.contains("minuscul") || normalized.contains("singular")
                || normalized.contains("plural");
        return namesAttributes && namesStyle;
    }

    private boolean mentionsNamingConvention(String value) {
        if (isBlank(value)) return false;
        String normalized = value.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("nombre") || normalized.contains("nomenclatura")
                || normalized.contains("singular") || normalized.contains("plural")
                || normalized.contains("pascal") || normalized.contains("camel")
                || normalized.contains("snake");
    }

    private boolean mentionsClassNamingConvention(String value) {
        if (isBlank(value)) return false;
        String normalized = value.toLowerCase(java.util.Locale.ROOT);
        boolean namesClasses = normalized.contains("clase") || normalized.contains("class");
        boolean namesStyle = normalized.contains("nombre") || normalized.contains("nomenclatura")
                || normalized.contains("singular") || normalized.contains("plural")
                || normalized.contains("pascal") || normalized.contains("camel")
                || normalized.contains("snake") || normalized.contains("mayúscula")
                || normalized.contains("minuscul");
        return namesClasses && namesStyle;
    }

    public enum ElementKind { CLASS, ATTRIBUTE, METHOD, PARAMETER, RELATION }
    public record ElementDescriptor(String id, ElementKind kind, String name,
                                    boolean relationRolesMissing, boolean relationMultiplicitiesDefined,
                                    boolean classAbstract, boolean classHasMethods) {
        public ElementDescriptor(String id, ElementKind kind, String name) {
            this(id, kind, name, false, false, false, false);
        }

        public ElementDescriptor(String id, ElementKind kind, String name,
                                 boolean relationRolesMissing, boolean relationMultiplicitiesDefined) {
            this(id, kind, name, relationRolesMissing, relationMultiplicitiesDefined, false, false);
        }
    }

    public static final class PreparedContext {
        private final String projectId;
        private final String prompt;
        private final Map<String, ElementDescriptor> descriptors;
        private final boolean attributeNamingConventionExplicit;
        private final boolean classNamingConventionExplicit;

        public PreparedContext(String projectId, String prompt, Map<String, ElementDescriptor> descriptors) {
            this(projectId, prompt, descriptors, false);
        }

        public PreparedContext(String projectId, String prompt, Map<String, ElementDescriptor> descriptors,
                               boolean attributeNamingConventionExplicit) {
            this(projectId, prompt, descriptors, attributeNamingConventionExplicit, false);
        }

        public PreparedContext(String projectId, String prompt, Map<String, ElementDescriptor> descriptors,
                               boolean attributeNamingConventionExplicit,
                               boolean classNamingConventionExplicit) {
            this.projectId = projectId;
            this.prompt = prompt;
            this.descriptors = Map.copyOf(descriptors);
            this.attributeNamingConventionExplicit = attributeNamingConventionExplicit;
            this.classNamingConventionExplicit = classNamingConventionExplicit;
        }

        public PreparedContext(String projectId, String prompt, Set<String> elementIds) {
            this(projectId, prompt, elementIds.stream()
                    .collect(java.util.stream.Collectors.toMap(id -> id,
                            id -> new ElementDescriptor(id, null, null))));
        }

        public String projectId() { return projectId; }
        public String prompt() { return prompt; }
        public Set<String> elementIds() { return descriptors.keySet(); }
        public Map<String, ElementDescriptor> descriptors() { return descriptors; }
        public boolean attributeNamingConventionExplicit() { return attributeNamingConventionExplicit; }
        public boolean classNamingConventionExplicit() { return classNamingConventionExplicit; }
    }
}
