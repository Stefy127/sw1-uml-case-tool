package com.sw1.umltool.features.importexport.dto;

import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;
import com.sw1.umltool.features.diagram.model.view.DiagramViewState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ImageImportPreviewResponse {
    private UmlDiagram canonicalModel;
    private DiagramViewState viewState;
    private List<String> warnings;
    private XmiImportPreviewResponse.Statistics statistics;
    private Double confidence;
    private List<String> detectedClassNames;
}
