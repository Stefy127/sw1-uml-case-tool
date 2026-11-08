import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api.config';
import { AiContextScope, AiConversation, AiMemory, AiMemoryScope, AiMemoryType, AiMessage, AiMessagesPage } from '../models/ai-chat.model';

@Injectable({ providedIn: 'root' })
export class AiChatService {
  constructor(private readonly http: HttpClient) {}

  getConversation(projectId: string, privateChat: boolean): Observable<AiConversation> {
    const path = privateChat ? 'private-chat' : 'chat';
    return this.http.get<AiConversation>(`${API_BASE_URL}/projects/${projectId}/ai/${path}`);
  }

  getMessages(projectId: string, conversationId: string, privateChat: boolean, before?: string | null): Observable<AiMessagesPage> {
    const path = privateChat ? 'private-chat' : 'chat';
    let params = new HttpParams();
    if (before) params = params.set('before', before);
    return this.http.get<AiMessagesPage>(`${API_BASE_URL}/projects/${projectId}/ai/${path}/${conversationId}/messages`, { params });
  }

  sendMessage(projectId: string, conversationId: string, privateChat: boolean, content: string,
    clientMessageId?: string | null,
    context?: { diagramId?: string | null; contextScope?: AiContextScope; diagramVersion?: number | null }): Observable<AiMessage> {
    const path = privateChat ? 'private-chat' : 'chat';
    const body = { content, ...(clientMessageId ? { clientMessageId } : {}), ...(context ?? {}) };
    return this.http.post<AiMessage>(`${API_BASE_URL}/projects/${projectId}/ai/${path}/${conversationId}/messages`, body);
  }

  listMemories(projectId: string, scope: AiMemoryScope): Observable<AiMemory[]> {
    return this.http.get<AiMemory[]>(`${API_BASE_URL}/projects/${projectId}/ai/memory`, { params: { scope } });
  }

  createMemory(projectId: string, scope: AiMemoryScope, type: AiMemoryType, content: string): Observable<AiMemory> {
    return this.http.post<AiMemory>(`${API_BASE_URL}/projects/${projectId}/ai/memory`, { scope, type, content });
  }

  updateMemory(projectId: string, memoryId: string, type: AiMemoryType, content: string): Observable<AiMemory> {
    return this.http.put<AiMemory>(`${API_BASE_URL}/projects/${projectId}/ai/memory/${memoryId}`, { type, content });
  }

  deleteMemory(projectId: string, memoryId: string): Observable<void> {
    return this.http.delete<void>(`${API_BASE_URL}/projects/${projectId}/ai/memory/${memoryId}`);
  }
}
