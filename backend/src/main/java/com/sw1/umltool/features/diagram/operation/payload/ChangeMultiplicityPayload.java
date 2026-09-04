package com.sw1.umltool.features.diagram.operation.payload;

import com.sw1.umltool.features.diagram.model.canonical.Multiplicity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeMultiplicityPayload {

    private String relationId;
    private Multiplicity sourceMultiplicity;
    private Multiplicity targetMultiplicity;
}
