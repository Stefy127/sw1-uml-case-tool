import { DiagramDetail } from './diagram.model';

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

export interface AiChangeProposalApplyOperation {
  proposalId: string;
  operation: AiChangeOperation;
  targetElementId: string;
  targetAttributeId: string | null;
  newAttributeName: string | null;
  attributeType: string | null;
}

export interface AiChangeProposalApplyRequest {
  diagramId: string;
  baseVersion: number;
  requestId: string;
  confirmed: boolean;
  operations: AiChangeProposalApplyOperation[];
}

export interface AiChangeProposalApplyResponse {
  requestId: string;
  projectId: string;
  diagramId: string;
  previousVersion: number;
  newVersion: number;
  appliedOperations: number;
  canonicalModel: DiagramDetail['canonicalModel'];
  viewState: DiagramDetail['viewState'];
}
