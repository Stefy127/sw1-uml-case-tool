package com.sw1.umltool.features.diagram.dto;

import com.sw1.umltool.features.diagram.operation.DiagramOperation;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteDiagramOperationRequest {

    @NotNull
    private DiagramOperation operation;
}
