import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/config/api.config';
import { AiSuggestionsResponse } from '../models/ai-suggestion.model';

@Injectable({ providedIn: 'root' })
export class AiSuggestionsService {
  private readonly http = inject(HttpClient);

  suggest(projectId: string, diagramId: string): Observable<AiSuggestionsResponse> {
    return this.http.post<AiSuggestionsResponse>(`${API_BASE_URL}/projects/${projectId}/ai/suggestions?diagramId=${encodeURIComponent(diagramId)}`, {});
  }
}
