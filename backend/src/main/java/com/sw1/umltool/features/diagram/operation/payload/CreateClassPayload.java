package com.sw1.umltool.features.diagram.operation.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateClassPayload {

    private String classId;
    private String name;
    private boolean isAbstract;
    private double x;
    private double y;
    private double width;
    private double height;
}
