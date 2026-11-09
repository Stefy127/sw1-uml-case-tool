import { DatePipe } from '@angular/common';
import { AfterViewInit, Component, ElementRef, OnDestroy, OnInit, ViewChild, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AiContextScope, AiConversation, AiMemory, AiMemoryScope, AiMemoryType, AiMessage } from '../../models/ai-chat.model';
import { AiChatService } from '../../services/ai-chat.service';
import { CollaborationEvent, CollaborationService } from '../../services/collaboration.service';
import { VoiceRecognitionService } from '../../services/voice-recognition.service';
import { Subscription, finalize, timeout } from 'rxjs';
import { AuthService } from '../../../auth/services/auth.service';

@Component({
  selector: 'app-project-chat',
  imports: [DatePipe, FormsModule],
  templateUrl: './project-chat.component.html',
  styleUrl: './project-chat.component.scss',
})
export class ProjectChatComponent implements AfterViewInit, OnInit, OnDestroy {
  readonly projectId = input.required<string>();
  readonly diagramId = input<string | null>(null);
  readonly diagramVersion = input<number | null>(null);
  readonly close = output<void>();
  readonly privateChat = signal(false);
  readonly conversation = signal<AiConversation | null>(null);
  readonly messages = signal<AiMessage[]>([]);
  readonly nextCursor = signal<string | null>(null);
  readonly loading = signal(false);
  readonly sending = signal(false);
  readonly error = signal('');
  readonly newMessages = signal(false);
  readonly aiGenerating = signal(false);
  readonly voiceListening = signal(false);
  readonly voiceError = signal('');
  readonly contextScope = signal<AiContextScope>('DIAGRAM');
  readonly memoryScope = signal<AiMemoryScope>('PROJECT');
  readonly memoryOpen = signal(false);
  readonly memories = signal<AiMemory[]>([]);
  readonly memoryLoading = signal(false);
  readonly memorySaving = signal(false);
  readonly memoryError = signal('');
  readonly memoryDraft = signal('');
  readonly memoryType = signal<AiMemoryType>('NOTE');
  readonly editingMemoryId = signal<string | null>(null);
  readonly canManageMemory = input(true);
  readonly memoryTypeLabel = computed(() => ({ CONVENTION: 'Convención', DECISION: 'Decisión', PREFERENCE: 'Preferencia', NOTE: 'Nota' }[this.memoryType()]));
  draft = '';
  @ViewChild('messagesContainer') private messagesContainer?: ElementRef<HTMLElement>;
  private readonly eventsSubscription: Subscription;
  private pendingClientMessageId: string | null = null;
  private pendingClientMessageContent: string | null = null;
  private aiGenerationTimer: ReturnType<typeof setTimeout> | null = null;
  private scrollFrame: number | null = null;
  private voiceSubscription: Subscription | null = null;
  private lastVoiceTranscript = '';

  constructor(private readonly chat: AiChatService, private readonly collaboration: CollaborationService, private readonly auth: AuthService, private readonly voiceRecognition: VoiceRecognitionService) {
    this.eventsSubscription = this.collaboration.events.subscribe(event => this.handleCollaborationEvent(event));
  }

  get collaborationStatus() { return this.collaboration.status; }
  connectionStatusLabel(): string {
    return ({ CONNECTED: 'Conectado', CONNECTING: 'Conectando...', RECONNECTING: 'Reconectando...', ERROR: 'Desconectado', DISCONNECTED: 'Desconectado' } as Record<string, string>)[this.collaboration.status()] ?? 'Desconectado';
  }

  ngOnInit(): void {
    if (!this.diagramId()) this.contextScope.set('PROJECT');
    this.loadConversation();
    this.loadMemories();
  }

  setContextScope(scope: AiContextScope): void { this.contextScope.set(scope); }

  ngAfterViewInit(): void { this.scheduleScrollToBottom(); }

  ngOnDestroy(): void {
    this.eventsSubscription.unsubscribe();
    this.stopVoiceRecognition();
    this.collaboration.unsubscribeProject(this.projectId());
    if (this.aiGenerationTimer) clearTimeout(this.aiGenerationTimer);
    if (this.scrollFrame !== null) cancelAnimationFrame(this.scrollFrame);
  }

  setMode(privateChat: boolean): void {
    if (this.privateChat() === privateChat || this.loading() || this.sending()) return;
    this.stopVoiceRecognition();
    if (!this.privateChat()) this.collaboration.unsubscribeProject(this.projectId());
    this.privateChat.set(privateChat);
    this.loadConversation();
  }

  setMemoryScope(scope: AiMemoryScope): void {
    if (this.memoryScope() === scope || this.memoryLoading()) return;
    this.memoryScope.set(scope);
    this.loadMemories();
  }

