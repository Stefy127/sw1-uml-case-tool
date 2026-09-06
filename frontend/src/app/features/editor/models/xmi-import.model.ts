import { DiagramViewState, UmlDiagram } from './diagram.model';

export interface XmiImportStatistics {
  classes: number;
  attributes: number;
  methods: number;
  relations: number;
  associationClasses: number;
}

export interface XmiImportResponse {
  canonicalModel: UmlDiagram;
  viewState: DiagramViewState;
  warnings: string[];
  statistics: XmiImportStatistics;
  confidence?: number | null;
  detectedClassNames?: string[];
}
