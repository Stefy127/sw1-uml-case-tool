export interface Project {
  id: string;
  name: string;
  description: string | null;
  ownerUserId: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateProjectRequest {
  name: string;
  description?: string;
  ownerUserId: string;
}
export type ProjectMemberRole = 'OWNER' | 'EDITOR' | 'VIEWER';
export interface ProjectMember { id: string; userId: string; firstName: string; lastName: string; email: string; role: ProjectMemberRole; }
export type ProjectShareMode = 'RESTRICTED' | 'LINK_VIEWER' | 'LINK_EDITOR';
export interface ShareLinkResponse { mode: ProjectShareMode; url: string | null; }
export interface SharedProjectResponse { project: Project; role: ProjectMemberRole; }