  toggleMemory(): void {
    const opening = !this.memoryOpen();
    this.memoryOpen.set(opening);
    if (opening) this.loadMemories();
  }
  closeMemory(): void { this.memoryOpen.set(false); }

  saveMemory(): void {
    const content = this.memoryDraft().trim();
    if (!content || !this.canManageMemory() || this.memorySaving()) return;
    this.memorySaving.set(true);
    this.memoryError.set('');
    const editingId = this.editingMemoryId();
    const request = editingId
      ? this.chat.updateMemory(this.projectId(), editingId, this.memoryType(), content)
      : this.chat.createMemory(this.projectId(), this.memoryScope(), this.memoryType(), content);
    request.subscribe({
      next: memory => { this.upsertMemory(memory); this.memoryDraft.set(''); this.editingMemoryId.set(null); this.memorySaving.set(false); },
      error: error => { this.memoryError.set(error?.error?.message ?? 'No se pudo guardar el recuerdo.'); this.memorySaving.set(false); },
    });
  }

  editMemory(memory: AiMemory): void {
    if (!this.canManageMemory() || this.memorySaving()) return;
    this.editingMemoryId.set(memory.id);
    this.memoryDraft.set(memory.content);
    this.memoryType.set(memory.type);
  }

  cancelMemoryEdit(): void {
    this.editingMemoryId.set(null);
    this.memoryDraft.set('');
  }

  removeMemory(memory: AiMemory): void {
    if (!this.canManageMemory() || this.memorySaving() || !confirm('¿Eliminar este recuerdo?')) return;
    this.memorySaving.set(true);
    this.chat.deleteMemory(this.projectId(), memory.id).subscribe({
      next: () => { this.memories.update(current => current.filter(value => value.id !== memory.id)); this.memorySaving.set(false); },
      error: error => { this.memoryError.set(error?.error?.message ?? 'No se pudo eliminar el recuerdo.'); this.memorySaving.set(false); },
    });
  }

  loadOlder(): void {
    const conversation = this.conversation();
    const cursor = this.nextCursor();
    if (!conversation || !cursor || this.loading()) return;
    const container = this.messagesContainer?.nativeElement;
    const previousHeight = container?.scrollHeight ?? 0;
    const previousTop = container?.scrollTop ?? 0;
    this.loading.set(true);
    this.chat.getMessages(this.projectId(), conversation.id, this.privateChat(), cursor).subscribe({
      next: page => { this.messages.update(current => [...page.messages, ...current]); this.nextCursor.set(page.nextCursor); this.loading.set(false); this.preserveScrollPosition(previousHeight, previousTop); },
      error: () => { this.error.set('No se pudo cargar el historial.'); this.loading.set(false); },
    });
  }

  send(): void {
    const content = this.draft.trim();
    const conversation = this.conversation();
    if (!content || !conversation || this.sending()) return;
    this.sending.set(true);
    this.aiGenerating.set(this.privateChat() || /(^|\s)@ia\b/i.test(content));
    if (this.aiGenerating()) this.startAiGenerationTimeout();
    this.error.set('');
    const clientMessageId = this.pendingClientMessageContent === content && this.pendingClientMessageId
      ? this.pendingClientMessageId : this.newClientMessageId();
    this.pendingClientMessageId = clientMessageId;
    this.pendingClientMessageContent = content;
    this.chat.sendMessage(this.projectId(), conversation.id, this.privateChat(), content, clientMessageId, this.chatContext()).pipe(
      timeout({ each: 15000 }),
      finalize(() => this.sending.set(false)),
    ).subscribe({
      next: message => { this.messages.update(current => current.some(item => item.id === message.id) ? current : [...current, message]); this.draft = ''; this.pendingClientMessageId = null; this.pendingClientMessageContent = null; this.sending.set(false); this.scheduleScrollToBottom(); },
      error: error => { this.error.set(error?.name === 'TimeoutError' ? 'El envío tardó demasiado. Comprueba la conexión y vuelve a intentarlo.' : error?.error?.message ?? 'No se pudo enviar el mensaje.'); this.aiGenerating.set(false); },
    });
  }

