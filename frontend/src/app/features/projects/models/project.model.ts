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
