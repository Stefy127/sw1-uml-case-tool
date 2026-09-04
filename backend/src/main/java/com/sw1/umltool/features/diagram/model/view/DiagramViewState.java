package com.sw1.umltool.features.diagram.model.view;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagramViewState {

    private String diagramId;

    @Builder.Default
    private List<NodeViewState> nodes = new ArrayList<>();

    @Builder.Default
    private List<RelationViewState> relations = new ArrayList<>();
}
