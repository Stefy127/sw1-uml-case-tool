import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api.config';
import { DiagramHistoryAvailability, DiagramHistoryEntry, DiagramHistoryPage } from '../models/diagram-history.model';

@Injectable({ providedIn: 'root' })
export class DiagramHistoryService {
  private readonly baseUrl = `${API_BASE_URL}/diagrams`;

  constructor(private readonly http: HttpClient) {}

  list(diagramId: string, page = 0, size = 25): Observable<DiagramHistoryPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<DiagramHistoryPage>(`${this.baseUrl}/${diagramId}/history`, { params });
  }

  availability(diagramId: string): Observable<DiagramHistoryAvailability> {
    return this.http.get<DiagramHistoryAvailability>(`${this.baseUrl}/${diagramId}/history/availability`);
  }

  undo(diagramId: string): Observable<DiagramHistoryEntry> {
    return this.http.post<DiagramHistoryEntry>(`${this.baseUrl}/${diagramId}/history/undo`, {});
  }

  redo(diagramId: string): Observable<DiagramHistoryEntry> {
    return this.http.post<DiagramHistoryEntry>(`${this.baseUrl}/${diagramId}/history/redo`, {});
  }
}
