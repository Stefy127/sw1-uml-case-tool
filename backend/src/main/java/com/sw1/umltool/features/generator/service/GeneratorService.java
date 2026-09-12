package com.sw1.umltool.features.generator.service;

import com.sw1.umltool.features.diagram.model.canonical.*;
import com.sw1.umltool.features.diagram.model.canonical.enums.RelationType;
import com.sw1.umltool.features.diagram.model.persistence.DiagramEntity;
import com.sw1.umltool.features.diagram.service.DiagramStateSerializer;
import com.sw1.umltool.features.generator.dto.GenerateBackendRequest;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class GeneratorService {
    private record RelationSpec(String field, String idField, String otherType, String otherRepository, String otherIdType, boolean collection, boolean owningSide) {}
    private final DiagramStateSerializer serializer;
    private final GeneratorValidator validator;

    public GeneratorService(DiagramStateSerializer serializer, GeneratorValidator validator) { this.serializer = serializer; this.validator = validator; }

    public byte[] generate(DiagramEntity entity, GenerateBackendRequest request) {
        UmlDiagram diagram = serializer.deserializeCanonical(entity.getCanonicalModelJson());
        List<String> errors = validator.validate(diagram);
        if (!errors.isEmpty()) throw new IllegalArgumentException("No se puede generar el backend: " + String.join("; ", errors));
        String artifact = safeArtifact(request == null ? null : request.artifactId(), entity.getName());
        String base = request == null ? "com.generated.app" : request.effectiveBasePackage();
        if (!base.matches("[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*")) throw new IllegalArgumentException("El paquete base no es válido para Java.");
        String group = request == null ? "com.generated" : request.effectiveGroupId();
        String project = request == null ? entity.getName() : request.effectiveProjectName(entity.getName());
        Map<String, UmlClass> classes = new LinkedHashMap<>();
        for (UmlClass umlClass : diagram.getClasses()) classes.put(umlClass.getId(), umlClass);
        Map<String, UmlRelation> associationClassRelations = associationClassRelations(diagram);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
                String root = artifact + "/";
                put(zip, root + "pom.xml", pom(group, artifact, project));
                put(zip, root + "README.md", readme(project, artifact, diagram, classes));
                put(zip, root + "src/main/resources/application.properties", properties());
                String packagePath = base.replace('.', '/');
                put(zip, root + "src/main/java/" + packagePath + "/GeneratedApplication.java", application(base, project));
                put(zip, root + "src/main/java/" + packagePath + "/config/OpenApiConfig.java", openApiConfig(base, project));
                for (UmlClass umlClass : classes.values()) {
                    String parent = inheritanceParent(umlClass, diagram, classes);
                    boolean dto = needsDto(umlClass, diagram, associationClassRelations);
                    put(zip, root + "src/main/java/" + packagePath + "/entity/" + javaName(umlClass.getName()) + ".java", entity(base, umlClass, parent, diagram, classes, associationClassRelations));
                    String name = javaName(umlClass.getName());
                    String entityIdType = idType(umlClass, classes, diagram);
                    if (dto) {
                        put(zip, root + "src/main/java/" + packagePath + "/dto/" + name + "RequestDto.java", dto(base, umlClass, false, diagram, classes, associationClassRelations));
                        put(zip, root + "src/main/java/" + packagePath + "/dto/" + name + "ResponseDto.java", dto(base, umlClass, true, diagram, classes, associationClassRelations));
                    }
                    put(zip, root + "src/main/java/" + packagePath + "/repository/" + name + "Repository.java", repository(base, name, entityIdType));
                    put(zip, root + "src/main/java/" + packagePath + "/service/" + name + "Service.java", dto ? dtoService(base, umlClass, entityIdType, diagram, classes, associationClassRelations) : service(base, name, entityIdType));
                    put(zip, root + "src/main/java/" + packagePath + "/controller/" + name + "Controller.java", dto ? dtoController(base, umlClass, entityIdType) : controller(base, name, entityIdType));
                }
                for (UmlClass bridge : bridgeClasses(diagram, classes, associationClassRelations)) {
                    String name = javaName(bridge.getName());
                    put(zip, root + "src/main/java/" + packagePath + "/entity/" + name + ".java", entity(base, bridge, null, diagram, classes, associationClassRelations));
                    put(zip, root + "src/main/java/" + packagePath + "/repository/" + name + "Repository.java", repository(base, name, "Long"));
                    put(zip, root + "src/main/java/" + packagePath + "/service/" + name + "Service.java", service(base, name, "Long"));
                    put(zip, root + "src/main/java/" + packagePath + "/controller/" + name + "Controller.java", controller(base, name, "Long"));
                }
                List<UmlClass> documentedClasses = new ArrayList<>(classes.values());
                documentedClasses.addAll(bridgeClasses(diagram, classes, associationClassRelations));
                put(zip, root + "postman/" + safeArtifact(project, artifact) + ".postman_collection.json", postmanCollection(project, diagram, documentedClasses, classes, associationClassRelations));
            }
            return bytes.toByteArray();
        } catch (Exception exception) { throw new IllegalStateException("No se pudo empaquetar el backend generado.", exception); }
    }

    public String fileName(String requested, String fallback) { return safeArtifact(requested, fallback); }

    private String entity(String base, UmlClass umlClass, String parent, UmlDiagram diagram, Map<String, UmlClass> classes, Map<String, UmlRelation> acRelations) {
        String name = javaName(umlClass.getName());
        String idType = idType(umlClass, classes, diagram);
        StringBuilder out = new StringBuilder("package ").append(base).append(".entity;\n\n");
        out.append("import jakarta.persistence.*;\nimport com.fasterxml.jackson.annotation.JsonIgnore;\nimport java.time.*;\nimport java.math.BigDecimal;\nimport java.util.*;\n\n");
        out.append("@Entity\n@Table(name=\"").append(sqlName(name)).append("\")\n");
        if (isInheritanceRoot(umlClass, diagram)) out.append("@Inheritance(strategy = InheritanceType.JOINED)\n");
        if (parent == null) out.append("public "); else out.append("public ");
        out.append("class ").append(name);
        if (parent != null) out.append(" extends ").append(parent);
        out.append(" {\n");
        out.append("    public ").append(name).append("() {}\n\n");
        boolean inheritedId = parent != null;
        boolean hasId = inheritedId;
        Set<String> fields = new HashSet<>();
        for (UmlAttribute attribute : umlClass.getAttributes()) {
            if (inheritedId && attribute.getName() != null && attribute.getName().equalsIgnoreCase("id")) continue;
            String field = javaField(attribute.getName()); if (!fields.add(field)) continue;
            String type = javaType(attribute.getType(), classes);
            if (attribute.getName() != null && attribute.getName().equalsIgnoreCase("id")) { out.append("    @Id\n    @GeneratedValue(strategy = GenerationType.IDENTITY)\n"); hasId = true; }
            if (attribute.isStatic()) out.append("    public static final "); else out.append("    private ");
            out.append(type).append(' ').append(field).append(defaultValue(attribute)).append(";\n\n");
        }
        if (!hasId) out.append("    @Id\n    @GeneratedValue(strategy = GenerationType.IDENTITY)\n    private Long id;\n\n");
        for (UmlRelation relation : diagram.getRelations()) {
            if (relation.getType() == RelationType.INHERITANCE || relation.getType() == RelationType.DEPENDENCY || acRelations.containsValue(relation)) continue;
            appendRelationFields(out, fields, umlClass, relation, classes);
        }
        UmlRelation linkedAssociation = acRelations.get(umlClass.getId());
        if (linkedAssociation != null) {
            addAssociationClassEnd(out, fields, classes, linkedAssociation.getSourceClassId(), "associationSource");
            addAssociationClassEnd(out, fields, classes, linkedAssociation.getTargetClassId(), "associationTarget");
        } else if (umlClass.getId() != null && umlClass.getId().startsWith("bridge-")) {
            UmlRelation bridgeRelation = diagram.getRelations().stream().filter(r -> ("bridge-" + r.getId()).equals(umlClass.getId())).findFirst().orElse(null);
            if (bridgeRelation != null) {
                addAssociationClassEnd(out, fields, classes, bridgeRelation.getSourceClassId(), "source");
                addAssociationClassEnd(out, fields, classes, bridgeRelation.getTargetClassId(), "target");
            }
        }
        appendJavaBeanAccessors(out);
        for (UmlMethod method : umlClass.getMethods()) {
            if (method == null || method.getName() == null || !validator.validIdentifier(javaName(method.getName()))) continue;
            String returnType = javaType(method.getReturnType(), classes);
            out.append("    public ").append(returnType).append(' ').append(javaName(method.getName())).append("() {\n").append(methodBody(returnType)).append("    }\n\n");
        }
        out.append("}\n"); return out.toString();
    }

    private void appendJavaBeanAccessors(StringBuilder out) {
        Matcher matcher = Pattern.compile("\\s+private\\s+([A-Za-z0-9_<>?, ]+)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*(?:=[^;]*)?;").matcher(out);
        List<String[]> fields = new ArrayList<>();
        while (matcher.find()) fields.add(new String[]{matcher.group(1).trim(), matcher.group(2)});
        for (String[] field : fields) {
            String type = field[0];
            String name = field[1];
            String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
            out.append("    public ").append(type).append(" get").append(suffix).append("() { return ").append(name).append("; }\n");
            out.append("    public void set").append(suffix).append('(').append(type).append(' ').append(name).append(") { this.").append(name).append(" = ").append(name).append("; }\n\n");
        }
    }

    private void addAssociationClassEnd(StringBuilder out, Set<String> fields, Map<String, UmlClass> classes, String classId, String preferredField) {
        UmlClass target = classes.get(classId);
        if (target == null) return;
        String type = javaName(target.getName());
        String field = preferredField;
        if (!fields.add(field)) {
            field = javaField(type);
            if (!fields.add(field)) return;
        }
        out.append("    @ManyToOne\n    @JoinColumn(name=\"").append(sqlName(field)).append("_id\")\n    private ").append(type).append(' ').append(field).append(";\n\n");
    }

    private void appendRelationFields(StringBuilder out, Set<String> fields, UmlClass current, UmlRelation relation, Map<String, UmlClass> classes) {
        UmlClass source = classes.get(relation.getSourceClassId());
        UmlClass target = classes.get(relation.getTargetClassId());
        if (source == null || target == null || source.getId().equals(target.getId())) return;
        boolean currentIsSource = current.getId().equals(source.getId());
        boolean currentIsTarget = current.getId().equals(target.getId());
        if (!currentIsSource && !currentIsTarget) return;
        boolean sourceMany = many(relation.getSourceMultiplicity());
        boolean targetMany = many(relation.getTargetMultiplicity());
        if (sourceMany && targetMany) return; // explicit bridge entity handles N:M

        if (relation.getType() == RelationType.COMPOSITION) {
            appendCompositionFields(out, fields, current, relation, source, target, sourceMany, targetMany);
            return;
        }

        String sourceType = javaName(source.getName());
        String targetType = javaName(target.getName());
        String sourceField = javaField(sourceType);
        String targetField = javaField(targetType);

        if (targetMany) {
            if (currentIsTarget) {
                addField(out, fields, "    @ManyToOne(optional = " + !required(relation.getTargetMultiplicity()) + ")\n    @JoinColumn(name=\"" + sqlName(sourceField) + "_id\")\n    private " + sourceType + " " + sourceField + ";\n\n", sourceField);
            } else {
                String field = plural(targetField);
                String annotation = "    @OneToMany(mappedBy = \"" + sourceField + "\")\n    @JsonIgnore\n";
                addField(out, fields, annotation + "    private List<" + targetType + "> " + field + " = new ArrayList<>();\n\n", field);
            }
            return;
        }
        if (sourceMany) {
            if (currentIsSource) {
                addField(out, fields, "    @ManyToOne(optional = " + !required(relation.getSourceMultiplicity()) + ")\n    @JoinColumn(name=\"" + sqlName(targetField) + "_id\")\n    private " + targetType + " " + targetField + ";\n\n", targetField);
            } else {
                String field = plural(sourceField);
                String annotation = "    @OneToMany(mappedBy = \"" + targetField + "\")\n    @JsonIgnore\n";
                addField(out, fields, annotation + "    private List<" + sourceType + "> " + field + " = new ArrayList<>();\n\n", field);
            }
            return;
        }
        // For 1:1, the target/part is the deterministic owner. The whole is inverse.
        if (currentIsTarget) {
            addField(out, fields, "    @OneToOne\n    @JoinColumn(name=\"" + sqlName(sourceField) + "_id\")\n    private " + sourceType + " " + sourceField + ";\n\n", sourceField);
        } else {
            String annotation = "    @OneToOne(mappedBy = \"" + sourceField + "\")\n    @JsonIgnore\n";
            addField(out, fields, annotation + "    private " + targetType + " " + targetField + ";\n\n", targetField);
        }
    }

    private void appendCompositionFields(StringBuilder out, Set<String> fields, UmlClass current,
            UmlRelation relation, UmlClass source, UmlClass target, boolean sourceMany, boolean targetMany) {
        UmlClass whole;
        UmlClass part;
        if (sourceMany != targetMany) {
            // In a 1:N composition the one end is the whole and the many end is the part.
            whole = sourceMany ? target : source;
            part = sourceMany ? source : target;
        } else {
            // The persisted editor convention for 1:1 composition is part(source) -> whole(target).
            whole = target;
            part = source;
        }
        String wholeType = javaName(whole.getName());
        String partType = javaName(part.getName());
        String wholeField = javaField(wholeType);
        String partField = javaField(partType);
        boolean currentIsWhole = current.getId().equals(whole.getId());
        boolean currentIsPart = current.getId().equals(part.getId());
        if (!currentIsWhole && !currentIsPart) return;

        boolean partMany = (part.getId().equals(relation.getSourceClassId()) ? sourceMany : targetMany);
        if (partMany) {
            if (currentIsPart) {
                addField(out, fields, "    @ManyToOne\n    @JoinColumn(name=\"" + sqlName(wholeField) + "_id\")\n    private " + wholeType + " " + wholeField + ";\n\n", wholeField);
            } else {
                addField(out, fields, "    @OneToMany(mappedBy = \"" + wholeField + "\", cascade = CascadeType.ALL, orphanRemoval = true)\n    @JsonIgnore\n    private List<" + partType + "> " + plural(partField) + " = new ArrayList<>();\n\n", plural(partField));
            }
        } else if (currentIsPart) {
            addField(out, fields, "    @OneToOne\n    @JoinColumn(name=\"" + sqlName(wholeField) + "_id\")\n    private " + wholeType + " " + wholeField + ";\n\n", wholeField);
        } else {
            addField(out, fields, "    @OneToOne(mappedBy = \"" + wholeField + "\", cascade = CascadeType.ALL, orphanRemoval = true)\n    @JsonIgnore\n    private " + partType + " " + partField + ";\n\n", partField);
        }
    }

    private void addField(StringBuilder out, Set<String> fields, String source, String field) {
        if (fields.add(field)) out.append(source);
    }

    private boolean needsDto(UmlClass umlClass, UmlDiagram diagram, Map<String, UmlRelation> associationClassRelations) {
        if (associationClassRelations.containsKey(umlClass.getId())) return true;
        Set<String> effectiveClassIds = effectiveClassIds(umlClass, diagram);
        return diagram.getRelations().stream().anyMatch(r -> r.getType() != RelationType.DEPENDENCY
                && r.getType() != RelationType.INHERITANCE
                && (effectiveClassIds.contains(r.getSourceClassId()) || effectiveClassIds.contains(r.getTargetClassId())));
    }

    private List<RelationSpec> relationSpecs(UmlClass current, UmlDiagram diagram, Map<String, UmlClass> classes,
        Map<String, UmlRelation> associationClassRelations, boolean requestOnly) {
        Map<String, RelationSpec> result = new LinkedHashMap<>();
        for (UmlClass declaringClass : effectiveHierarchy(current, diagram, classes)) {
            for (UmlRelation relation : diagram.getRelations()) {
            if (relation.getType() == RelationType.DEPENDENCY || relation.getType() == RelationType.INHERITANCE) continue;
            if (representedByAssociationClass(relation, associationClassRelations)) continue;
            boolean source = Objects.equals(declaringClass.getId(), relation.getSourceClassId());
            boolean target = Objects.equals(declaringClass.getId(), relation.getTargetClassId());
            if (!source && !target) continue;
            UmlClass other = classes.get(source ? relation.getTargetClassId() : relation.getSourceClassId());
            if (other == null || Objects.equals(other.getId(), current.getId())) continue;
            boolean sourceMany = many(relation.getSourceMultiplicity());
            boolean targetMany = many(relation.getTargetMultiplicity());
            boolean collection = sourceMany && targetMany || source && targetMany || target && sourceMany;
            boolean owningSide = isOwningSide(declaringClass, relation, sourceMany, targetMany);
            if (requestOnly && !owningSide) continue;
            String otherField = javaField(javaName(other.getName()));
            String field = collection ? plural(otherField) : otherField;
            String idField = field + (collection ? "Ids" : "Id");
            result.putIfAbsent(idField, new RelationSpec(field, idField, javaName(other.getName()), javaName(other.getName()) + "Repository", idType(other, classes, diagram), collection, owningSide));
            }
        }
        UmlRelation association = associationClassRelations.get(current.getId());
        if (association != null) {
            UmlClass source = classes.get(association.getSourceClassId());
            UmlClass target = classes.get(association.getTargetClassId());
            if (source != null) result.putIfAbsent("sourceId", new RelationSpec("associationSource", "sourceId", javaName(source.getName()), javaName(source.getName()) + "Repository", idType(source, classes, diagram), false, true));
            if (target != null) result.putIfAbsent("targetId", new RelationSpec("associationTarget", "targetId", javaName(target.getName()), javaName(target.getName()) + "Repository", idType(target, classes, diagram), false, true));
        }
        return new ArrayList<>(result.values());
    }

    private Set<String> effectiveClassIds(UmlClass current, UmlDiagram diagram) {
        Set<String> ids = new LinkedHashSet<>();
        String itemId = current == null ? null : current.getId();
        while (itemId != null && ids.add(itemId)) {
            String childId = itemId;
            itemId = diagram.getRelations().stream()
                    .filter(r -> r.getType() == RelationType.INHERITANCE && Objects.equals(childId, r.getSourceClassId()))
                    .map(UmlRelation::getTargetClassId).findFirst().orElse(null);
        }
        return ids;
    }

    private List<UmlClass> effectiveHierarchy(UmlClass current, UmlDiagram diagram, Map<String, UmlClass> classes) {
        List<UmlClass> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        UmlClass item = current;
        while (item != null && item.getId() != null && visited.add(item.getId())) {
            result.add(item);
            String itemId = item.getId();
            String parentId = diagram.getRelations().stream()
                    .filter(r -> r.getType() == RelationType.INHERITANCE && Objects.equals(itemId, r.getSourceClassId()))
                    .map(UmlRelation::getTargetClassId).findFirst().orElse(null);
            item = parentId == null ? null : classes.get(parentId);
        }
        return result;
    }

    private boolean isOwningSide(UmlClass current, UmlRelation relation, boolean sourceMany, boolean targetMany) {
        boolean source = Objects.equals(current.getId(), relation.getSourceClassId());
        boolean target = Objects.equals(current.getId(), relation.getTargetClassId());
        if (relation.getType() == RelationType.COMPOSITION) {
            // The editor persists 1:1 composition as part(source) -> whole(target).
            // For 1:N, the many end is the part and owns the foreign key.
            String partId = sourceMany != targetMany
                    ? (sourceMany ? relation.getSourceClassId() : relation.getTargetClassId())
                    : relation.getSourceClassId();
            return Objects.equals(current.getId(), partId);
        }
        if (sourceMany != targetMany) {
            return sourceMany ? source : target;
        }
        // Keep the existing deterministic 1:1 convention: target owns the FK.
        return target;
    }

    /**
     * Returns scalar attributes visible on an entity, including inherited
     * attributes in parent-first order. The entity itself still declares only
     * its own attributes; this helper is intentionally for REST DTOs/services.
     */
    private List<UmlAttribute> effectiveAttributes(UmlClass current, UmlDiagram diagram, Map<String, UmlClass> classes) {
        LinkedHashMap<String, UmlAttribute> result = new LinkedHashMap<>();
        collectEffectiveAttributes(current, diagram, classes, new HashSet<>(), result);
        return new ArrayList<>(result.values());
    }

    private void collectEffectiveAttributes(UmlClass current, UmlDiagram diagram, Map<String, UmlClass> classes,
            Set<String> visited, Map<String, UmlAttribute> result) {
        if (current == null || current.getId() == null || !visited.add(current.getId())) return;
        diagram.getRelations().stream()
                .filter(r -> r.getType() == RelationType.INHERITANCE && Objects.equals(current.getId(), r.getSourceClassId()))
                .map(r -> classes.get(r.getTargetClassId()))
                .filter(Objects::nonNull)
                .findFirst()
                .ifPresent(parent -> collectEffectiveAttributes(parent, diagram, classes, visited, result));
        for (UmlAttribute attribute : current.getAttributes()) {
            if (attribute == null || attribute.getName() == null || attribute.getName().isBlank()) continue;
            result.putIfAbsent(attribute.getName().toLowerCase(Locale.ROOT), attribute);
        }
    }

    private boolean representedByAssociationClass(UmlRelation relation, Map<String, UmlRelation> associationClassRelations) {
        return associationClassRelations.values().stream().anyMatch(linked -> Objects.equals(linked.getId(), relation.getId()));
    }

    private String dto(String base, UmlClass umlClass, boolean response, UmlDiagram diagram, Map<String, UmlClass> classes, Map<String, UmlRelation> associationClassRelations) {
        String name = javaName(umlClass.getName()) + (response ? "ResponseDto" : "RequestDto");
        String entityName = javaName(umlClass.getName());
        StringBuilder out = new StringBuilder("package ").append(base).append(".dto;\n\nimport java.util.*;\n\npublic class ").append(name).append(" {\n");
        if (response) out.append("    private ").append(idType(umlClass, classes, diagram)).append(" id;\n");
        for (UmlAttribute attr : effectiveAttributes(umlClass, diagram, classes)) {
            if (attr.getName() == null || attr.getName().equalsIgnoreCase("id")) continue;
            out.append("    private ").append(javaType(attr.getType(), classes)).append(' ').append(javaField(attr.getName())).append(";\n");
        }
        for (RelationSpec spec : relationSpecs(umlClass, diagram, classes, associationClassRelations, !response)) out.append("    private ").append(spec.collection ? "List<" + spec.otherIdType + ">" : spec.otherIdType).append(' ').append(spec.idField).append(spec.collection ? " = new ArrayList<>();\n" : ";\n");
        out.append("\n    public ").append(name).append("() {}\n\n");
        if (response) appendDtoAccessor(out, idType(umlClass, classes, diagram), "id");
        for (UmlAttribute attr : effectiveAttributes(umlClass, diagram, classes)) if (attr.getName() != null && !attr.getName().equalsIgnoreCase("id")) appendDtoAccessor(out, javaType(attr.getType(), classes), javaField(attr.getName()));
        for (RelationSpec spec : relationSpecs(umlClass, diagram, classes, associationClassRelations, !response)) appendDtoAccessor(out, spec.collection ? "List<" + spec.otherIdType + ">" : spec.otherIdType, spec.idField);
        return out.append("}\n").toString();
    }

    private void appendDtoAccessor(StringBuilder out, String type, String field) {
        String suffix = Character.toUpperCase(field.charAt(0)) + field.substring(1);
        out.append("    public ").append(type).append(" get").append(suffix).append("() { return ").append(field).append("; }\n");
        out.append("    public void set").append(suffix).append('(').append(type).append(' ').append(field).append(") { this.").append(field).append(" = ").append(field).append("; }\n");
    }

    private String dtoService(String base, UmlClass umlClass, String idType, UmlDiagram diagram, Map<String, UmlClass> classes, Map<String, UmlRelation> associationClassRelations) {
        String name = javaName(umlClass.getName());
        List<RelationSpec> requestSpecs = relationSpecs(umlClass, diagram, classes, associationClassRelations, true);
        List<RelationSpec> responseSpecs = relationSpecs(umlClass, diagram, classes, associationClassRelations, false);
        StringBuilder out = new StringBuilder("package ").append(base).append(".service;\n\nimport ").append(base).append(".entity.").append(name).append(";\nimport ").append(base).append(".dto.").append(name).append("RequestDto;\nimport ").append(base).append(".dto.").append(name).append("ResponseDto;\nimport ").append(base).append(".repository.").append(name).append("Repository;\n");
        for (RelationSpec spec : requestSpecs) out.append("import ").append(base).append(".entity.").append(spec.otherType).append(";\nimport ").append(base).append(".repository.").append(spec.otherRepository.replace("Repository", "Repository")).append(";\n");
        out.append("import org.springframework.http.HttpStatus;\nimport org.springframework.stereotype.Service;\nimport org.springframework.web.server.ResponseStatusException;\nimport java.util.*;\n\n@Service\npublic class ").append(name).append("Service {\n    private final ").append(name).append("Repository repository;\n");
        for (RelationSpec spec : requestSpecs) out.append("    private final ").append(spec.otherRepository).append(' ').append(javaField(spec.otherType)).append("Repository;\n");
        out.append("    public ").append(name).append("Service(").append(name).append("Repository repository");
        for (RelationSpec spec : requestSpecs) out.append(", ").append(spec.otherRepository).append(' ').append(javaField(spec.otherType)).append("Repository");
        out.append(") { this.repository = repository;");
        for (RelationSpec spec : requestSpecs) out.append(" this.").append(javaField(spec.otherType)).append("Repository = ").append(javaField(spec.otherType)).append("Repository;");
        out.append(" }\n    public List<").append(name).append("ResponseDto> findAll() { return repository.findAll().stream().map(this::toResponse).toList(); }\n    public ").append(name).append("ResponseDto findById(").append(idType).append(" id) { return toResponse(repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND))); }\n    public ").append(name).append("ResponseDto save(").append(name).append("RequestDto value) { return toResponse(repository.save(fromRequest(value, new ").append(name).append("()))); }\n    public ").append(name).append("ResponseDto update(").append(idType).append(" id, ").append(name).append("RequestDto value) { ").append(name).append(" current = repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)); fromRequest(value, current); return toResponse(repository.save(current)); }\n    public void deleteById(").append(idType).append(" id) { if (!repository.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND); repository.deleteById(id); }\n");
        out.append("    private ").append(name).append(" fromRequest(").append(name).append("RequestDto value, ").append(name).append(" entity) {\n");
        for (UmlAttribute attr : effectiveAttributes(umlClass, diagram, classes)) if (attr.getName() != null && !attr.getName().equalsIgnoreCase("id")) out.append("        entity.set").append(cap(javaField(attr.getName()))).append("(value.get").append(cap(javaField(attr.getName()))).append("());\n");
        for (RelationSpec spec : requestSpecs) {
            String repo = javaField(spec.otherType) + "Repository";
            if (spec.collection) out.append("        entity.set").append(cap(spec.field)).append("(value.get").append(cap(spec.idField)).append("() == null ? new ArrayList<>() : new ArrayList<>(value.get").append(cap(spec.idField)).append("().stream().map(id -> ").append(repo).append(".findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, \"Related entity not found\"))).toList()));\n");
            else out.append("        entity.set").append(cap(spec.field)).append("(value.get").append(cap(spec.idField)).append("() == null ? null : ").append(repo).append(".findById(value.get").append(cap(spec.idField)).append("()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, \"Related entity not found\")));\n");
        }
        out.append("        return entity;\n    }\n    private ").append(name).append("ResponseDto toResponse(").append(name).append(" entity) { ").append(name).append("ResponseDto value = new ").append(name).append("ResponseDto(); value.setId(entity.getId());\n");
        for (UmlAttribute attr : effectiveAttributes(umlClass, diagram, classes)) if (attr.getName() != null && !attr.getName().equalsIgnoreCase("id")) out.append("        value.set").append(cap(javaField(attr.getName()))).append("(entity.get").append(cap(javaField(attr.getName()))).append("());\n");
        for (RelationSpec spec : responseSpecs) if (spec.collection) out.append("        value.set").append(cap(spec.idField)).append("(entity.get").append(cap(spec.field)).append("() == null ? new ArrayList<>() : entity.get").append(cap(spec.field)).append("().stream().map(item -> item.getId()).toList());\n"); else out.append("        value.set").append(cap(spec.idField)).append("(entity.get").append(cap(spec.field)).append("() == null ? null : entity.get").append(cap(spec.field)).append("().getId());\n");
        return out.append("        return value; }\n}\n").toString();
    }

    private String dtoController(String base, UmlClass umlClass, String idType) {
        String name = javaName(umlClass.getName());
        return "package " + base + ".controller;\n\n"
                + "import " + base + ".dto." + name + "RequestDto;\n"
                + "import " + base + ".dto." + name + "ResponseDto;\n"
                + "import " + base + ".service." + name + "Service;\n"
                + "import io.swagger.v3.oas.annotations.Operation;\n"
                + "import io.swagger.v3.oas.annotations.tags.Tag;\n"
                + "import org.springframework.web.bind.annotation.*;\nimport java.util.*;\n\n"
                + "@RestController\n@RequestMapping(\"/api/" + plural(name) + "\")\n"
                + "@Tag(name = \"" + name + "\", description = \"CRUD de " + name + "\")\n"
                + "public class " + name + "Controller {\n"
                + "    private final " + name + "Service service;\n"
                + "    public " + name + "Controller(" + name + "Service service) { this.service = service; }\n"
                + "    @Operation(summary = \"Listar " + name + "\")\n    @GetMapping\n    public List<" + name + "ResponseDto> findAll() { return service.findAll(); }\n"
                + "    @Operation(summary = \"Obtener " + name + " por ID\")\n    @GetMapping(\"/{id}\")\n    public " + name + "ResponseDto findById(@PathVariable(\"id\") " + idType + " id) { return service.findById(id); }\n"
                + "    @Operation(summary = \"Crear " + name + "\")\n    @PostMapping\n    public " + name + "ResponseDto save(@RequestBody " + name + "RequestDto value) { return service.save(value); }\n"
                + "    @Operation(summary = \"Actualizar " + name + "\")\n    @PutMapping(\"/{id}\")\n    public " + name + "ResponseDto update(@PathVariable(\"id\") " + idType + " id, @RequestBody " + name + "RequestDto value) { return service.update(id, value); }\n"
                + "    @Operation(summary = \"Eliminar " + name + "\")\n    @DeleteMapping(\"/{id}\")\n    public void delete(@PathVariable(\"id\") " + idType + " id) { service.deleteById(id); }\n"
                + "}\n";
    }
    private String cap(String value) { return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1); }

    private String repository(String base, String name, String idType) { return "package " + base + ".repository;\n\nimport " + base + ".entity." + name + ";\nimport org.springframework.data.jpa.repository.JpaRepository;\n\npublic interface " + name + "Repository extends JpaRepository<" + name + ", " + idType + "> {}\n"; }
    private String service(String base, String name, String idType) { return "package " + base + ".service;\n\nimport " + base + ".entity." + name + ";\nimport " + base + ".repository." + name + "Repository;\nimport org.springframework.http.HttpStatus;\nimport org.springframework.stereotype.Service;\nimport org.springframework.web.server.ResponseStatusException;\nimport java.util.*;\n\n@Service\npublic class " + name + "Service {\n    private final " + name + "Repository repository;\n    public " + name + "Service(" + name + "Repository repository) { this.repository = repository; }\n    public List<" + name + "> findAll() { return repository.findAll(); }\n    public " + name + " findById(" + idType + " id) { return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)); }\n    public " + name + " save(" + name + " value) { return repository.save(value); }\n    public " + name + " update(" + idType + " id, " + name + " value) { return repository.findById(id).map(current -> { value.setId(id); return repository.save(value); }).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)); }\n    public void deleteById(" + idType + " id) { if (!repository.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND); repository.deleteById(id); }\n}\n"; }
    private String controller(String base, String name, String idType) {
        return "package " + base + ".controller;\n\n"
                + "import " + base + ".entity." + name + ";\nimport " + base + ".service." + name + "Service;\n"
                + "import io.swagger.v3.oas.annotations.Operation;\nimport io.swagger.v3.oas.annotations.tags.Tag;\n"
                + "import org.springframework.web.bind.annotation.*;\nimport java.util.*;\n\n"
                + "@RestController\n@RequestMapping(\"/api/" + plural(name) + "\")\n"
                + "@Tag(name = \"" + name + "\", description = \"CRUD de " + name + "\")\n"
                + "public class " + name + "Controller {\n"
                + "    private final " + name + "Service service;\n"
                + "    public " + name + "Controller(" + name + "Service service) { this.service = service; }\n"
                + "    @Operation(summary = \"Listar " + name + "\")\n    @GetMapping\n    public List<" + name + "> findAll() { return service.findAll(); }\n"
                + "    @Operation(summary = \"Obtener " + name + " por ID\")\n    @GetMapping(\"/{id}\")\n    public " + name + " findById(@PathVariable(\"id\") " + idType + " id) { return service.findById(id); }\n"
                + "    @Operation(summary = \"Crear " + name + "\")\n    @PostMapping\n    public " + name + " save(@RequestBody " + name + " value) { return service.save(value); }\n"
                + "    @Operation(summary = \"Actualizar " + name + "\")\n    @PutMapping(\"/{id}\")\n    public " + name + " update(@PathVariable(\"id\") " + idType + " id, @RequestBody " + name + " value) { return service.update(id, value); }\n"
                + "    @Operation(summary = \"Eliminar " + name + "\")\n    @DeleteMapping(\"/{id}\")\n    public void delete(@PathVariable(\"id\") " + idType + " id) { service.deleteById(id); }\n"
                + "}\n";
    }
    private String application(String base, String project) { return "package " + base + ";\n\nimport org.springframework.boot.SpringApplication;\nimport org.springframework.boot.autoconfigure.SpringBootApplication;\n\n@SpringBootApplication\npublic class GeneratedApplication { public static void main(String[] args) { SpringApplication.run(GeneratedApplication.class, args); } }\n"; }
    private String openApiConfig(String base, String project) {
        return "package " + base + ".config;\n\n"
                + "import io.swagger.v3.oas.annotations.OpenAPIDefinition;\n"
                + "import io.swagger.v3.oas.annotations.info.Info;\n"
                + "import org.springframework.context.annotation.Configuration;\n\n"
                + "@Configuration\n@OpenAPIDefinition(info = @Info(title = \"" + javaString(project) + " API\", version = \"1.0.0\", description = \"Backend generated from UML model by SW1 UML CASE Tool\"))\n"
                + "public class OpenApiConfig {}\n";
    }
    private String pom(String group, String artifact, String project) { return "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><modelVersion>4.0.0</modelVersion><parent><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-parent</artifactId><version>3.4.5</version><relativePath/></parent><groupId>" + xml(group) + "</groupId><artifactId>" + xml(artifact) + "</artifactId><version>0.0.1-SNAPSHOT</version><name>" + xml(project) + "</name><properties><java.version>21</java.version></properties><dependencies><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency><dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency><dependency><groupId>org.springdoc</groupId><artifactId>springdoc-openapi-starter-webmvc-ui</artifactId><version>2.8.9</version></dependency><dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency></dependencies><build><plugins><plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin></plugins></build></project>\n"; }
    private String properties() { return "spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/generated_db}\nspring.datasource.username=${DB_USERNAME:postgres}\nspring.datasource.password=${DB_PASSWORD:postgres}\nspring.jpa.hibernate.ddl-auto=update\n"; }
    private String readme(String project, String artifact, UmlDiagram diagram, Map<String, UmlClass> classes) { return "# " + project + "\n\nBackend Spring Boot generado desde el modelo UML canónico. Requiere Java 21, Maven y PostgreSQL.\n\n## Ejecución\n\nEjecuta `mvn spring-boot:run` o `mvn package` y luego `java -jar target/*.jar`. Configura `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` si PostgreSQL no usa los valores predeterminados.\n\n## API\n\n- Swagger UI: http://localhost:8080/swagger-ui.html\n- OpenAPI JSON: http://localhost:8080/v3/api-docs\n- Colección Postman: `postman/" + safeArtifact(project, artifact) + ".postman_collection.json`\n\nLa colección utiliza la variable `baseUrl` con valor `http://localhost:8080`. Entidades generadas: " + classes.values().stream().map(UmlClass::getName).toList() + ".\n"; }

    private String postmanCollection(String project, UmlDiagram diagram, List<UmlClass> documentedClasses,
            Map<String, UmlClass> classes, Map<String, UmlRelation> associationClassRelations) {
        StringBuilder out = new StringBuilder("{\"info\":{\"name\":").append(json(project))
                .append(",\"schema\":\"https://schema.getpostman.com/json/collection/v2.1.0/collection.json\"},")
                .append("\"variable\":[{\"key\":\"baseUrl\",\"value\":\"http://localhost:8080\"}],\"item\":[");
        for (int index = 0; index < documentedClasses.size(); index++) {
            UmlClass item = documentedClasses.get(index);
            String name = javaName(item.getName());
            if (index > 0) out.append(',');
            out.append("{\"name\":").append(json(name)).append(",\"item\":[");
            String path = "/api/" + plural(name);
            out.append(postmanRequest("GET all", "GET", path, null)).append(',')
                    .append(postmanRequest("GET by ID", "GET", path + "/1", null)).append(',')
                    .append(postmanRequest("POST", "POST", path, requestExample(item, diagram, classes, associationClassRelations))).append(',')
                    .append(postmanRequest("PUT", "PUT", path + "/1", requestExample(item, diagram, classes, associationClassRelations))).append(',')
                    .append(postmanRequest("DELETE", "DELETE", path + "/1", null))
                    .append("]}");
        }
        return out.append("]}\n").toString();
    }

    private String postmanRequest(String name, String method, String path, String body) {
        StringBuilder out = new StringBuilder("{\"name\":").append(json(name)).append(",\"request\":{\"method\":").append(json(method))
                .append(",\"header\":[],\"url\":\"{{baseUrl}}").append(path).append("\"");
        if (body != null) out.append(",\"body\":{\"mode\":\"raw\",\"raw\":").append(json(body)).append(",\"options\":{\"raw\":{\"language\":\"json\"}}}");
        return out.append("}}").toString();
    }

    private String requestExample(UmlClass umlClass, UmlDiagram diagram, Map<String, UmlClass> classes,
            Map<String, UmlRelation> associationClassRelations) {
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        if (classes.containsKey(umlClass.getId())) {
            for (UmlAttribute attr : effectiveAttributes(umlClass, diagram, classes)) {
                if (attr.getName() == null || attr.getName().equalsIgnoreCase("id")) continue;
                if (!first) out.append(',');
                first = false;
                out.append(json(javaField(attr.getName()))).append(':').append(exampleValue(javaType(attr.getType(), classes)));
            }
            for (RelationSpec spec : relationSpecs(umlClass, diagram, classes, associationClassRelations, true)) {
                if (!first) out.append(',');
                first = false;
                out.append(json(spec.idField())).append(':').append(spec.collection() ? "[1]" : "1");
            }
        }
        return out.append('}').toString();
    }

    private String exampleValue(String type) {
        return switch (type) {
            case "Integer", "Long" -> "1";
            case "Double", "Float", "BigDecimal" -> "1.0";
            case "Boolean" -> "true";
            case "LocalDate" -> "\"2026-01-01\"";
            case "LocalDateTime" -> "\"2026-01-01T10:00:00\"";
            default -> "\"example\"";
        };
    }

    private String json(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n") + "\"";
    }

    private String javaString(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
    }

    private Map<String, UmlRelation> associationClassRelations(UmlDiagram diagram) { Map<String, UmlRelation> result = new HashMap<>(); if (diagram.getAssociationClassLinks() != null) for (AssociationClassLink link : diagram.getAssociationClassLinks()) diagram.getRelations().stream().filter(r -> Objects.equals(r.getId(), link.getRelationId())).findFirst().ifPresent(r -> result.put(link.getClassId(), r)); return result; }
    private List<UmlClass> bridgeClasses(UmlDiagram diagram, Map<String, UmlClass> classes, Map<String, UmlRelation> ac) { List<UmlClass> result = new ArrayList<>(); for (UmlRelation r : diagram.getRelations()) if (r.getType() != RelationType.DEPENDENCY && many(r.getSourceMultiplicity()) && many(r.getTargetMultiplicity()) && ac.values().stream().noneMatch(r::equals)) result.add(UmlClass.builder().id("bridge-" + r.getId()).name(javaName(classes.get(r.getSourceClassId()).getName()) + javaName(classes.get(r.getTargetClassId()).getName())).build()); return result; }
    private String inheritanceParent(UmlClass child, UmlDiagram diagram, Map<String, UmlClass> classes) { return diagram.getRelations().stream().filter(r -> r.getType() == RelationType.INHERITANCE && child.getId().equals(r.getSourceClassId())).map(r -> classes.get(r.getTargetClassId())).filter(Objects::nonNull).map(c -> javaName(c.getName())).findFirst().orElse(null); }
    private String idType(UmlClass c, Map<String, UmlClass> classes, UmlDiagram diagram) {
        Set<String> visited = new HashSet<>();
        UmlClass current = c;
        while (current != null && visited.add(current.getId())) {
            String ownType = current.getAttributes().stream()
                    .filter(a -> a.getName() != null && a.getName().equalsIgnoreCase("id"))
                    .map(a -> javaType(a.getType(), classes)).findFirst().orElse(null);
            if (ownType != null) return ownType;
            String currentId = current.getId();
            String parentId = diagram.getRelations().stream()
                    .filter(r -> r.getType() == RelationType.INHERITANCE && currentId.equals(r.getSourceClassId()))
                    .map(UmlRelation::getTargetClassId).findFirst().orElse(null);
            current = parentId == null ? null : classes.get(parentId);
        }
        return "Long";
    }
    private boolean isInheritanceRoot(UmlClass candidate, UmlDiagram diagram) {
        boolean parent = diagram.getRelations().stream().anyMatch(r -> r.getType() == RelationType.INHERITANCE && candidate.getId().equals(r.getTargetClassId()));
        boolean child = diagram.getRelations().stream().anyMatch(r -> r.getType() == RelationType.INHERITANCE && candidate.getId().equals(r.getSourceClassId()));
        return parent && !child;
    }
    private String javaType(String type, Map<String, UmlClass> classes) { if (type == null || type.isBlank()) return "String"; String t = type.trim(); for (UmlClass c : classes.values()) if (c.getName().equalsIgnoreCase(t)) return javaName(c.getName()); return switch (t.toLowerCase(Locale.ROOT)) { case "string" -> "String"; case "integer", "int" -> "Integer"; case "long" -> "Long"; case "boolean" -> "Boolean"; case "double" -> "Double"; case "float" -> "Float"; case "date", "localdate" -> "LocalDate"; case "datetime", "localdatetime" -> "LocalDateTime"; case "bigdecimal" -> "BigDecimal"; case "uuid" -> "UUID"; default -> "String"; }; }
    private boolean many(Multiplicity m) { return m != null && ("*".equals(m.getUpper()) || (m.getUpper() != null && m.getUpper().contains("*"))); }
    private boolean required(Multiplicity m) { return m != null && "1".equals(m.getLower()); }
    private String defaultReturn(String type) { return "boolean".equals(type) || "Boolean".equals(type) ? "false" : "int".equals(type) || "Integer".equals(type) || "long".equals(type) || "Long".equals(type) || "double".equals(type) || "Double".equals(type) ? "0" : "void".equals(type) ? "null" : "null"; }
    private String methodBody(String type) { return "void".equals(type) ? "        return;\n" : "        return " + defaultReturn(type) + ";\n"; }
    private String defaultValue(UmlAttribute a) { return a.getDefaultValue() == null || a.getDefaultValue().isBlank() ? "" : " = " + a.getDefaultValue(); }
    private String javaName(String value) { if (value == null || value.isBlank()) return "Unnamed"; StringBuilder result = new StringBuilder(); for (String part : value.replaceAll("[^A-Za-z0-9_$]+", " ").trim().split(" +")) if (!part.isBlank()) result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)); return result.length() == 0 ? "Unnamed" : result.toString(); }
    private String javaField(String value) { String n = javaName(value); return Character.toLowerCase(n.charAt(0)) + n.substring(1); }
    private String plural(String value) { String n = javaField(value); return n.endsWith("s") ? n : n + "s"; }
    private String sqlName(String value) { return javaField(value).replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT); }
    private String safeArtifact(String requested, String fallback) { String source = requested == null || requested.isBlank() ? fallback : requested; String result = source == null ? "generated-backend" : source.replaceAll("[^A-Za-z0-9_-]", "-").replaceAll("-+", "-"); return result.isBlank() ? "generated-backend" : result.toLowerCase(Locale.ROOT); }
    private String xml(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
    private void put(ZipOutputStream zip, String name, String content) throws Exception { zip.putNextEntry(new ZipEntry(name)); zip.write(content.getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); }
}