  /** Renders a deliberately small Markdown subset after escaping user/AI text. */
  renderMessage(content: string): string {
    const escape = (value: string) => value.replace(/[&<>"']/g, character => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[character] ?? character));
    const inline = (value: string) => escape(value)
      .replace(/`([^`\n]+)`/g, '<code>$1</code>')
      .replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>');
    const lines = (content ?? '').split(/\r?\n/);
    const html: string[] = [];
    let list: string[] = [];
    let listType: 'ul' | 'ol' | null = null;
    const closeList = () => { if (list.length && listType) { html.push(`<${listType}>${list.join('')}</${listType}>`); list = []; listType = null; } };
    for (const line of lines) {
      const unordered = /^\s*-\s+/.test(line);
      const ordered = /^\s*\d+(?:\\[.]|[.])\s+/.test(line);
      if (unordered || ordered) {
        const nextType = ordered ? 'ol' : 'ul';
        if (listType && listType !== nextType) closeList();
        listType = nextType;
        const item = unordered ? line.replace(/^\s*-\s+/, '') : line.replace(/^\s*\d+(?:\\[.]|[.])\s+/, '');
        list.push(`<li>${inline(item)}</li>`);
        continue;
      }
      closeList();
      if (line.trim()) html.push(`<p>${inline(line)}</p>`);
    }
    closeList();
    return html.join('');
  }

  private newClientMessageId(): string {
    return typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  }

  onComposerKeydown(event: KeyboardEvent): void {
    if (event.key !== 'Enter' || event.shiftKey || event.isComposing || event.keyCode === 229) return;
    event.preventDefault();
    this.send();
  }

  onMessagesScroll(): void {
    if (this.isNearBottom()) this.newMessages.set(false);
  }

  scrollToLatest(): void {
    this.newMessages.set(false);
    this.aiGenerating.set(false);
    this.scheduleScrollToBottom();
  }

  isOwnMessage(message: AiMessage): boolean {
    return !!message.authorUserId && message.authorUserId === this.auth.currentUser()?.id;
  }

  messageInitials(message: AiMessage): string {
    const parts = message.authorName.trim().split(/\s+/).filter(Boolean);
    return (parts[0]?.[0] ?? '?') + (parts.length > 1 ? parts.at(-1)?.[0] ?? '' : '');
  }

  showDayDivider(index: number): boolean {
    if (index === 0) return true;
    const current = this.messages()[index];
    const previous = this.messages()[index - 1];
    return new Date(current.createdAt).toDateString() !== new Date(previous.createdAt).toDateString();
  }

  messageGroupStarts(index: number): boolean {
    if (index === 0) return true;
    const current = this.messages()[index];
    const previous = this.messages()[index - 1];
    return current.authorUserId !== previous.authorUserId || this.showDayDivider(index);
  }

  closePanel(): void {
    this.stopVoiceRecognition();
    this.collaboration.unsubscribeProject(this.projectId());
    this.close.emit();
  }

  toggleVoiceRecognition(): void {
    if (this.voiceListening()) {
      this.stopVoiceRecognition();
      return;
    }
    if (!this.voiceRecognition.isSupported()) {
      this.voiceError.set('El reconocimiento de voz no está disponible en este navegador.');
      return;
    }
    this.voiceError.set('');
    this.lastVoiceTranscript = '';
    this.voiceListening.set(true);
    this.voiceSubscription?.unsubscribe();
    this.voiceSubscription = this.voiceRecognition.listen().subscribe({
      next: event => {
        if (event.type === 'result' && event.text?.trim()) {
          const transcript = event.text.trim();
          if (transcript === this.lastVoiceTranscript) return;
          this.lastVoiceTranscript = transcript;
          this.appendVoiceTranscript(transcript);
        }
        if (event.type === 'error') {
          this.voiceError.set(event.message ?? 'No se pudo reconocer la voz.');
          this.voiceListening.set(false);
        }
        if (event.type === 'end') this.voiceListening.set(false);
      },
      error: () => {
        this.voiceError.set('No se pudo reconocer la voz.');
        this.voiceListening.set(false);
      },
      complete: () => this.voiceListening.set(false),
    });
  }

  private stopVoiceRecognition(): void {
    this.voiceRecognition.stop();
    this.voiceSubscription?.unsubscribe();
    this.voiceSubscription = null;
    this.voiceListening.set(false);
    this.lastVoiceTranscript = '';
  }

  private appendVoiceTranscript(transcript: string): void {
    const current = this.draft.trimEnd();
    this.draft = current ? `${current} ${transcript}` : transcript;
  }

  private loadConversation(): void {
    this.loading.set(true);
    this.error.set('');
    this.conversation.set(null);
    this.messages.set([]);
    this.nextCursor.set(null);
    this.newMessages.set(false);
    this.chat.getConversation(this.projectId(), this.privateChat()).subscribe({
      next: conversation => {
        this.conversation.set(conversation);
        this.collaboration.subscribeProject(this.projectId(), conversation.id);
        this.chat.getMessages(this.projectId(), conversation.id, this.privateChat()).subscribe({
          next: page => { this.messages.set(page.messages); this.nextCursor.set(page.nextCursor); this.loading.set(false); this.scheduleScrollToBottom(); },
          error: () => { this.error.set('No se pudo cargar el historial.'); this.loading.set(false); },
        });
      },
      error: error => { this.error.set(error?.error?.message ?? 'No tienes acceso a este chat.'); this.loading.set(false); },
    });
  }

  private chatContext(): { diagramId: string | null; contextScope: AiContextScope; diagramVersion: number | null } {
    const scope = this.diagramId() ? this.contextScope() : 'PROJECT';
    return {
      diagramId: scope === 'DIAGRAM' ? this.diagramId() : null,
      contextScope: scope,
      diagramVersion: scope === 'DIAGRAM' ? this.diagramVersion() : null,
    };
  }

  private loadMemories(): void {
    this.memoryLoading.set(true);
    this.memoryError.set('');
    this.chat.listMemories(this.projectId(), this.memoryScope()).subscribe({
      next: memories => { this.memories.set(this.uniqueMemories(memories)); this.memoryLoading.set(false); },
      error: error => { this.memoryError.set(error?.error?.message ?? 'No se pudo cargar la memoria.'); this.memoryLoading.set(false); },
    });
  }

  private upsertMemory(memory: AiMemory): void {
    this.memories.update(current => this.uniqueMemories([memory, ...current.filter(value => value.id !== memory.id)]));
  }

  private uniqueMemories(memories: AiMemory[]): AiMemory[] {
    const seen = new Set<string>();
    return memories.filter(memory => {
      const key = memory.content.trim().toLowerCase();
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
  }

  private handleCollaborationEvent(event: CollaborationEvent): void {
    if (event.type === 'JOINED' && this.memoryOpen()) this.loadMemories();
    if (event.projectId === this.projectId() && event.type.startsWith('AI_MEMORY_PROJECT_')) {
      if (this.memoryScope() === 'PROJECT' && (event.type === 'AI_MEMORY_PROJECT_CREATED' || event.type === 'AI_MEMORY_PROJECT_UPDATED') && event.memory) this.upsertMemory(event.memory);
      if (this.memoryScope() === 'PROJECT' && event.type === 'AI_MEMORY_PROJECT_DELETED' && event.memoryId) {
        this.memories.update(current => current.filter(value => value.id !== event.memoryId));
      }
    }
    if (event.type === 'CHAT_MESSAGE_REJECTED' && event.projectId === this.projectId() && event.clientMessageId === this.pendingClientMessageId) {
      this.pendingClientMessageId = null;
      this.sending.set(false);
      this.aiGenerating.set(false);
      this.error.set(event.errorMessage ?? 'No se pudo enviar el mensaje.');
      return;
    }
    if (event.type === 'CHAT_MESSAGE_CREATED' && event.projectId === this.projectId() && event.clientMessageId === this.pendingClientMessageId) {
      this.pendingClientMessageId = null;
      this.draft = '';
      this.sending.set(false);
      if (this.aiGenerating()) this.startAiGenerationTimeout();
    }
    if (event.type === 'CHAT_AI_MESSAGE_CREATED' && event.projectId === this.projectId()) {
      this.aiGenerating.set(false);
      if (this.aiGenerationTimer) clearTimeout(this.aiGenerationTimer);
    }
    if ((event.type !== 'CHAT_MESSAGE_CREATED' && event.type !== 'CHAT_AI_MESSAGE_CREATED') || event.projectId !== this.projectId() || !event.message) return;
    const conversation = this.conversation();
    if (!conversation || event.conversationId !== conversation.id) return;
    if (this.messages().some(message => message.id === event.message!.id)) return;
    const shouldStickToBottom = this.isNearBottom();
    this.messages.update(current => [...current, event.message!]);
    if (shouldStickToBottom) this.scheduleScrollToBottom();
    else this.newMessages.set(true);
    if (event.clientMessageId === this.pendingClientMessageId) {
      this.pendingClientMessageId = null;
      this.draft = '';
      this.sending.set(false);
    }
  }

  private startAiGenerationTimeout(): void {
    if (this.aiGenerationTimer) clearTimeout(this.aiGenerationTimer);
    this.aiGenerationTimer = setTimeout(() => {
      this.aiGenerating.set(false);
      this.error.set('La generación de IA tardó demasiado. Puedes continuar escribiendo o reintentar.');
    }, 150000);
  }

  private isNearBottom(): boolean {
    const container = this.messagesContainer?.nativeElement;
    return !container || container.scrollHeight - container.scrollTop - container.clientHeight <= 56;
  }

  private scheduleScrollToBottom(): void {
    if (this.scrollFrame !== null) cancelAnimationFrame(this.scrollFrame);
    this.scrollFrame = requestAnimationFrame(() => {
      this.scrollFrame = null;
      const container = this.messagesContainer?.nativeElement;
      if (container) container.scrollTop = container.scrollHeight;
    });
  }

  private preserveScrollPosition(previousHeight: number, previousTop: number): void {
    requestAnimationFrame(() => {
      const container = this.messagesContainer?.nativeElement;
      if (container) container.scrollTop = previousTop + container.scrollHeight - previousHeight;
    });
  }
}
