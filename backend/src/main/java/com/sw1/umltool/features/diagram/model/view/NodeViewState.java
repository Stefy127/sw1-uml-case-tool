package com.sw1.umltool.features.diagram.model.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeViewState {

    private String classId;

    private double x;

    private double y;

    private double width;

    private double height;
}
