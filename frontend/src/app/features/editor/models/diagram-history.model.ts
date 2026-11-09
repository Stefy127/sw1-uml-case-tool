export interface DiagramHistoryEntry {
  id: string;
  operationId: string;
  operationType: string;
  origin: string;
  action: string;
  state: string;
  previousVersion: number;
  newVersion: number;
  userId: string;
  createdAt: string;
  description: string;
  details: string[];
}

export interface DiagramHistoryPage {
  content: DiagramHistoryEntry[];
  totalPages: number;
  totalElements: number;
  number: number;
  size: number;
  last: boolean;
}

export interface DiagramHistoryAvailability {
  diagramId: string;
  currentVersion: number;
  canUndo: boolean;
  canRedo: boolean;
}
