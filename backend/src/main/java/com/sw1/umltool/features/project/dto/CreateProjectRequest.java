package com.sw1.umltool.features.project.dto;

import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateProjectRequest {

    @NotBlank
    private String name;

    private String description;

    @NotBlank
    private String ownerUserId;

    private String diagramName;
    private UmlDiagram canonicalModel;
    private DiagramViewState viewState;
}
