package com.sw1.umltool.features.diagram.validation;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
public class ValidationResult {

    private final List<ValidationError> errors = new ArrayList<>();

    public void addError(String code, String message, String elementId) {
        errors.add(
                ValidationError.builder()
                        .code(code)
                        .message(message)
                        .elementId(elementId)
                        .build()
        );
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public List<ValidationError> getErrors() {
        return Collections.unmodifiableList(errors);
    }
}