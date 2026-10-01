import { DiagramOperationType } from './diagram-operation.model';

export type VoiceRelationType = 'ASSOCIATION' | 'AGGREGATION' | 'COMPOSITION' | 'INHERITANCE' | 'DEPENDENCY';

export type VoiceCommandKind =
  | 'CREATE_CLASS'
  | 'DELETE_CLASS'
  | 'RENAME_CLASS'
  | 'LIST_CLASSES'
  | 'ADD_ATTRIBUTE'
  | 'RENAME_ATTRIBUTE'
  | 'CHANGE_ATTRIBUTE_TYPE'
  | 'REMOVE_ATTRIBUTE'
  | 'READ_CLASS_ATTRIBUTES'
  | 'ADD_METHOD'
  | 'RENAME_METHOD'
  | 'REMOVE_METHOD'
  | 'READ_CLASS_METHODS'
  | 'CREATE_RELATION'
  | 'REMOVE_RELATION'
  | 'CHANGE_RELATION_TYPE'
  | 'READ_CLASS_RELATIONS';

export interface VoiceCommand {
  type: VoiceCommandKind;
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
  relationType?: VoiceRelationType;
}

export interface VoiceCommandResult {
  success: boolean;
  command: VoiceCommand | null;
  errors: string[];
}

export interface VoiceCommandPreview {
  originalText: string;
  commandType: DiagramOperationType | VoiceCommandKind | null;
  summary: string;
  command: VoiceCommand | null;
  errors: string[];
  source?: 'LOCAL' | 'AI';
  confidence?: number | null;
  readOnly?: boolean;
}
