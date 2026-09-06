package com.sw1.umltool.features.importexport.mapper;

import com.sw1.umltool.features.diagram.model.canonical.*;
import com.sw1.umltool.features.diagram.model.canonical.enums.RelationType;
import com.sw1.umltool.features.diagram.model.canonical.enums.Visibility;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;
import com.sw1.umltool.features.diagram.model.view.NodeViewState;
import com.sw1.umltool.features.importexport.dto.AiUmlDetectionResponse;
import com.sw1.umltool.features.importexport.dto.ImageImportPreviewResponse;
import com.sw1.umltool.features.importexport.dto.XmiImportPreviewResponse;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ImageUmlCanonicalMapper {
    public ImageImportPreviewResponse map(AiUmlDetectionResponse detection, String diagramId, String diagramName) {
        if (detection == null || !detection.success() || detection.diagram() == null) {
            throw new IllegalArgumentException("No pudimos reconocer un diagrama UML en esta imagen.");
        }
        List<String> warnings = new ArrayList<>(safe(detection.warnings()));
        Map<String, String> classIds = new LinkedHashMap<>();
        List<UmlClass> classes = new ArrayList<>();
        for (var item : safe(detection.diagram().classes())) {
            String name = required(item.name(), "La IA detectó una clase sin nombre.");
            String key = name.toLowerCase(Locale.ROOT);
            if (classIds.containsKey(key)) throw new IllegalArgumentException("La imagen contiene clases duplicadas: " + name);
            String id = UUID.randomUUID().toString();
            classIds.put(key, id);
            if (hasText(item.ref())) classIds.put("ref:" + item.ref(), id);
            classes.add(UmlClass.builder().id(id).name(name).isAbstract(Boolean.TRUE.equals(item.abstractClass()))
                    .attributes(mapAttributes(item.attributes(), warnings)).methods(mapMethods(item.methods(), warnings)).build());
        }
        if (classes.isEmpty()) throw new IllegalArgumentException("No pudimos reconocer clases UML en esta imagen.");

        Map<String, String> relationIds = new HashMap<>();
        List<UmlRelation> relations = new ArrayList<>();
        for (var item : safe(detection.diagram().relations())) {
            String id = UUID.randomUUID().toString();
            String sourceId = classId(classIds, item.sourceClassRef(), item.sourceClassName());
            String targetId = classId(classIds, item.targetClassRef(), item.targetClassName());
            if (sourceId == null || targetId == null) throw new IllegalArgumentException("Una relación detectada referencia una clase inexistente.");
            RelationType type;
            try { type = RelationType.valueOf(required(item.type(), "Una relación detectada no tiene tipo.").toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Tipo de relación UML no soportado: " + item.type()); }
            relations.add(UmlRelation.builder().id(id).sourceClassId(sourceId).targetClassId(targetId).type(type)
                    .sourceMultiplicity(multiplicity(item.sourceMultiplicity(), warnings))
                    .targetMultiplicity(multiplicity(item.targetMultiplicity(), warnings))
                    .sourceRole(blankToNull(item.sourceRole())).targetRole(blankToNull(item.targetRole()))
                    .sourceNavigable(Boolean.TRUE.equals(item.sourceNavigable())).targetNavigable(Boolean.TRUE.equals(item.targetNavigable())).build());
            if (hasText(item.ref())) relationIds.put(item.ref(), id);
        }

        List<AssociationClassLink> links = new ArrayList<>();
        for (var item : safe(detection.diagram().associationClasses())) {
            String relationId = relationIds.get(item.relationRef());
            String classId = classId(classIds, item.classRef(), item.className());
            if (relationId == null || classId == null) {
                warnings.add("No se pudo resolver una clase de asociación detectada.");
                continue;
            }
            links.add(AssociationClassLink.builder().id(UUID.randomUUID().toString()).relationId(relationId).classId(classId).build());
        }
        UmlDiagram diagram = UmlDiagram.builder().id(diagramId).name(diagramName).version(0)
                .classes(classes).relations(relations).associationClassLinks(links).build();
        List<NodeViewState> nodes = new ArrayList<>();
        for (int index = 0; index < classes.size(); index++) {
            nodes.add(NodeViewState.builder().classId(classes.get(index).getId()).x(100 + (index % 4) * 320)
                    .y(100 + (index / 4) * 240).width(240).height(180).build());
        }
        DiagramViewState view = DiagramViewState.builder().diagramId(diagramId).nodes(nodes).build();
        int attributes = classes.stream().mapToInt(c -> c.getAttributes().size()).sum();
        int methods = classes.stream().mapToInt(c -> c.getMethods().size()).sum();
        return ImageImportPreviewResponse.builder().canonicalModel(diagram).viewState(view).warnings(warnings)
                .statistics(XmiImportPreviewResponse.Statistics.builder().classes(classes.size()).attributes(attributes)
                        .methods(methods).relations(relations.size()).associationClasses(links.size()).build())
                .confidence(detection.confidence()).detectedClassNames(classes.stream().map(UmlClass::getName).toList()).build();
    }

    private List<UmlAttribute> mapAttributes(List<AiUmlDetectionResponse.AttributeDetection> items, List<String> warnings) {
        List<UmlAttribute> result = new ArrayList<>();
        for (var item : safe(items)) {
            if (!hasText(item.name())) { warnings.add("Se omitió un atributo ilegible."); continue; }
            String type = typeOrObject(item.type(), "atributo " + item.name(), warnings);
            result.add(UmlAttribute.builder().id(UUID.randomUUID().toString()).name(item.name().trim()).type(type)
                    .visibility(visibility(item.visibility(), Visibility.PRIVATE)).isStatic(Boolean.TRUE.equals(item.staticAttribute()))
                    .isFinal(Boolean.TRUE.equals(item.finalAttribute())).defaultValue(blankToNull(item.defaultValue()))
                    .primaryKey(Boolean.TRUE.equals(item.primaryKey())).build());
        }
        return result;
    }

    private List<UmlMethod> mapMethods(List<AiUmlDetectionResponse.MethodDetection> items, List<String> warnings) {
        List<UmlMethod> result = new ArrayList<>();
        for (var item : safe(items)) {
            if (!hasText(item.name())) { warnings.add("Se omitió un método ilegible."); continue; }
            List<UmlParameter> parameters = new ArrayList<>();
            for (var parameter : safe(item.parameters())) {
                if (!hasText(parameter.name())) { warnings.add("Se omitió un parámetro ilegible."); continue; }
                parameters.add(UmlParameter.builder().id(UUID.randomUUID().toString()).name(parameter.name().trim())
                        .type(typeOrObject(parameter.type(), "parámetro " + parameter.name(), warnings)).build());
            }
            result.add(UmlMethod.builder().id(UUID.randomUUID().toString()).name(item.name().trim())
                    .returnType(hasText(item.returnType()) ? item.returnType().trim() : "void")
                    .visibility(visibility(item.visibility(), Visibility.PUBLIC)).isStatic(Boolean.TRUE.equals(item.staticMethod()))
                    .parameters(parameters).build());
        }
        return result;
    }

    private Multiplicity multiplicity(String value, List<String> warnings) {
        if (!hasText(value)) {
            warnings.add("No se pudo determinar una multiplicidad visible; se usÃ³ 1..1.");
            return new Multiplicity("1", "1");
        }
        String normalized = value.trim().replace(" ", "");
        if ("*".equals(normalized)) return new Multiplicity("0", "*");
        String[] parts = normalized.split("\\.\\.", -1);
        if (parts.length == 1 && numeric(parts[0])) return new Multiplicity(parts[0], parts[0]);
        if (parts.length == 2 && numeric(parts[0]) && (numeric(parts[1]) || "*".equals(parts[1]))) return new Multiplicity(parts[0], parts[1]);
        warnings.add("Multiplicidad ambigua '" + value + "'; se usó 1.");
        return new Multiplicity("1", "1");
    }

    private Visibility visibility(String value, Visibility fallback) {
        if (!hasText(value)) return fallback;
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "+", "PUBLIC" -> Visibility.PUBLIC; case "-", "PRIVATE" -> Visibility.PRIVATE;
            case "#", "PROTECTED" -> Visibility.PROTECTED; case "~", "PACKAGE" -> Visibility.PACKAGE; default -> fallback;
        };
    }
    private String classId(Map<String, String> ids, String ref, String name) {
        if (hasText(ref) && ids.containsKey("ref:" + ref)) return ids.get("ref:" + ref);
        return hasText(name) ? ids.get(name.trim().toLowerCase(Locale.ROOT)) : null;
    }
    private String typeOrObject(String value, String element, List<String> warnings) {
        if (hasText(value)) return value.trim(); warnings.add("No se pudo leer el tipo de " + element + "; se usó Object."); return "Object";
    }
    private String required(String value, String message) { if (!hasText(value)) throw new IllegalArgumentException(message); return value.trim(); }
    private boolean numeric(String value) { try { return Integer.parseInt(value) >= 0; } catch (Exception ignored) { return false; } }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private String blankToNull(String value) { return hasText(value) ? value.trim() : null; }
    private <T> List<T> safe(List<T> values) { return values == null ? List.of() : values; }
}
