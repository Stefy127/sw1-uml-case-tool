package com.sw1.umltool.features.diagram.validation;

import com.sw1.umltool.features.diagram.model.canonical.Multiplicity;
import com.sw1.umltool.features.diagram.model.canonical.UmlAttribute;
import com.sw1.umltool.features.diagram.model.canonical.UmlClass;
import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.canonical.UmlMethod;
import com.sw1.umltool.features.diagram.model.canonical.UmlParameter;
import com.sw1.umltool.features.diagram.model.canonical.UmlRelation;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class CanonicalModelValidator {

    public ValidationResult validate(UmlDiagram diagram) {

        ValidationResult result = new ValidationResult();

        if (diagram == null) {
            result.addError(
                    "DIAGRAM_REQUIRED",
                    "El diagrama no puede ser nulo",
                    null
            );

            return result;
        }

        validateDiagram(diagram, result);

        return result;
    }

    private void validateDiagram(
            UmlDiagram diagram,
            ValidationResult result
    ) {

        if (isBlank(diagram.getId())) {
            result.addError(
                    "DIAGRAM_ID_REQUIRED",
                    "El diagrama debe tener un identificador",
                    null
            );
        }

        if (isBlank(diagram.getName())) {
            result.addError(
                    "DIAGRAM_NAME_REQUIRED",
                    "El diagrama debe tener un nombre",
                    diagram.getId()
            );
        }

        validateClasses(diagram.getClasses(), result);

        validateRelations(
                diagram.getRelations(),
                diagram.getClasses(),
                result
        );
    }

    private void validateClasses(
            List<UmlClass> classes,
            ValidationResult result
    ) {

        if (classes == null) {
            return;
        }

        Set<String> classIds = new HashSet<>();
        Set<String> classNames = new HashSet<>();

        for (UmlClass umlClass : classes) {

            if (umlClass == null) {
                result.addError(
                        "NULL_CLASS",
                        "El diagrama contiene una clase nula",
                        null
                );
                continue;
            }

            if (isBlank(umlClass.getId())) {

                result.addError(
                        "CLASS_ID_REQUIRED",
                        "Toda clase debe tener un identificador",
                        null
                );

            } else if (!classIds.add(umlClass.getId())) {

                result.addError(
                        "DUPLICATE_CLASS_ID",
                        "Existe más de una clase con el mismo identificador",
                        umlClass.getId()
                );
            }

            if (isBlank(umlClass.getName())) {

                result.addError(
                        "CLASS_NAME_REQUIRED",
                        "Toda clase debe tener un nombre",
                        umlClass.getId()
                );

            } else {

                String normalizedName =
                        umlClass.getName().trim().toLowerCase();

                if (!classNames.add(normalizedName)) {

                    result.addError(
                            "DUPLICATE_CLASS_NAME",
                            "No pueden existir dos clases con el mismo nombre",
                            umlClass.getId()
                    );
                }
            }

            validateAttributes(umlClass, result);
            validateMethods(umlClass, result);
        }
    }

    private void validateAttributes(
            UmlClass umlClass,
            ValidationResult result
    ) {

        if (umlClass.getAttributes() == null) {
            return;
        }

        Set<String> attributeIds = new HashSet<>();
        Set<String> attributeNames = new HashSet<>();

        for (UmlAttribute attribute : umlClass.getAttributes()) {

            if (attribute == null) {
                result.addError(
                        "NULL_ATTRIBUTE",
                        "La clase contiene un atributo nulo",
                        umlClass.getId()
                );
                continue;
            }

            if (isBlank(attribute.getId())) {

                result.addError(
                        "ATTRIBUTE_ID_REQUIRED",
                        "Todo atributo debe tener un identificador",
                        umlClass.getId()
                );

            } else if (!attributeIds.add(attribute.getId())) {

                result.addError(
                        "DUPLICATE_ATTRIBUTE_ID",
                        "Existe más de un atributo con el mismo identificador",
                        attribute.getId()
                );
            }

            if (isBlank(attribute.getName())) {

                result.addError(
                        "ATTRIBUTE_NAME_REQUIRED",
                        "Todo atributo debe tener un nombre",
                        attribute.getId()
                );

            } else {

                String normalizedName =
                        attribute.getName().trim().toLowerCase();

                if (!attributeNames.add(normalizedName)) {

                    result.addError(
                            "DUPLICATE_ATTRIBUTE_NAME",
                            "Una clase no puede tener dos atributos con el mismo nombre",
                            attribute.getId()
                    );
                }
            }

            if (isBlank(attribute.getType())) {

                result.addError(
                        "ATTRIBUTE_TYPE_REQUIRED",
                        "Todo atributo debe tener un tipo",
                        attribute.getId()
                );
            }
        }
    }

    private void validateMethods(
            UmlClass umlClass,
            ValidationResult result
    ) {

        if (umlClass.getMethods() == null) {
            return;
        }

        Set<String> methodIds = new HashSet<>();

        for (UmlMethod method : umlClass.getMethods()) {

            if (method == null) {
                result.addError(
                        "NULL_METHOD",
                        "La clase contiene un método nulo",
                        umlClass.getId()
                );
                continue;
            }

            if (isBlank(method.getId())) {

                result.addError(
                        "METHOD_ID_REQUIRED",
                        "Todo método debe tener un identificador",
                        umlClass.getId()
                );

            } else if (!methodIds.add(method.getId())) {

                result.addError(
                        "DUPLICATE_METHOD_ID",
                        "Existe más de un método con el mismo identificador",
                        method.getId()
                );
            }

            if (isBlank(method.getName())) {

                result.addError(
                        "METHOD_NAME_REQUIRED",
                        "Todo método debe tener un nombre",
                        method.getId()
                );
            }

            validateParameters(method, result);
        }
    }

    private void validateParameters(
            UmlMethod method,
            ValidationResult result
    ) {

        if (method.getParameters() == null) {
            return;
        }

        Set<String> parameterIds = new HashSet<>();
        Set<String> parameterNames = new HashSet<>();

        for (UmlParameter parameter : method.getParameters()) {

            if (parameter == null) {
                result.addError(
                        "NULL_PARAMETER",
                        "El método contiene un parámetro nulo",
                        method.getId()
                );
                continue;
            }

            if (isBlank(parameter.getId())) {

                result.addError(
                        "PARAMETER_ID_REQUIRED",
                        "Todo parámetro debe tener un identificador",
                        method.getId()
                );

            } else if (!parameterIds.add(parameter.getId())) {

                result.addError(
                        "DUPLICATE_PARAMETER_ID",
                        "Existe más de un parámetro con el mismo identificador",
                        parameter.getId()
                );
            }

            if (isBlank(parameter.getName())) {

                result.addError(
                        "PARAMETER_NAME_REQUIRED",
                        "Todo parámetro debe tener un nombre",
                        parameter.getId()
                );

            } else {

                String normalizedName =
                        parameter.getName().trim().toLowerCase();

                if (!parameterNames.add(normalizedName)) {

                    result.addError(
                            "DUPLICATE_PARAMETER_NAME",
                            "Un método no puede tener dos parámetros con el mismo nombre",
                            parameter.getId()
                    );
                }
            }

            if (isBlank(parameter.getType())) {

                result.addError(
                        "PARAMETER_TYPE_REQUIRED",
                        "Todo parámetro debe tener un tipo",
                        parameter.getId()
                );
            }
        }
    }

    private void validateRelations(
            List<UmlRelation> relations,
            List<UmlClass> classes,
            ValidationResult result
    ) {

        if (relations == null) {
            return;
        }

        Set<String> validClassIds = new HashSet<>();

        if (classes != null) {
            for (UmlClass umlClass : classes) {

                if (umlClass != null &&
                        !isBlank(umlClass.getId())) {

                    validClassIds.add(umlClass.getId());
                }
            }
        }

        Set<String> relationIds = new HashSet<>();

        for (UmlRelation relation : relations) {

            if (relation == null) {
                result.addError(
                        "NULL_RELATION",
                        "El diagrama contiene una relación nula",
                        null
                );
                continue;
            }

            if (isBlank(relation.getId())) {

                result.addError(
                        "RELATION_ID_REQUIRED",
                        "Toda relación debe tener un identificador",
                        null
                );

            } else if (!relationIds.add(relation.getId())) {

                result.addError(
                        "DUPLICATE_RELATION_ID",
                        "Existe más de una relación con el mismo identificador",
                        relation.getId()
                );
            }

            if (!validClassIds.contains(relation.getSourceClassId())) {

                result.addError(
                        "INVALID_SOURCE_CLASS",
                        "La clase origen de la relación no existe",
                        relation.getId()
                );
            }

            if (!validClassIds.contains(relation.getTargetClassId())) {

                result.addError(
                        "INVALID_TARGET_CLASS",
                        "La clase destino de la relación no existe",
                        relation.getId()
                );
            }

            if (relation.getType() == null) {

                result.addError(
                        "RELATION_TYPE_REQUIRED",
                        "Toda relación debe tener un tipo",
                        relation.getId()
                );
            }

            validateMultiplicity(
                    relation.getSourceMultiplicity(),
                    relation.getId(),
                    "SOURCE",
                    result
            );

            validateMultiplicity(
                    relation.getTargetMultiplicity(),
                    relation.getId(),
                    "TARGET",
                    result
            );
        }
    }

    private void validateMultiplicity(
            Multiplicity multiplicity,
            String relationId,
            String side,
            ValidationResult result
    ) {

        if (multiplicity == null) {

            result.addError(
                    "MULTIPLICITY_REQUIRED",
                    "La relación debe definir multiplicidad en ambos extremos",
                    relationId
            );

            return;
        }

        String lower = multiplicity.getLower();
        String upper = multiplicity.getUpper();

        if (!isValidLowerMultiplicity(lower)) {

            result.addError(
                    "INVALID_" + side + "_LOWER_MULTIPLICITY",
                    "El límite inferior de la multiplicidad no es válido",
                    relationId
            );
        }

        if (!isValidUpperMultiplicity(upper)) {

            result.addError(
                    "INVALID_" + side + "_UPPER_MULTIPLICITY",
                    "El límite superior de la multiplicidad no es válido",
                    relationId
            );
        }

        if (isNumeric(lower) && isNumeric(upper)) {

            int lowerValue = Integer.parseInt(lower);
            int upperValue = Integer.parseInt(upper);

            if (lowerValue > upperValue) {

                result.addError(
                        "INVALID_" + side + "_MULTIPLICITY_RANGE",
                        "El límite inferior no puede ser mayor al límite superior",
                        relationId
                );
            }
        }
    }

    private boolean isValidLowerMultiplicity(String value) {
        return isNumeric(value);
    }

    private boolean isValidUpperMultiplicity(String value) {
        return "*".equals(value) || isNumeric(value);
    }

    private boolean isNumeric(String value) {

        if (isBlank(value)) {
            return false;
        }

        try {
            return Integer.parseInt(value) >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}