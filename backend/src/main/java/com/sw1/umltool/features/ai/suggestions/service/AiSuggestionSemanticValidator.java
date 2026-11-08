package com.sw1.umltool.features.ai.suggestions.service;

import com.sw1.umltool.features.ai.analysis.service.AiAnalysisContextService;

/**
 * Conservative validation for generated suggestions. Suggestions are not
 * diagram operations and must never be treated as an executable mutation.
 */
public final class AiSuggestionSemanticValidator {
    public boolean accepts(String category, String elementId, boolean requiresBusinessContext,
                           AiAnalysisContextService.ElementDescriptor descriptor) {
        if ("GENERAL".equals(category)) return true;
        if (!"ATTRIBUTE".equals(category) || elementId == null || descriptor == null) return false;
        if (!requiresBusinessContext) return false;
        return descriptor.kind() == AiAnalysisContextService.ElementKind.CLASS
                || descriptor.kind() == AiAnalysisContextService.ElementKind.ATTRIBUTE;
    }
}
