import { Injectable, signal } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { API_BASE_URL } from '../../../core/config/api.config';
import { ExecuteDiagramOperationRequest, OperationExecutionResponse } from '../models/diagram-operation.model';

export type CollaborationStatus = 'DISCONNECTED' | 'CONNECTING' | 'CONNECTED' | 'RECONNECTING' | 'ERROR';
export interface CollaborationEvent { type: string; diagramId?: string; operationId?: string; reason?: string; serverVersion?: number; version?: number; users?: Array<{ userId: string }>; result?: OperationExecutionResponse; }

@Injectable({ providedIn: 'root' })
export class CollaborationService {
  readonly status = signal<CollaborationStatus>('DISCONNECTED');
  readonly joined = signal(false);
  readonly events = new Subject<CollaborationEvent>();
  private socket: WebSocket | null = null;
  private diagramId = '';
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private reconnectAttempt = 0;
  private knownVersion = 0;
  private needsResync = false;
  private pending = new Map<string, { resolve: (response: OperationExecutionResponse) => void; reject: (error: unknown) => void }>();

  connect(diagramId: string, knownVersion: number): void {
    this.diagramId = diagramId;
    this.knownVersion = knownVersion;
    this.joined.set(false);
    this.needsResync = false;
    this.closeSocket(false);
    const token = localStorage.getItem('sw1.auth.token');
    if (!token || typeof WebSocket === 'undefined') { this.status.set('ERROR'); return; }
    this.status.set(this.reconnectAttempt ? 'RECONNECTING' : 'CONNECTING');
    const wsBase = API_BASE_URL.replace(/^http/, 'ws').replace(/\/api$/, '');
    this.socket = new WebSocket(`${wsBase}/ws/collaboration?token=${encodeURIComponent(token)}`);
    this.socket.onopen = () => {
      this.reconnectAttempt = 0;
      this.status.set('CONNECTED');
      this.send({ type: 'JOIN_DIAGRAM', diagramId, knownVersion: this.knownVersion });
    };
    this.socket.onmessage = (event) => this.handleMessage(JSON.parse(event.data) as CollaborationEvent);
    this.socket.onerror = () => this.status.set('ERROR');
    this.socket.onclose = () => {
      this.socket = null;
      if (this.diagramId) this.scheduleReconnect();
      else this.status.set('DISCONNECTED');
    };
  }

  disconnect(): void {
    this.diagramId = '';
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
    this.reconnectTimer = null;
    this.closeSocket(true);
    this.status.set('DISCONNECTED');
    this.joined.set(false);
    for (const pending of this.pending.values()) pending.reject(new Error('WebSocket disconnected'));
    this.pending.clear();
  }

  hasActiveDiagram(): boolean { return !!this.diagramId && typeof WebSocket !== 'undefined' && !!localStorage.getItem('sw1.auth.token'); }
  isConnected(): boolean { return this.status() === 'CONNECTED' && this.joined() && this.socket?.readyState === WebSocket.OPEN; }
  markResynced(version: number): void { this.knownVersion = version; this.needsResync = false; this.joined.set(true); this.flushPending(); }

  sendOperation(request: ExecuteDiagramOperationRequest): Observable<OperationExecutionResponse> {
    return new Observable((subscriber) => {
      if (!this.hasActiveDiagram()) { subscriber.error(new Error('WebSocket is not configured')); return; }
      const operation = request.operation;
      this.pending.set(operation.operationId, { resolve: (response) => { this.pendingOperations.delete(operation.operationId); subscriber.next(response); subscriber.complete(); }, reject: (error) => { this.pendingOperations.delete(operation.operationId); subscriber.error(error); } });
      this.pendingOperations.set(operation.operationId, operation);
      if (this.isConnected()) this.sendOperationFrame(operation);
      return () => { this.pending.delete(operation.operationId); this.pendingOperations.delete(operation.operationId); };
    });
  }

  private handleMessage(message: CollaborationEvent): void {
    console.debug('[WS RECEIVE]', message.type, message.operationId ?? '', message.version ?? '');
    if (message.type === 'JOINED') {
      this.joined.set(!this.needsResync);
      if (!this.needsResync) this.flushPending();
      this.events.next(message);
      return;
    }
    if (message.type === 'OPERATION_APPLIED' && message.result) {
      this.knownVersion = message.result.newVersion;
      const pending = message.operationId ? this.pending.get(message.operationId) : undefined;
      if (pending) { this.pending.delete(message.operationId!); pending.resolve(message.result); return; }
    }
    if (message.type === 'RESYNC_REQUIRED') {
      this.needsResync = true;
      this.joined.set(false);
      if (message.serverVersion !== undefined) this.knownVersion = message.serverVersion;
    }
    if (message.type === 'OPERATION_REJECTED' && message.operationId) {
      const pending = this.pending.get(message.operationId);
      if (pending) { this.pending.delete(message.operationId); pending.reject(message); }
    }
    this.events.next(message);
  }

  private send(message: object): void { this.socket?.send(JSON.stringify(message)); }
  private sendOperationFrame(operation: ExecuteDiagramOperationRequest['operation']): void {
    console.debug('[WS SEND]', operation.operationId, operation.baseVersion, operation.type);
    this.send({ type: 'APPLY_OPERATION', diagramId: operation.diagramId, operationId: operation.operationId, baseVersion: operation.baseVersion, operation });
  }
  private flushPending(): void {
    if (!this.isConnected()) return;
    for (const operationId of this.pending.keys()) {
      const operation = this.pendingOperations.get(operationId);
      if (operation) this.sendOperationFrame(operation);
    }
  }
  private pendingOperations = new Map<string, ExecuteDiagramOperationRequest['operation']>();
  private scheduleReconnect(): void {
    if (this.reconnectTimer) return;
    this.reconnectAttempt++;
    this.status.set('RECONNECTING');
    const delay = Math.min(1000 * 2 ** Math.min(this.reconnectAttempt - 1, 4), 16000);
    this.reconnectTimer = setTimeout(() => { this.reconnectTimer = null; this.connect(this.diagramId, this.knownVersion); }, delay);
  }
  private closeSocket(clearDiagram: boolean): void {
    if (clearDiagram) this.diagramId = '';
    if (this.socket) { this.socket.onclose = null; this.socket.close(); this.socket = null; }
  }
}
