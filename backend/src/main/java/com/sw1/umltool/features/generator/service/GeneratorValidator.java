package com.sw1.umltool.features.generator.service;

import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.canonical.UmlClass;
import com.sw1.umltool.features.diagram.model.canonical.UmlAttribute;
import com.sw1.umltool.features.diagram.validation.CanonicalModelValidator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class GeneratorValidator {
    private static final Set<String> JAVA_KEYWORDS = Set.of("class", "interface", "enum", "public", "private", "protected", "static", "final", "void", "int", "long", "boolean", "new", "return", "package", "import", "extends", "implements", "this");
    private final CanonicalModelValidator canonicalValidator;

    public GeneratorValidator(CanonicalModelValidator canonicalValidator) { this.canonicalValidator = canonicalValidator; }

    public List<String> validate(UmlDiagram diagram) {
        List<String> errors = new ArrayList<>();
        var result = canonicalValidator.validate(diagram);
        result.getErrors().forEach(error -> errors.add(error.getMessage()));
        if (diagram == null) return errors;
        Set<String> names = new HashSet<>();
        for (UmlClass umlClass : diagram.getClasses()) {
            if (umlClass == null || blank(umlClass.getName())) continue;
            if (!names.add(umlClass.getName().toLowerCase())) errors.add("Clases duplicadas: " + umlClass.getName());
            if (!validIdentifier(umlClass.getName())) errors.add("Nombre de clase no válido para Java: " + umlClass.getName());
            Set<String> attributes = new HashSet<>();
            for (UmlAttribute attribute : umlClass.getAttributes()) {
                if (attribute != null && !blank(attribute.getName()) && !attributes.add(attribute.getName().toLowerCase())) errors.add("Atributos duplicados en " + umlClass.getName() + ": " + attribute.getName());
            }
        }
        return errors;
    }

    public boolean validIdentifier(String value) { return value != null && value.matches("[A-Za-z_$][A-Za-z0-9_$]*") && !JAVA_KEYWORDS.contains(value); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}
