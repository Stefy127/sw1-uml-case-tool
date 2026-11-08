package com.sw1.umltool.features.ai.chat.service;

import com.sw1.umltool.features.ai.chat.model.AiContextScope;
import com.sw1.umltool.features.diagram.model.canonical.UmlClass;
import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.canonical.UmlRelation;
import com.sw1.umltool.features.diagram.model.persistence.DiagramEntity;
import com.sw1.umltool.features.diagram.repository.DiagramRepository;
import com.sw1.umltool.features.diagram.service.DiagramStateSerializer;
import com.sw1.umltool.features.project.service.ProjectAccessService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiFactualQueryService {
    private static final Pattern NAMED_QUERY = Pattern.compile("(?:atributos?|campos?|m[eé]todos?|operaciones?|relaciones?)\\s+(?:tiene|hay|existen|de)\\s+(?:la\\s+clase\\s+)?(.+?)(?:[?¿!]*)$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern NORMALIZED_NAMED_QUERY = Pattern.compile("(?:atributos?|campos?|metodos?|operaciones?|relaciones?)\\s+(?:tiene|hay|existen|de)\\s+(?:la\\s+clase\\s+)?(.+?)[?¿!]*$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private final DiagramRepository diagrams;
    private final DiagramStateSerializer serializer;
    private final ProjectAccessService access;

    public AiFactualQueryService(DiagramRepository diagrams, DiagramStateSerializer serializer, ProjectAccessService access) {
        this.diagrams = diagrams;
        this.serializer = serializer;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public Optional<String> answer(String projectId, String userId, String question, String diagramId, AiContextScope requestedScope) {
        access.requireMember(projectId, userId);
        String normalized = normalize(question);
        AiContextScope scope = requestedScope == null
                ? (diagramId == null || diagramId.isBlank() ? AiContextScope.PROJECT : AiContextScope.DIAGRAM)
                : requestedScope;
        if (isDiagramInventory(normalized)) return Optional.of(listDiagrams(projectId));
        if (isDiagramCount(normalized)) return Optional.of(countDiagrams(projectId));

        String kind = namedKind(normalized);
        if (kind == null) {
            if (isClassInventory(normalized)) return listClasses(projectId, diagramId, scope);
            return Optional.empty();
        }
        Matcher matcher = NORMALIZED_NAMED_QUERY.matcher(normalized);
        if (!matcher.find()) return Optional.empty();
        String className = matcher.group(1).trim().replaceAll("[?¿!]+$", "").trim();
        return queryClass(projectId, diagramId, scope, kind, className);
    }

    private String listDiagrams(String projectId) {
        long count = diagrams.countByProjectId(projectId);
        if (count == 0) return "No hay diagramas en este proyecto.";
        var page = diagrams.findByProjectId(projectId, PageRequest.of(0, 50));
        String names = page.getContent().stream().map(DiagramEntity::getName).map(this::safe)
                .reduce((left, right) -> left + ", " + right).orElse("");
        return count == 1 ? "Hay 1 diagrama en este proyecto: " + names + "."
                : "Hay " + count + " diagramas en este proyecto: " + names + ".";
    }

    private String countDiagrams(String projectId) {
        long count = diagrams.countByProjectId(projectId);
        return count == 1 ? "Hay 1 diagrama en este proyecto." : "Hay " + count + " diagramas en este proyecto.";
    }

    private Optional<String> listClasses(String projectId, String diagramId, AiContextScope scope) {
        List<DiagramWithModel> values = models(projectId, diagramId, scope);
        if (scope == AiContextScope.PROJECT) {
            if (values.isEmpty()) return Optional.of("No hay diagramas autorizados para consultar.");
            String result = values.stream().map(value -> safe(value.diagram().getName()) + ": " +
                    value.model().getClasses().stream().map(UmlClass::getName).map(this::safe).reduce((a, b) -> a + ", " + b).orElse("(sin clases)"))
                    .reduce((a, b) -> a + "; " + b).orElse("");
            return Optional.of("Clases por diagrama: " + result + ".");
        }
        if (values.size() != 1) return Optional.of("No se pudo resolver el diagrama autorizado.");
        List<String> names = values.get(0).model().getClasses().stream().map(UmlClass::getName).map(this::safe).toList();
        return Optional.of(names.isEmpty() ? "No hay clases definidas en este diagrama." : "Las clases del diagrama son: " + String.join(", ", names) + ".");
    }

    private Optional<String> queryClass(String projectId, String diagramId, AiContextScope scope, String kind, String requestedName) {
        List<ClassMatch> matches = new ArrayList<>();
        for (DiagramWithModel value : models(projectId, diagramId, scope)) {
            value.model().getClasses().stream().filter(item -> normalize(item.getName()).equals(normalize(requestedName)))
                    .forEach(item -> matches.add(new ClassMatch(value.diagram().getName(), item)));
        }
        if (matches.isEmpty()) return Optional.of("No existe una clase llamada " + safe(requestedName) + " en el contexto consultado.");
        if (matches.size() > 1) return Optional.of("La clase " + safe(requestedName) + " aparece en varios diagramas: " +
                matches.stream().map(match -> safe(match.diagramName())).distinct().reduce((a, b) -> a + ", " + b).orElse("") + ". Indica cuál quieres consultar.");
        UmlClass umlClass = matches.get(0).umlClass();
        return Optional.of(formatClassQuery(kind, umlClass));
    }

    private String formatClassQuery(String kind, UmlClass umlClass) {
        if ("atributos".equals(kind)) {
            if (umlClass.getAttributes() == null || umlClass.getAttributes().isEmpty()) return "La clase " + safe(umlClass.getName()) + " no tiene atributos definidos actualmente.";
            return "Atributos de " + safe(umlClass.getName()) + ": " + umlClass.getAttributes().stream().map(a -> safe(a.getName()) + " : " + safe(a.getType())).reduce((a, b) -> a + ", " + b).orElse("") + ".";
        }
        if ("métodos".equals(kind)) {
            if (umlClass.getMethods() == null || umlClass.getMethods().isEmpty()) return "La clase " + safe(umlClass.getName()) + " no tiene métodos definidos actualmente.";
            return "Métodos de " + safe(umlClass.getName()) + ": " + umlClass.getMethods().stream().map(m -> safe(m.getName())).reduce((a, b) -> a + ", " + b).orElse("") + ".";
        }
        return "Las relaciones de " + safe(umlClass.getName()) + " se consultan desde el diagrama activo. Indica también el otro extremo si necesitas una relación concreta.";
    }

    private List<DiagramWithModel> models(String projectId, String diagramId, AiContextScope scope) {
        List<DiagramEntity> entities;
        if (scope == AiContextScope.DIAGRAM && diagramId != null && !diagramId.isBlank()) {
            entities = diagrams.findByIdAndProjectId(diagramId.trim(), projectId).map(List::of).orElse(List.of());
        } else if (scope == AiContextScope.DIAGRAM) {
            List<DiagramEntity> projectDiagrams = diagrams.findByProjectId(projectId);
            entities = projectDiagrams.size() == 1 ? projectDiagrams : List.of();
        } else {
            entities = new ArrayList<>();
            int page = 0;
            org.springframework.data.domain.Page<DiagramEntity> current;
            do {
                current = diagrams.findByProjectId(projectId, PageRequest.of(page++, 20));
                entities.addAll(current.getContent());
            } while (current.hasNext());
        }
        return entities.stream().map(entity -> new DiagramWithModel(entity, serializer.deserializeCanonical(entity.getCanonicalModelJson()))).toList();
    }

    private boolean isDiagramInventory(String value) { return value.contains("que diagramas existen") || value.contains("que diagramas hay") || value.contains("listar diagramas"); }
    private boolean isDiagramCount(String value) { return value.contains("cuantos diagramas") || value.contains("cuantas diagramas"); }
    private boolean isClassInventory(String value) { return value.contains("que clases existen") || value.contains("que clases hay") || value.contains("listar clases"); }
    private String namedKind(String value) {
        if (value.contains("atributos") || value.contains("campos")) return "atributos";
        if (value.contains("metodos") || value.contains("operaciones")) return "métodos";
        if (value.contains("relaciones")) return "relaciones";
        return null;
    }
    private String normalize(String value) { return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").trim().toLowerCase(Locale.ROOT); }
    private String safe(String value) { return value == null ? "(sin nombre)" : value.replace('\n', ' ').trim(); }
    private record DiagramWithModel(DiagramEntity diagram, UmlDiagram model) { }
    private record ClassMatch(String diagramName, UmlClass umlClass) { }
}
