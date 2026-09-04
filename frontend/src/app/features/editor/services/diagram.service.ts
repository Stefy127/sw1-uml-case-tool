import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api.config';
import { DiagramDetail, DiagramSummary } from '../models/diagram.model';

@Injectable({ providedIn: 'root' })
export class DiagramService {
  private readonly diagramsUrl = `${API_BASE_URL}/diagrams`;

  constructor(private readonly http: HttpClient) {}

  getDiagramsByProject(projectId: string): Observable<DiagramSummary[]> {
    const params = new HttpParams().set('projectId', projectId);
    return this.http.get<DiagramSummary[]>(this.diagramsUrl, { params });
  }

  getDiagramById(diagramId: string): Observable<DiagramDetail> {
    return this.http.get<DiagramDetail>(`${this.diagramsUrl}/${diagramId}`);
  }

  createDiagram(projectId: string, name: string): Observable<DiagramDetail> {
    return this.http.post<DiagramDetail>(this.diagramsUrl, { projectId, name });
  }
}
