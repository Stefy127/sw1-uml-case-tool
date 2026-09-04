package com.sw1.umltool.features.diagram.model.canonical;

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
public class UmlDiagram {

    private String id;

    private String name;

    private long version;

    @Builder.Default
    private List<UmlClass> classes = new ArrayList<>();

    @Builder.Default
    private List<UmlRelation> relations = new ArrayList<>();
}