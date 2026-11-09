package com.sw1.umltool.features.diagram.history.service;

import com.sw1.umltool.features.diagram.model.canonical.UmlAttribute;
import com.sw1.umltool.features.diagram.model.canonical.UmlClass;
import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.canonical.UmlMethod;
import com.sw1.umltool.features.diagram.service.DiagramStateSerializer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DiagramHistoryDescriptionService {
    private final DiagramStateSerializer serializer;

    public DiagramHistoryDescriptionService(DiagramStateSerializer serializer) {
        this.serializer = serializer;
    }

    public Description describe(String beforeJson, String afterJson, String operationType) {
        try {
            return describe(serializer.deserializeCanonical(beforeJson), serializer.deserializeCanonical(afterJson), operationType);
        } catch (RuntimeException ignored) {
            return new Description("Se modificó el diagrama.", List.of());
        }
    }

    private Description describe(UmlDiagram before, UmlDiagram after, String operationType) {
        List<String> details = new ArrayList<>();
        List<UmlClass> addedClasses = difference(before.getClasses(), after.getClasses(), UmlClass::getId);
        List<UmlClass> removedClasses = difference(after.getClasses(), before.getClasses(), UmlClass::getId);
        addedClasses.forEach(item -> details.add("Se creó la clase " + item.getName() + "."));
        removedClasses.forEach(item -> details.add("Se eliminó la clase " + item.getName() + "."));

        Map<String, UmlClass> oldClasses = byId(before.getClasses(), UmlClass::getId);
        Map<String, UmlClass> newClasses = byId(after.getClasses(), UmlClass::getId);
        for (String id : intersection(oldClasses.keySet(), newClasses.keySet())) {
            UmlClass oldClass = oldClasses.get(id);
            UmlClass newClass = newClasses.get(id);
            describeAttributes(oldClass, newClass, details);
            describeMethods(oldClass, newClass, details);
            if (!Objects.equals(oldClass.getName(), newClass.getName())) {
                details.add("Se renombró " + oldClass.getName() + " a " + newClass.getName() + ".");
            }
        }
        if (!Objects.equals(before.getRelations(), after.getRelations())) details.add("Se modificó una relación entre clases.");
        if (details.isEmpty()) return new Description("Se modificó el diagrama.", List.of());
        return new Description(operationType != null && operationType.startsWith("BATCH:")
                ? "Se aplicaron " + details.size() + " cambios." : details.get(0), details);
    }

    private void describeAttributes(UmlClass before, UmlClass after, List<String> details) {
        Map<String, UmlAttribute> oldAttributes = byId(before.getAttributes(), UmlAttribute::getId);
        Map<String, UmlAttribute> newAttributes = byId(after.getAttributes(), UmlAttribute::getId);
        difference(before.getAttributes(), after.getAttributes(), UmlAttribute::getId).forEach(item ->
                details.add("Se eliminó el atributo " + item.getName() + ": " + item.getType() + " de " + before.getName() + "."));
        difference(after.getAttributes(), before.getAttributes(), UmlAttribute::getId).forEach(item ->
                details.add("Se agregó el atributo " + item.getName() + ": " + item.getType() + " a " + after.getName() + "."));
        for (String id : intersection(oldAttributes.keySet(), newAttributes.keySet())) {
            UmlAttribute oldAttribute = oldAttributes.get(id);
            UmlAttribute newAttribute = newAttributes.get(id);
            if (!Objects.equals(oldAttribute.getName(), newAttribute.getName())) {
                details.add("Se renombró el atributo " + oldAttribute.getName() + " a " + newAttribute.getName() + " en " + after.getName() + ".");
            }
            if (!Objects.equals(oldAttribute.getType(), newAttribute.getType())) {
                details.add("Se cambió el tipo de " + newAttribute.getName() + " de " + oldAttribute.getType() + " a " + newAttribute.getType() + " en " + after.getName() + ".");
            }
        }
    }

    private void describeMethods(UmlClass before, UmlClass after, List<String> details) {
        Map<String, UmlMethod> oldMethods = byId(before.getMethods(), UmlMethod::getId);
        Map<String, UmlMethod> newMethods = byId(after.getMethods(), UmlMethod::getId);
        difference(before.getMethods(), after.getMethods(), UmlMethod::getId).forEach(item ->
                details.add("Se eliminó el método " + item.getName() + "() de " + before.getName() + "."));
        difference(after.getMethods(), before.getMethods(), UmlMethod::getId).forEach(item ->
                details.add("Se agregó el método " + item.getName() + "() a " + after.getName() + "."));
        for (String id : intersection(oldMethods.keySet(), newMethods.keySet())) {
            UmlMethod oldMethod = oldMethods.get(id);
            UmlMethod newMethod = newMethods.get(id);
            if (!Objects.equals(oldMethod.getName(), newMethod.getName())) {
                details.add("Se renombró el método " + oldMethod.getName() + "() a " + newMethod.getName() + "() en " + after.getName() + ".");
            }
            if (!Objects.equals(oldMethod.getReturnType(), newMethod.getReturnType())) {
                details.add("Se cambió el retorno de " + newMethod.getName() + "() de " + oldMethod.getReturnType() + " a " + newMethod.getReturnType() + " en " + after.getName() + ".");
            }
        }
    }

    private <T> List<T> difference(List<T> left, List<T> right, Function<T, String> key) {
        Set<String> rightIds = right.stream().map(key).collect(Collectors.toSet());
        return left.stream().filter(item -> !rightIds.contains(key.apply(item))).toList();
    }

    private <T> Map<String, T> byId(List<T> values, Function<T, String> key) {
        Map<String, T> result = new HashMap<>();
        values.forEach(item -> result.put(key.apply(item), item));
        return result;
    }

    private Set<String> intersection(Set<String> left, Set<String> right) {
        return left.stream().filter(right::contains).collect(Collectors.toSet());
    }

    public record Description(String summary, List<String> details) { }
}
