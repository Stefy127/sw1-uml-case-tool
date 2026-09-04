package com.sw1.umltool.features.diagram.model.canonical;

import com.sw1.umltool.features.diagram.model.canonical.enums.Visibility;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UmlAttribute {

    private String id;

    private String name;

    private String type;

    private Visibility visibility;

    private boolean isStatic;

    private boolean isFinal;

    private String defaultValue;

    private boolean primaryKey;
}