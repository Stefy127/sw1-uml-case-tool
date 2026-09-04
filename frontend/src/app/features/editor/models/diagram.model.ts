export interface DiagramSummary {
  id: string;
  projectId: string;
  name: string;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface DiagramDetail extends DiagramSummary {
  canonicalModel: UmlDiagram;
  viewState: DiagramViewState;
}

export interface UmlDiagram {
  id: string;
  name: string;
  version: number;
  classes: UmlClass[];
  relations: UmlRelation[];
}

export interface UmlClass {
  id: string;
  name: string;
  abstract: boolean;
  attributes: UmlAttribute[];
  methods: UmlMethod[];
}

export interface UmlAttribute {
  id: string;
  name: string;
  type: string;
  visibility: string;
  static: boolean;
  final: boolean;
  defaultValue: string | null;
  primaryKey: boolean;
}

export interface UmlMethod {
  id: string;
  name: string;
  returnType: string;
  visibility: string;
  static: boolean;
  parameters: UmlParameter[];
}

export interface UmlParameter {
  id: string;
  name: string;
  type: string;
}

export interface UmlRelation {
  id: string;
  sourceClassId: string;
  targetClassId: string;
  type: string;
  sourceMultiplicity: Multiplicity;
  targetMultiplicity: Multiplicity;
  sourceRole: string | null;
  targetRole: string | null;
  sourceNavigable: boolean;
  targetNavigable: boolean;
}

export interface Multiplicity {
  lower: string;
  upper: string;
}

export interface DiagramViewState {
  diagramId: string;
  nodes: NodeViewState[];
  relations: RelationViewState[];
}

export interface NodeViewState {
  classId: string;
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface RelationViewState {
  relationId: string;
}
