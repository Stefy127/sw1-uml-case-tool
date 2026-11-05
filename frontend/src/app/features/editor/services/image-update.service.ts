import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../../core/config/api.config';
import { ImageUpdateApplyResponse, ImageUpdatePreview, ImageUpdateProposal } from '../models/image-update.model';

@Injectable({ providedIn: 'root' })
export class ImageUpdateService {
  private readonly http = inject(HttpClient);

  preview(diagramId: string, baseVersion: number, file: File, instruction: string, allowDestructiveChanges: boolean): Observable<ImageUpdatePreview> {
    const form = new FormData();
    form.append('file', file);
    const params = new HttpParams().set('diagramId', diagramId).set('baseVersion', baseVersion).set('instruction', instruction).set('allowDestructiveChanges', allowDestructiveChanges);
    return this.http.post<ImageUpdatePreview>(`${API_BASE_URL}/import/image/update-preview`, form, { params });
  }

  apply(diagramId: string, baseVersion: number, allowDestructiveChanges: boolean, operations: Array<{ proposal: ImageUpdateProposal; selected: boolean }>): Observable<ImageUpdateApplyResponse> {
    return this.http.post<ImageUpdateApplyResponse>(`${API_BASE_URL}/import/image/update-apply`, { diagramId, baseVersion, allowDestructiveChanges, operations });
  }
}
