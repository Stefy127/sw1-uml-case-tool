import { DiagramOperationType } from './diagram-operation.model';

export type VoiceRelationType = 'ASSOCIATION' | 'AGGREGATION' | 'COMPOSITION' | 'INHERITANCE' | 'DEPENDENCY';

export type VoiceCommandKind =
  | 'CREATE_CLASS'
  | 'DELETE_CLASS'
  | 'RENAME_CLASS'
  | 'ADD_ATTRIBUTE'
  | 'REMOVE_ATTRIBUTE'
  | 'ADD_METHOD'
  | 'CREATE_RELATION';

export interface VoiceCommand {
  type: VoiceCommandKind;
  className?: string;
  secondaryClassName?: string;
  newClassName?: string;
  attributeName?: string;
  attributeType?: string;
  methodName?: string;
  relationType?: VoiceRelationType;
}

export interface VoiceCommandResult {
  success: boolean;
  command: VoiceCommand | null;
  errors: string[];
}

export interface VoiceCommandPreview {
  originalText: string;
  commandType: DiagramOperationType | null;
  summary: string;
  command: VoiceCommand | null;
  errors: string[];
  source?: 'LOCAL' | 'AI';
  confidence?: number | null;
}
