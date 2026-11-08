package com.sw1.umltool.features.ai.analysis.dto;

import java.util.List;

public record AnalysisResponse(String projectId, String summary, List<AnalysisFinding> findings) {
}
