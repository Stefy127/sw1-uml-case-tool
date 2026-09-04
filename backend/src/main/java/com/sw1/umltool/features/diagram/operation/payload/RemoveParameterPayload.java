package com.sw1.umltool.features.diagram.operation.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemoveParameterPayload {

    private String classId;
    private String methodId;
    private String parameterId;
}
