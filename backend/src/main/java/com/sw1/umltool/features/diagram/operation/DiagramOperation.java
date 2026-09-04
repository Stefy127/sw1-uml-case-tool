package com.sw1.umltool.features.diagram.operation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagramOperation {

    private String operationId;

    private String diagramId;

    private String userId;

    private long baseVersion;

    private DiagramOperationType type;

    private Object payload;
}
