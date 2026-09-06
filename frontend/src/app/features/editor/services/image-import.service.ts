import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/config/api.config';
import { XmiImportResponse } from '../models/xmi-import.model';

@Injectable({ providedIn: 'root' })
export class ImageImportService {
  private readonly http = inject(HttpClient);

  preview(file: File): Observable<XmiImportResponse> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<XmiImportResponse>(`${API_BASE_URL}/import/image/preview`, form);
  }

  apply(diagramId: string, baseVersion: number, file: File): Observable<XmiImportResponse> {
    const form = new FormData();
    form.append('file', file);
    const params = new HttpParams().set('diagramId', diagramId).set('baseVersion', baseVersion);
    return this.http.post<XmiImportResponse>(`${API_BASE_URL}/import/image/apply`, form, { params });
  }
}
