package com.sw1.umltool.features.diagram.operation.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeRelationRolesPayload {

    private String relationId;
    private String sourceRole;
    private String targetRole;
}
