export interface ImageUpdateProposal {
  proposalId: string;
  type: string;
  className?: string;
  secondaryClassName?: string;
  sourceClassName?: string;
  targetClassName?: string;
  newClassName?: string;
  attributeName?: string;
  newAttributeName?: string;
  attributeType?: string;
  methodName?: string;
  newMethodName?: string;
  returnType?: string;
  relationType?: string;
  confidence?: number;
  summary?: string;
  destructive?: boolean;
  warnings?: string[];
}

export interface ImageUpdatePreview {
  success: boolean;
  diagramId: string;
  baseVersion: number;
  operations: ImageUpdateProposal[];
  warnings: string[];
  confidence?: number;
}

export interface ImageUpdateApplyResponse {
  diagramId: string;
  previousVersion: number;
  newVersion: number;
  canonicalModel: import('./diagram.model').UmlDiagram;
  viewState: import('./diagram.model').DiagramViewState;
  appliedOperations: number;
}
