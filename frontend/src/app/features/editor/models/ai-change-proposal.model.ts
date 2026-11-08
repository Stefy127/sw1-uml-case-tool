export type AiChangeOperation = 'ADD_ATTRIBUTE' | 'RENAME_ATTRIBUTE' | 'CHANGE_ATTRIBUTE_TYPE' | string;

export interface AiChangeProposal {
  proposalId: string;
  operation: AiChangeOperation;
  targetElementId: string;
  targetElementName: string;
  targetAttributeId: string | null;
  oldValue: string | null;
  newValue: string | null;
  reason: string;
  requiresBusinessConfirmation: boolean;
}

export interface AiChangeProposalResponse {
  projectId: string;
  diagramId: string;
  diagramVersion: number;
  proposals: AiChangeProposal[];
  isStale: boolean;
}
