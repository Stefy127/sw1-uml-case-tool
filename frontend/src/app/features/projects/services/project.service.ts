import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api.config';
import { CreateProjectRequest, Project, ProjectMember, ProjectMemberRole } from '../models/project.model';

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
  getSharedProjects(): Observable<Project[]> { return this.http.get<Project[]>(`${this.projectsUrl}/shared`); }
  getMembers(projectId: string): Observable<ProjectMember[]> { return this.http.get<ProjectMember[]>(`${this.projectsUrl}/${projectId}/members`); }
  addMember(projectId: string, email: string, role: ProjectMemberRole): Observable<ProjectMember> { return this.http.post<ProjectMember>(`${this.projectsUrl}/${projectId}/members`, { email, role }); }
  changeMemberRole(projectId: string, memberId: string, role: ProjectMemberRole): Observable<ProjectMember> { return this.http.put<ProjectMember>(`${this.projectsUrl}/${projectId}/members/${memberId}/role`, { role }); }
  removeMember(projectId: string, memberId: string): Observable<void> { return this.http.delete<void>(`${this.projectsUrl}/${projectId}/members/${memberId}`); }
  getMyRole(projectId: string): Observable<{ role: ProjectMemberRole }> { return this.http.get<{ role: ProjectMemberRole }>(`${this.projectsUrl}/${projectId}/my-role`); }
}
