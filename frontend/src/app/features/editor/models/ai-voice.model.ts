import { VoiceCommand } from './voice-command.model';

export interface AiVoiceInterpretRequest {
  text: string;
  language: string;
  diagramContext: VoiceDiagramContext;
}

export interface VoiceDiagramContext {
  classes: Array<{
    id: string;
    name: string;
    attributes?: Array<{ id: string; name: string; type: string }>;
    methods?: Array<{ id: string; name: string; returnType: string; parameters?: Array<{ id: string; name: string; type: string }> }>;
  }>;
  relations?: Array<{
    id: string;
    sourceClassId: string;
    targetClassId: string;
    type: string;
    sourceMultiplicity?: { lower: string; upper: string };
    targetMultiplicity?: { lower: string; upper: string };
  }>;
  associationClassLinks?: Array<{ id: string; relationId: string; classId: string }>;
}

export interface AiVoiceInterpretResponse {
  success: boolean;
  command: VoiceCommand | null;
  confidence: number | null;
  summary: string | null;
  errors: string[];
}
