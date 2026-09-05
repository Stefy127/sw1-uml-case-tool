export type DiagramOperationType =
  | 'CREATE_CLASS'
  | 'DELETE_CLASS'
  | 'RENAME_CLASS'
  | 'SET_CLASS_ABSTRACT'
  | 'ADD_ATTRIBUTE'
  | 'UPDATE_ATTRIBUTE'
  | 'REMOVE_ATTRIBUTE'
  | 'ADD_METHOD'
  | 'UPDATE_METHOD'
  | 'REMOVE_METHOD'
  | 'ADD_PARAMETER'
  | 'UPDATE_PARAMETER'
  | 'REMOVE_PARAMETER'
  | 'CREATE_RELATION'
  | 'DELETE_RELATION'
  | 'CHANGE_RELATION_TYPE'
  | 'CHANGE_MULTIPLICITY'
  | 'CHANGE_RELATION_ROLES'
  | 'CHANGE_NAVIGABILITY'
  | 'MOVE_CLASS'
  | 'RESIZE_CLASS'
  | 'UPDATE_CLASS_STYLE';

export interface CreateClassPayload {
  classId: string;
  name: string;
  isAbstract: boolean;
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface DeleteClassPayload {
  classId: string;
}

export interface RenameClassPayload {
  classId: string;
  name: string;
}

export interface MoveClassPayload {
  classId: string;
  x: number;
  y: number;
}

export interface ResizeClassPayload {
  classId: string;
  width: number;
  height: number;
}

export interface UpdateClassStylePayload {
  classId: string;
  headerColor: string | null;
  bodyColor: string | null;
  borderColor: string | null;
}

export interface CreateRelationPayload {
  relation: import('./diagram.model').UmlRelation;
}

export interface DeleteRelationPayload {
  relationId: string;
}

export interface ChangeMultiplicityPayload {
  relationId: string;
  sourceMultiplicity: import('./diagram.model').Multiplicity;
  targetMultiplicity: import('./diagram.model').Multiplicity;
}

export interface ChangeRelationRolesPayload {
  relationId: string;
  sourceRole: string;
  targetRole: string;
}

export interface ChangeNavigabilityPayload {
  relationId: string;
  sourceNavigable: boolean;
  targetNavigable: boolean;
}

export interface AddAttributePayload {
  classId: string;
  attribute: import('./diagram.model').UmlAttribute;
}

export interface UpdateAttributePayload {
  classId: string;
  attributeId: string;
  name: string;
  type: string;
  visibility: string;
  isStatic: boolean;
  isFinal: boolean;
  defaultValue: string | null;
  primaryKey: boolean;
}

export interface RemoveAttributePayload {
  classId: string;
  attributeId: string;
}

export interface AddMethodPayload {
  classId: string;
  method: import('./diagram.model').UmlMethod;
}

export interface UpdateMethodPayload {
  classId: string;
  methodId: string;
  name: string;
  returnType: string;
  visibility: string;
  isStatic: boolean;
}

export interface RemoveMethodPayload {
  classId: string;
  methodId: string;
}

export interface AddParameterPayload {
  classId: string;
  methodId: string;
  parameter: import('./diagram.model').UmlParameter;
}

export interface UpdateParameterPayload {
  classId: string;
  methodId: string;
  parameterId: string;
  name: string;
  type: string;
}

export interface RemoveParameterPayload {
  classId: string;
  methodId: string;
  parameterId: string;
}

export interface DiagramOperation {
  operationId: string;
  diagramId: string;
  userId: string;
  baseVersion: number;
  type: DiagramOperationType;
  payload:
    | CreateClassPayload
    | DeleteClassPayload
    | RenameClassPayload
    | MoveClassPayload
    | ResizeClassPayload
    | UpdateClassStylePayload
    | CreateRelationPayload
    | DeleteRelationPayload
    | ChangeMultiplicityPayload
    | ChangeRelationRolesPayload
    | ChangeNavigabilityPayload
    | AddAttributePayload
    | UpdateAttributePayload
    | RemoveAttributePayload
    | AddMethodPayload
    | UpdateMethodPayload
    | RemoveMethodPayload
    | AddParameterPayload
    | UpdateParameterPayload
    | RemoveParameterPayload;
}

export interface ExecuteDiagramOperationRequest {
  operation: DiagramOperation;
}

export interface OperationExecutionResponse {
  operationId: string;
  diagramId: string;
  previousVersion: number;
  newVersion: number;
  canonicalModel: import('./diagram.model').UmlDiagram;
  viewState: import('./diagram.model').DiagramViewState;
}
