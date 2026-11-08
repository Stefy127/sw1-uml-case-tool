package com.sw1.umltool.features.ai.analysis.dto;

import com.sw1.umltool.features.ai.analysis.model.AnalysisCategory;
import com.sw1.umltool.features.ai.analysis.model.AnalysisSeverity;

public record AnalysisFinding(
        AnalysisSeverity severity,
        AnalysisCategory category,
        String elementId,
        String title,
        String description,
        String suggestion) {
}
