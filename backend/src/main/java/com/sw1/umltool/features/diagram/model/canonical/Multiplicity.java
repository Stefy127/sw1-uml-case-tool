package com.sw1.umltool.features.diagram.model.canonical;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Multiplicity {

    private String lower;
    private String upper;
}