import { TestBed } from '@angular/core/testing';
import { of, Subject } from 'rxjs';
import { signal } from '@angular/core';

import { ProjectChatComponent } from './project-chat.component';
import { AiChatService } from '../../services/ai-chat.service';
import { CollaborationEvent } from '../../services/collaboration.service';
import { CollaborationService } from '../../services/collaboration.service';
import { AuthService } from '../../../auth/services/auth.service';

describe('ProjectChatComponent', () => {
  it('subscribes to the project and appends a persisted WebSocket message once', async () => {
    const events = new Subject<CollaborationEvent>();
    const collaboration = {
      events,
      status: signal<'CONNECTED'>('CONNECTED'),
      subscribeProject: vi.fn(),
      unsubscribeProject: vi.fn(),
      sendChatMessage: vi.fn(() => null),
    };
    const conversation = { id: 'conversation-1', projectId: 'project-1', type: 'GROUP' as const };
    const chat = {
      getConversation: vi.fn(() => of(conversation)),
      getMessages: vi.fn(() => of({ messages: [], nextCursor: null })),
      listMemories: vi.fn(() => of([])),
      sendMessage: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [ProjectChatComponent],
      providers: [
        { provide: AiChatService, useValue: chat },
        { provide: CollaborationService, useValue: collaboration },
        { provide: AuthService, useValue: { currentUser: signal({ id: 'user-1', firstName: 'Ana', lastName: 'Test', email: '', theme: '' }) } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ProjectChatComponent);
    fixture.componentRef.setInput('projectId', 'project-1');
    fixture.detectChanges();
    await fixture.whenStable();

    expect(collaboration.subscribeProject).toHaveBeenCalledWith('project-1', 'conversation-1');
    expect(fixture.componentInstance.connectionStatusLabel()).toBe('Conectado');
    const message = { id: 'message-1', clientMessageId: 'client-1', authorUserId: 'user-1', authorName: 'Ana', authorType: 'USER' as const, content: 'Hola', createdAt: '2026-01-01T10:00:00' };
    events.next({ type: 'CHAT_MESSAGE_CREATED', projectId: 'project-1', conversationId: 'conversation-1', message });
    events.next({ type: 'CHAT_MESSAGE_CREATED', projectId: 'project-1', conversationId: 'conversation-1', message });

    expect(fixture.componentInstance.messages()).toHaveLength(1);
    fixture.componentInstance.closePanel();
    expect(collaboration.unsubscribeProject).toHaveBeenCalledWith('project-1');
  });

  it('sends once on Enter, preserves Shift+Enter, and ignores IME composition', async () => {
    const collaboration = {
      events: new Subject<CollaborationEvent>(),
      status: signal<'CONNECTED'>('CONNECTED'),
      subscribeProject: vi.fn(),
      unsubscribeProject: vi.fn(),
      sendChatMessage: vi.fn(() => null),
    };
    const chat = { getConversation: vi.fn(() => of({ id: 'c1', projectId: 'p1', type: 'GROUP' as const })), getMessages: vi.fn(() => of({ messages: [], nextCursor: null })), listMemories: vi.fn(() => of([])), sendMessage: vi.fn() };
    await TestBed.configureTestingModule({
      imports: [ProjectChatComponent],
      providers: [
        { provide: AiChatService, useValue: chat },
        { provide: CollaborationService, useValue: collaboration },
        { provide: AuthService, useValue: { currentUser: signal(null) } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ProjectChatComponent);
    fixture.componentRef.setInput('projectId', 'p1');
    fixture.detectChanges();
    await fixture.whenStable();
    const page = fixture.componentInstance;
    const send = vi.spyOn(page, 'send');

    const enter = new KeyboardEvent('keydown', { key: 'Enter' });
    page.onComposerKeydown(enter);
    page.onComposerKeydown(new KeyboardEvent('keydown', { key: 'Enter', shiftKey: true }));
    page.onComposerKeydown(new KeyboardEvent('keydown', { key: 'Enter', isComposing: true }));

    expect(send).toHaveBeenCalledTimes(1);
  });

  it('reconciles REST create response and WebSocket CREATED by memory id', async () => {
    const events = new Subject<CollaborationEvent>();
    const created = new Subject<any>();
    const collaboration = {
      events,
      status: signal<'CONNECTED'>('CONNECTED'),
      subscribeProject: vi.fn(),
      unsubscribeProject: vi.fn(),
      sendChatMessage: vi.fn(() => null),
    };
    const chat = {
      getConversation: vi.fn(() => of({ id: 'c1', projectId: 'p1', type: 'GROUP' as const })),
      getMessages: vi.fn(() => of({ messages: [], nextCursor: null })),
      listMemories: vi.fn(() => of([])),
      createMemory: vi.fn(() => created),
      sendMessage: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [ProjectChatComponent],
      providers: [
        { provide: AiChatService, useValue: chat },
        { provide: CollaborationService, useValue: collaboration },
        { provide: AuthService, useValue: { currentUser: signal(null) } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ProjectChatComponent);
    fixture.componentRef.setInput('projectId', 'p1');
    fixture.detectChanges();
    await fixture.whenStable();
    const memory = { id: 'memory-1', projectId: 'p1', scope: 'PROJECT' as const, ownerUserId: null, type: 'NOTE' as const,
      content: 'Nombres en singular', createdByUserId: 'u1', sourceMessageId: null,
      createdAt: '2026-01-01T10:00:00', updatedAt: '2026-01-01T10:00:00' };
    fixture.componentInstance.memoryDraft.set(memory.content);
    fixture.componentInstance.saveMemory();
    events.next({ type: 'AI_MEMORY_PROJECT_CREATED', projectId: 'p1', memory });
    created.next(memory);
    expect(fixture.componentInstance.memories()).toHaveLength(1);
  });
});
