package com.sw1.umltool.features.diagram.history.dto;

import com.sw1.umltool.features.diagram.history.model.DiagramHistoryEntity;

import java.time.LocalDateTime;

public record DiagramHistoryResponse(String id, String operationId, String operationType, String origin,
                                     String action, String state, long previousVersion, long newVersion,
                                     String userId, LocalDateTime createdAt, String description,
                                     java.util.List<String> details) {
    public static DiagramHistoryResponse from(DiagramHistoryEntity value, String description,
                                              java.util.List<String> details) {
        return new DiagramHistoryResponse(value.getId(), value.getOperationId(), value.getOperationType(),
                value.getOrigin(), value.getAction(), value.getState(), value.getPreviousVersion(),
                value.getNewVersion(), value.getUserId(), value.getCreatedAt(), description, details);
    }
}
