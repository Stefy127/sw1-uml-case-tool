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
  isAbstract: boolean;
  /** Jackson may expose Lombok boolean accessors without the `is` prefix. */
  abstract?: boolean;
  attributes: UmlAttribute[];
  methods: UmlMethod[];
}

export interface UmlAttribute {
  id: string;
  name: string;
  type: string;
  visibility: string;
  isStatic: boolean;
  isFinal: boolean;
  static?: boolean;
  final?: boolean;
  defaultValue: string | null;
  primaryKey: boolean;
}

export interface UmlMethod {
  id: string;
  name: string;
  returnType: string;
  visibility: string;
  isStatic: boolean;
  static?: boolean;
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
  headerColor?: string | null;
  bodyColor?: string | null;
  borderColor?: string | null;
}

export interface RelationViewState {
  relationId: string;
}
