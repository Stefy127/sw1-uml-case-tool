import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api.config';

export interface BackendGenerationRequest {
  basePackage: string;
  artifactId: string;
  groupId: string;
  projectName: string;
}

@Injectable({ providedIn: 'root' })
export class BackendGeneratorService {
  private readonly http = inject(HttpClient);

  generate(diagramId: string, request: BackendGenerationRequest): Observable<HttpResponse<Blob>> {
    return this.http.post(`${API_BASE_URL}/diagrams/${diagramId}/generate/backend`, request, {
      observe: 'response',
      responseType: 'blob',
    });
  }
}
