package com.sw1.umltool.features.diagram.model.canonical;

import com.sw1.umltool.features.diagram.model.canonical.enums.RelationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UmlRelation {

    private String id;

    private String sourceClassId;

    private String targetClassId;

    private RelationType type;

    private Multiplicity sourceMultiplicity;

    private Multiplicity targetMultiplicity;

    private String sourceRole;

    private String targetRole;

    private boolean sourceNavigable;

    private boolean targetNavigable;
}