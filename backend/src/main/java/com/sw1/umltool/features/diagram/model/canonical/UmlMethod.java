package com.sw1.umltool.features.diagram.model.canonical;

import com.sw1.umltool.features.diagram.model.canonical.enums.Visibility;
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
public class UmlMethod {

    private String id;

    private String name;

    private String returnType;

    private Visibility visibility;

    private boolean isStatic;

    @Builder.Default
    private List<UmlParameter> parameters = new ArrayList<>();
}