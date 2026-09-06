import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { API_BASE_URL } from '../../../core/config/api.config';
import { AiVoiceInterpretRequest, AiVoiceInterpretResponse } from '../models/ai-voice.model';

@Injectable({ providedIn: 'root' })
export class AiVoiceCommandService {
  private readonly http = inject(HttpClient);

  interpret(request: AiVoiceInterpretRequest) {
    return this.http.post<AiVoiceInterpretResponse>(`${API_BASE_URL}/ai/voice/interpret`, request);
  }
}
