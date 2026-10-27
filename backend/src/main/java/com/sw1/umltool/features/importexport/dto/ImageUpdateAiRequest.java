package com.sw1.umltool.features.importexport.dto;

import com.sw1.umltool.features.diagram.model.canonical.UmlDiagram;

public record ImageUpdateAiRequest(
        String fileName,
        String contentType,
        String imageBase64,
        String instruction,
        boolean allowDestructiveChanges,
        UmlDiagram diagramContext) {}
