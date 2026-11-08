export interface AiSuggestion {
  id: string;
  category: string;
  elementId: string | null;
  title: string;
  reason: string;
  proposal: string;
  requiresBusinessContext: boolean;
}

export interface AiSuggestionsResponse {
  projectId: string;
  diagramId: string | null;
  summary: string;
  suggestions: AiSuggestion[];
}
