package com.sw1.umltool.features.ai.suggestions.dto;

public record AiSuggestion(String id, String category, String elementId, String title,
                           String reason, String proposal, boolean requiresBusinessContext) { }
