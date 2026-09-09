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
