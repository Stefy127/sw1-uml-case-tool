package com.sw1.umltool.features.diagram.history.dto;

public record DiagramHistoryAvailabilityResponse(String diagramId, long currentVersion,
                                                 boolean canUndo, boolean canRedo) { }
