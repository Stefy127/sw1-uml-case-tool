package com.sw1.umltool.features.diagram.operation.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateClassStylePayload {

    private String classId;

    private String headerColor;

    private String bodyColor;

    private String borderColor;
}
