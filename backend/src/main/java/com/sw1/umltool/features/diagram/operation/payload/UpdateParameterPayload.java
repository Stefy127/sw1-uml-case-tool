package com.sw1.umltool.features.diagram.operation.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateParameterPayload {

    private String classId;
    private String methodId;
    private String parameterId;
    private String name;
    private String type;
}
