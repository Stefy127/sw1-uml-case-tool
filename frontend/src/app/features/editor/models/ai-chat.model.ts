export type AiConversationType = 'GROUP' | 'PRIVATE';
export type AiMessageAuthorType = 'USER' | 'AI' | 'SYSTEM';
export type AiMemoryScope = 'PROJECT' | 'PRIVATE';
export type AiMemoryType = 'CONVENTION' | 'DECISION' | 'PREFERENCE' | 'NOTE';
export type AiContextScope = 'DIAGRAM' | 'PROJECT';

export interface AiConversation {
  id: string;
  projectId: string;
  type: AiConversationType;
  ownerUserId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface AiMessage {
  id: string;
  clientMessageId?: string | null;
  authorUserId: string | null;
  authorName: string;
  authorType: AiMessageAuthorType;
  content: string;
  createdAt: string;
}

export interface AiMessagesPage {
  messages: AiMessage[];
  nextCursor: string | null;
}

export interface AiMemory {
  id: string;
  projectId: string;
  scope: AiMemoryScope;
  ownerUserId: string | null;
  type: AiMemoryType;
  content: string;
  createdByUserId: string;
  sourceMessageId: string | null;
  createdAt: string;
  updatedAt: string;
}
