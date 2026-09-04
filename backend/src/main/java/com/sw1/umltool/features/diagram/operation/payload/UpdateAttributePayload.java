package com.sw1.umltool.features.diagram.operation.payload;

import com.sw1.umltool.features.diagram.model.canonical.enums.Visibility;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAttributePayload {

    private String classId;
    private String attributeId;
    private String name;
    private String type;
    private Visibility visibility;
    private Boolean isStatic;
    private Boolean isFinal;
    private String defaultValue;
    private Boolean primaryKey;
}
