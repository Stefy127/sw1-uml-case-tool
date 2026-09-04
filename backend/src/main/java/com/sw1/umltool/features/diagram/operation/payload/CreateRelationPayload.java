package com.sw1.umltool.features.diagram.operation.payload;

import com.sw1.umltool.features.diagram.model.canonical.UmlRelation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRelationPayload {

    private UmlRelation relation;
}
