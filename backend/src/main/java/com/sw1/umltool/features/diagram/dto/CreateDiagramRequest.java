package com.sw1.umltool.features.diagram.dto;

import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;
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

    private UmlDiagram canonicalModel;

    private DiagramViewState viewState;
}
