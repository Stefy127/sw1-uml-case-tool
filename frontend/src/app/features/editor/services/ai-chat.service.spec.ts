import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_BASE_URL } from '../../../core/config/api.config';
import { AiChatService } from './ai-chat.service';

describe('AiChatService', () => {
  let service: AiChatService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AiChatService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AiChatService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads the group conversation and paged messages', () => {
    service.getConversation('project-1', false).subscribe();
    const conversation = http.expectOne(`${API_BASE_URL}/projects/project-1/ai/chat`);
    conversation.flush({ id: 'conversation-1', projectId: 'project-1', type: 'GROUP' });

    service.getMessages('project-1', 'conversation-1', false).subscribe();
    const messages = http.expectOne(`${API_BASE_URL}/projects/project-1/ai/chat/conversation-1/messages`);
    expect(messages.request.method).toBe('GET');
    messages.flush({ messages: [], nextCursor: null });
  });

  it('uses the private conversation endpoint and sends only content', () => {
    service.sendMessage('project-1', 'conversation-2', true, 'Private note').subscribe();
    const request = http.expectOne(`${API_BASE_URL}/projects/project-1/ai/private-chat/conversation-2/messages`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ content: 'Private note' });
    request.flush({ id: 'message-1', content: 'Private note' });
  });
});
