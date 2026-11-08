export type AnalysisSeverity = 'ERROR' | 'WARNING' | 'INFO';

export interface AnalysisFinding {
  severity: AnalysisSeverity;
  category: string;
  elementId: string | null;
  title: string;
  description: string;
  suggestion: string | null;
}

export interface AnalysisResponse {
  projectId: string;
  summary: string;
  findings: AnalysisFinding[];
}
