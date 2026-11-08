import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api.config';
import { AnalysisResponse } from '../models/ai-analysis.model';

@Injectable({ providedIn: 'root' })
export class AiAnalysisService {
  private readonly http = inject(HttpClient);

  analyze(projectId: string, diagramId?: string | null): Observable<AnalysisResponse> {
    const url = `${API_BASE_URL}/projects/${projectId}/ai/analyze`;
    return this.http.post<AnalysisResponse>(diagramId ? `${url}?diagramId=${encodeURIComponent(diagramId)}` : url, {});
  }
}
