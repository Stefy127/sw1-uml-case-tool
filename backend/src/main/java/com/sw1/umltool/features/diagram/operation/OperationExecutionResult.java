package com.sw1.umltool.features.diagram.operation;

import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationExecutionResult {

    private String operationId;
    private String diagramId;
    private long previousVersion;
    private long newVersion;
    private UmlDiagram diagram;
    private DiagramViewState viewState;
}
