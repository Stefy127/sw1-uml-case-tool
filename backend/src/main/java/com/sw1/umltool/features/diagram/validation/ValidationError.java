package com.sw1.umltool.features.diagram.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationError {

    private String code;

    private String message;

    private String elementId;
}