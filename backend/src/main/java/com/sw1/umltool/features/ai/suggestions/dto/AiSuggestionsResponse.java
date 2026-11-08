package com.sw1.umltool.features.ai.suggestions.dto;

import java.util.List;

public record AiSuggestionsResponse(String projectId, String diagramId, String summary,
                                    List<AiSuggestion> suggestions) { }
