import { TestBed } from '@angular/core/testing';
import { of, Subject } from 'rxjs';
import { signal } from '@angular/core';

import { ProjectChatComponent } from './project-chat.component';
import { AiChatService } from '../../services/ai-chat.service';
import { CollaborationEvent } from '../../services/collaboration.service';
import { CollaborationService } from '../../services/collaboration.service';
import { AuthService } from '../../../auth/services/auth.service';
import { VoiceRecognitionService } from '../../services/voice-recognition.service';

describe('ProjectChatComponent', () => {
  it('renders basic Markdown safely without exposing raw HTML', () => {
    const page = Object.create(ProjectChatComponent.prototype) as ProjectChatComponent;
    const rendered = page.renderMessage('**Clases**\n- `Cliente`\n<script>alert(1)</script>');

    expect(rendered).toContain('<strong>Clases</strong>');
    expect(rendered).toContain('<li><code>Cliente</code></li>');
    expect(rendered).toContain('&lt;script&gt;alert(1)&lt;/script&gt;');
    expect(rendered).not.toContain('<script>');
  });

  it('renders escaped numbered Markdown lists as ordered lists', () => {
    const page = Object.create(ProjectChatComponent.prototype) as ProjectChatComponent;
    const rendered = page.renderMessage('1\\. Hecho **comprobado**\n2\\. Sugerencia opcional');

    expect(rendered).toContain('<ol><li>Hecho <strong>comprobado</strong></li><li>Sugerencia opcional</li></ol>');
  });

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

  it('sends the selected diagram context and supports the project scope', async () => {
    const collaboration = {
      events: new Subject<CollaborationEvent>(),
      status: signal<'DISCONNECTED'>('DISCONNECTED'),
      subscribeProject: vi.fn(),
      unsubscribeProject: vi.fn(),
      sendChatMessage: vi.fn(() => null),
    };
    const chat = {
      getConversation: vi.fn(() => of({ id: 'c1', projectId: 'p1', type: 'PRIVATE' as const })),
      getMessages: vi.fn(() => of({ messages: [], nextCursor: null })),
      listMemories: vi.fn(() => of([])),
      sendMessage: vi.fn(() => of({ id: 'm1', authorType: 'USER', content: 'hola', createdAt: '2026-01-01T00:00:00', authorName: 'Ana', authorUserId: 'u1' })),
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
    fixture.componentRef.setInput('diagramId', 'diagram-7');
    fixture.componentRef.setInput('diagramVersion', 12);
    fixture.detectChanges();
    await fixture.whenStable();
    const page = fixture.componentInstance;
    page.draft = '¿Qué clases existen?';
    page.send();
    expect(chat.sendMessage).toHaveBeenCalledWith('p1', 'c1', false, '¿Qué clases existen?', expect.any(String), {
      diagramId: 'diagram-7', contextScope: 'DIAGRAM', diagramVersion: 12,
    });
    page.setContextScope('PROJECT');
    expect(page.contextScope()).toBe('PROJECT');
  });

  it('releases sending and clears the composer when HTTP delivery completes', async () => {
    const response = new Subject<any>();
    const collaboration = { events: new Subject<CollaborationEvent>(), status: signal<'DISCONNECTED'>('DISCONNECTED'), subscribeProject: vi.fn(), unsubscribeProject: vi.fn(), sendChatMessage: vi.fn(() => null) };
    const chat = {
      getConversation: vi.fn(() => of({ id: 'c1', projectId: 'p1', type: 'PRIVATE' as const })),
      getMessages: vi.fn(() => of({ messages: [], nextCursor: null })), listMemories: vi.fn(() => of([])), sendMessage: vi.fn(() => response),
    };
    await TestBed.configureTestingModule({ imports: [ProjectChatComponent], providers: [
      { provide: AiChatService, useValue: chat }, { provide: CollaborationService, useValue: collaboration },
      { provide: AuthService, useValue: { currentUser: signal(null) } },
    ] }).compileComponents();
    const fixture = TestBed.createComponent(ProjectChatComponent); fixture.componentRef.setInput('projectId', 'p1'); fixture.detectChanges(); await fixture.whenStable();
    const page = fixture.componentInstance; page.draft = 'consulta'; page.send();
    expect(page.sending()).toBe(true);
    response.next({ id: 'm1', authorType: 'USER', content: 'consulta', createdAt: '2026-01-01T00:00:00', authorName: 'Ana', authorUserId: 'u1' }); response.complete();
    expect(page.sending()).toBe(false); expect(page.draft).toBe('');
  });

  it('releases sending but preserves the composer when HTTP delivery fails', async () => {
    const response = new Subject<any>();
    const collaboration = { events: new Subject<CollaborationEvent>(), status: signal<'DISCONNECTED'>('DISCONNECTED'), subscribeProject: vi.fn(), unsubscribeProject: vi.fn(), sendChatMessage: vi.fn(() => null) };
    const chat = {
      getConversation: vi.fn(() => of({ id: 'c1', projectId: 'p1', type: 'PRIVATE' as const })),
      getMessages: vi.fn(() => of({ messages: [], nextCursor: null })), listMemories: vi.fn(() => of([])), sendMessage: vi.fn(() => response),
    };
    await TestBed.configureTestingModule({ imports: [ProjectChatComponent], providers: [
      { provide: AiChatService, useValue: chat }, { provide: CollaborationService, useValue: collaboration },
      { provide: AuthService, useValue: { currentUser: signal(null) } },
    ] }).compileComponents();
    const fixture = TestBed.createComponent(ProjectChatComponent); fixture.componentRef.setInput('projectId', 'p1'); fixture.detectChanges(); await fixture.whenStable();
    const page = fixture.componentInstance; page.draft = 'reintentar'; page.send(); response.error({ error: { message: 'fallo' } });
    expect(page.sending()).toBe(false); expect(page.draft).toBe('reintentar'); expect(page.error()).toBe('fallo');
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

  it('adds dictated text to the draft without sending it automatically', async () => {
    const voiceEvents = new Subject<{ type: 'result' | 'error' | 'end'; text?: string; message?: string }>();
    const voice = { isSupported: vi.fn(() => true), listen: vi.fn(() => voiceEvents), stop: vi.fn() };
    const collaboration = { events: new Subject<CollaborationEvent>(), status: signal<'DISCONNECTED'>('DISCONNECTED'), subscribeProject: vi.fn(), unsubscribeProject: vi.fn(), sendChatMessage: vi.fn(() => null) };
    const chat = { getConversation: vi.fn(() => of({ id: 'c1', projectId: 'p1', type: 'GROUP' as const })), getMessages: vi.fn(() => of({ messages: [], nextCursor: null })), listMemories: vi.fn(() => of([])), sendMessage: vi.fn() };
    await TestBed.configureTestingModule({ imports: [ProjectChatComponent], providers: [
      { provide: AiChatService, useValue: chat }, { provide: CollaborationService, useValue: collaboration },
      { provide: AuthService, useValue: { currentUser: signal(null) } }, { provide: VoiceRecognitionService, useValue: voice },
    ] }).compileComponents();
    const fixture = TestBed.createComponent(ProjectChatComponent); fixture.componentRef.setInput('projectId', 'p1'); fixture.detectChanges(); await fixture.whenStable();
    const page = fixture.componentInstance; page.draft = 'Hola'; page.toggleVoiceRecognition();
    voiceEvents.next({ type: 'result', text: 'mundo' });
    voiceEvents.next({ type: 'result', text: 'mundo' });
    expect(page.draft).toBe('Hola mundo');
    expect(page.voiceListening()).toBe(true);
    expect(chat.sendMessage).not.toHaveBeenCalled();
    page.toggleVoiceRecognition();
    expect(voice.stop).toHaveBeenCalled();
    expect(page.voiceListening()).toBe(false);
  });

  it('stops dictation when changing mode or closing the chat', async () => {
    const voiceEvents = new Subject<{ type: 'result' | 'error' | 'end'; text?: string; message?: string }>();
    const voice = { isSupported: vi.fn(() => true), listen: vi.fn(() => voiceEvents), stop: vi.fn() };
    const collaboration = { events: new Subject<CollaborationEvent>(), status: signal<'DISCONNECTED'>('DISCONNECTED'), subscribeProject: vi.fn(), unsubscribeProject: vi.fn(), sendChatMessage: vi.fn(() => null) };
    const chat = { getConversation: vi.fn(() => of({ id: 'c1', projectId: 'p1', type: 'GROUP' as const })), getMessages: vi.fn(() => of({ messages: [], nextCursor: null })), listMemories: vi.fn(() => of([])), sendMessage: vi.fn() };
    await TestBed.configureTestingModule({ imports: [ProjectChatComponent], providers: [
      { provide: AiChatService, useValue: chat }, { provide: CollaborationService, useValue: collaboration },
      { provide: AuthService, useValue: { currentUser: signal(null) } }, { provide: VoiceRecognitionService, useValue: voice },
    ] }).compileComponents();
    const fixture = TestBed.createComponent(ProjectChatComponent); fixture.componentRef.setInput('projectId', 'p1'); fixture.detectChanges(); await fixture.whenStable();
    const page = fixture.componentInstance; page.toggleVoiceRecognition(); page.setMode(true);
    expect(voice.stop).toHaveBeenCalled();
    page.toggleVoiceRecognition(); page.closePanel();
    expect(voice.stop.mock.calls.length).toBeGreaterThanOrEqual(2);
  });
});
