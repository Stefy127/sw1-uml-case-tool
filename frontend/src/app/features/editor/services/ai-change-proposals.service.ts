import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api.config';
import {
  AiChangeProposalApplyRequest,
  AiChangeProposalApplyResponse,
  AiChangeProposalResponse,
} from '../models/ai-change-proposal.model';

@Injectable({ providedIn: 'root' })
export class AiChangeProposalsService {
  private readonly http = inject(HttpClient);

  propose(projectId: string, diagramId: string, instruction: string, targetElementId: string | null = null): Observable<AiChangeProposalResponse> {
    return this.http.post<AiChangeProposalResponse>(
      `${API_BASE_URL}/projects/${encodeURIComponent(projectId)}/ai/change-proposals?diagramId=${encodeURIComponent(diagramId)}`,
      { instruction, targetElementId },
    );
  }

  apply(projectId: string, request: AiChangeProposalApplyRequest): Observable<AiChangeProposalApplyResponse> {
    return this.http.post<AiChangeProposalApplyResponse>(
      `${API_BASE_URL}/projects/${encodeURIComponent(projectId)}/ai/change-proposals/apply`,
      request,
    );
  }
}
