import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api.config';
import { CreateProjectRequest, Project } from '../models/project.model';

@Injectable({ providedIn: 'root' })
export class ProjectService {
  private readonly projectsUrl = `${API_BASE_URL}/projects`;

  constructor(private readonly http: HttpClient) {}

  getProjectsByOwner(ownerUserId: string): Observable<Project[]> {
    const params = new HttpParams().set('ownerUserId', ownerUserId);
    return this.http.get<Project[]>(this.projectsUrl, { params });
  }

  getProjectById(projectId: string): Observable<Project> {
    return this.http.get<Project>(`${this.projectsUrl}/${projectId}`);
  }

  createProject(request: CreateProjectRequest): Observable<Project> {
    return this.http.post<Project>(this.projectsUrl, request);
  }
}
