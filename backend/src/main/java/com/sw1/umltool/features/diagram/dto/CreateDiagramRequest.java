package com.sw1.umltool.features.diagram.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateDiagramRequest {

    @NotBlank
    private String projectId;

    @NotBlank
    private String name;
}
