package com.sw1.umltool.features.diagram.dto;

import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;
import com.sw1.umltool.features.diagram.operation.OperationExecutionResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationExecutionResponse {

    private String operationId;
    private String diagramId;
    private long previousVersion;
    private long newVersion;
    private UmlDiagram canonicalModel;
    private DiagramViewState viewState;

    public static OperationExecutionResponse from(OperationExecutionResult result) {
        return OperationExecutionResponse.builder()
                .operationId(result.getOperationId())
                .diagramId(result.getDiagramId())
                .previousVersion(result.getPreviousVersion())
                .newVersion(result.getNewVersion())
                .canonicalModel(result.getDiagram())
                .viewState(result.getViewState())
                .build();
    }
}
