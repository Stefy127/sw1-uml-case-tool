package com.sw1.umltool.features.diagram.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagramSummaryResponse {

    private String id;
    private String projectId;
    private String name;
    private long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
