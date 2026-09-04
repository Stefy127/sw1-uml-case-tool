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
public class UmlClass {

    private String id;

    private String name;

    private boolean isAbstract;

    @Builder.Default
    private List<UmlAttribute> attributes = new ArrayList<>();

    @Builder.Default
    private List<UmlMethod> methods = new ArrayList<>();
}