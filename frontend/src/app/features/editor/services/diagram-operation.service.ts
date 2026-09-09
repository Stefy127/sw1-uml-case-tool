import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api.config';
import {
  ExecuteDiagramOperationRequest,
  OperationExecutionResponse,
} from '../models/diagram-operation.model';
import { CollaborationService } from './collaboration.service';

@Injectable({ providedIn: 'root' })
export class DiagramOperationService {
  constructor(private readonly http: HttpClient, private readonly collaboration: CollaborationService) {}

  execute(
    diagramId: string,
    request: ExecuteDiagramOperationRequest,
  ): Observable<OperationExecutionResponse> {
    if (this.collaboration.hasActiveDiagram()) return this.collaboration.sendOperation(request);
    return this.http.post<OperationExecutionResponse>(
      `${API_BASE_URL}/diagrams/${diagramId}/operations`,
      request,
    );
  }
}
