import { VoiceCommand } from './voice-command.model';

export interface AiVoiceInterpretRequest {
  text: string;
  language: string;
  diagramContext: { classes: Array<{ id: string; name: string }> };
}

export interface AiVoiceInterpretResponse {
  success: boolean;
  command: VoiceCommand | null;
  confidence: number | null;
  summary: string | null;
  errors: string[];
}
