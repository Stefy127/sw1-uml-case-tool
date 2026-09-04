import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { DEV_USER_ID } from '../../../../core/config/dev-user.config';
import { DiagramDetail, UmlAttribute, UmlClass } from '../../models/diagram.model';
import { DiagramOperationService } from '../../services/diagram-operation.service';
import { DiagramService } from '../../services/diagram.service';

type EditorTool = 'SELECT' | 'CLASS';
type Visibility = 'PUBLIC' | 'PRIVATE' | 'PROTECTED' | 'PACKAGE';

interface AttributeDraft {
  name: string;
  type: string;
  visibility: Visibility;
  isStatic: boolean;
  isFinal: boolean;
  defaultValue: string;
  primaryKey: boolean;
}

interface RenderedUmlClass {
  umlClass: UmlClass;
  x: number;
  y: number;
  width: number;
  height: number;
}

interface DragState {
  classId: string;
  pointerId: number;
  startPointerX: number;
  startPointerY: number;
  initialX: number;
  initialY: number;
  offsetX: number;
  offsetY: number;
  previewX: number;
  previewY: number;
  isDragging: boolean;
  captureElement: HTMLElement;
}

@Component({
  selector: 'app-editor-page',
  imports: [RouterLink],
  templateUrl: './editor-page.component.html',
  styleUrl: './editor-page.component.scss',
})
export class EditorPageComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly diagramService = inject(DiagramService);
  private readonly operationService = inject(DiagramOperationService);

  readonly diagramId = this.route.snapshot.paramMap.get('diagramId') ?? '';
  readonly diagram = signal<DiagramDetail | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly activeTool = signal<EditorTool>('SELECT');
  readonly selectedClassId = signal<string | null>(null);
  readonly editingClassId = signal<string | null>(null);
  readonly editingName = signal('');
  readonly renaming = signal(false);
  readonly renameError = signal('');
  readonly dragState = signal<DragState | null>(null);
  readonly moving = signal(false);
  readonly selectedClass = computed(() => {
    const umlClass = this.diagram()?.canonicalModel.classes.find(
      (candidate) => candidate.id === this.selectedClassId(),
    );
    return umlClass ? { umlClass } : null;
  });
  readonly attributeDraft = signal<AttributeDraft | null>(null);
  readonly editingAttributeId = signal<string | null>(null);
  readonly attributeSaving = signal(false);
  readonly attributeError = signal('');
  readonly pendingAttributeDeletion = signal<UmlAttribute | null>(null);
  readonly advancedAttributeOptionsOpen = signal(false);
  readonly visibilityOptions: Visibility[] = ['PUBLIC', 'PRIVATE', 'PROTECTED', 'PACKAGE'];
  readonly renderedClasses = computed<RenderedUmlClass[]>(() => {
    const currentDiagram = this.diagram();
    const classes = currentDiagram?.canonicalModel?.classes ?? [];
    const nodes = currentDiagram?.viewState?.nodes ?? [];
    return classes.map((umlClass, index) => {
      const node = nodes.find((candidate) => candidate.classId === umlClass.id);
      return {
        umlClass,
        x: this.previewX(umlClass.id, node?.x ?? 80 + index * 40),
        y: this.previewY(umlClass.id, node?.y ?? 80 + index * 40),
        width: node?.width ?? 240,
        height: node?.height ?? 180,
      };
    });
  });
  tab = 'Propiedades';
  tools = ['⌁', '□', '⌁', '◇', '◈', '↗'];
  toolLabels = ['Seleccionar', 'Clase', 'Relación', 'Agregación', 'Composición', 'Herencia'];

  constructor() {
    if (!this.diagramId) {
      this.error.set('No se pudo identificar el diagrama.');
      this.loading.set(false);
      return;
    }
    this.diagramService.getDiagramById(this.diagramId).subscribe({
      next: (diagram) => {
        this.diagram.set(diagram);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('No se pudo cargar el diagrama.');
        this.loading.set(false);
      },
    });
  }

  setActiveTool(tool: EditorTool): void {
    this.activeTool.set(tool);
  }

  onCanvasClick(event: MouseEvent): void {
    if (this.activeTool() === 'CLASS') {
      const canvas = event.currentTarget as HTMLElement;
      const rect = canvas.getBoundingClientRect();
      this.createClassAt(
        Math.max(0, event.clientX - rect.left - 120),
        Math.max(0, event.clientY - rect.top - 30),
      );
      return;
    }
    this.selectedClassId.set(null);
  }

  selectClass(umlClass: UmlClass, event: MouseEvent): void {
    event.stopPropagation();
    if (this.activeTool() === 'SELECT') this.selectedClassId.set(umlClass.id);
  }

  onClassPointerDown(umlClass: UmlClass, event: PointerEvent): void {
    event.stopPropagation();
    if (this.activeTool() !== 'SELECT' || this.moving()) return;
    const currentDiagram = this.diagram();
    const rendered = this.renderedClasses().find((item) => item.umlClass.id === umlClass.id);
    if (!currentDiagram || !rendered) return;
    const canvas = (event.currentTarget as HTMLElement).closest('.canvas');
    if (!canvas) return;
    const rect = canvas.getBoundingClientRect();
    const pointerX = event.clientX - rect.left;
    const pointerY = event.clientY - rect.top;
    const state: DragState = {
      classId: umlClass.id,
      pointerId: event.pointerId,
      startPointerX: event.clientX,
      startPointerY: event.clientY,
      initialX: rendered.x,
      initialY: rendered.y,
      offsetX: pointerX - rendered.x,
      offsetY: pointerY - rendered.y,
      previewX: rendered.x,
      previewY: rendered.y,
      isDragging: false,
      captureElement: event.currentTarget as HTMLElement,
    };
    this.selectedClassId.set(umlClass.id);
    this.dragState.set(state);
    state.captureElement.setPointerCapture?.(event.pointerId);
  }

  onCanvasPointerMove(event: PointerEvent): void {
    const state = this.dragState();
    if (!state || state.pointerId !== event.pointerId || this.activeTool() !== 'SELECT') return;
    const distance = Math.hypot(
      event.clientX - state.startPointerX,
      event.clientY - state.startPointerY,
    );
    if (!state.isDragging && distance < 4) return;
    const canvas = event.currentTarget as HTMLElement;
    const rect = canvas.getBoundingClientRect();
    const rendered = this.renderedClasses().find((item) => item.umlClass.id === state.classId);
    if (!rendered) return;
    state.isDragging = true;
    state.previewX = this.limitPosition(
      event.clientX - rect.left - state.offsetX,
      canvas.clientWidth,
      rendered.width,
    );
    state.previewY = this.limitPosition(
      event.clientY - rect.top - state.offsetY,
      canvas.clientHeight,
      rendered.height,
    );
    this.dragState.set({ ...state });
  }

  onCanvasPointerUp(event: PointerEvent): void {
    this.finishDrag(event);
  }
  onCanvasPointerCancel(event: PointerEvent): void {
    this.finishDrag(event, true);
  }

  private finishDrag(event: PointerEvent, cancelled = false): void {
    const state = this.dragState();
    if (!state || state.pointerId !== event.pointerId) return;
    state.captureElement.releasePointerCapture?.(event.pointerId);
    if (cancelled || !state.isDragging) {
      this.dragState.set(null);
      return;
    }
    const { classId, previewX, previewY, initialX, initialY } = state;
    this.dragState.set({ ...state });
    if (previewX === initialX && previewY === initialY) return this.dragState.set(null);
    const currentDiagram = this.diagram();
    if (!currentDiagram) return this.dragState.set(null);
    this.moving.set(true);
    this.operationService
      .execute(this.diagramId, {
        operation: {
          operationId: crypto.randomUUID(),
          diagramId: this.diagramId,
          userId: DEV_USER_ID,
          baseVersion: currentDiagram.version,
          type: 'MOVE_CLASS',
          payload: { classId, x: previewX, y: previewY },
        },
      })
      .subscribe({
        next: (response) => {
          this.diagram.update((diagram) =>
            diagram
              ? {
                  ...diagram,
                  version: response.newVersion,
                  canonicalModel: response.canonicalModel,
                  viewState: response.viewState,
                }
              : diagram,
          );
          this.moving.set(false);
          this.dragState.set(null);
        },
        error: (error: unknown) => {
          this.moving.set(false);
          this.dragState.set(null);
          this.error.set(this.operationError(error, 'No se pudo mover la clase.'));
          console.error(
            'No se pudo mover la clase.',
            error instanceof HttpErrorResponse ? error.status : 'Error HTTP',
          );
        },
      });
  }

  private previewX(classId: string, fallback: number): number {
    const state = this.dragState();
    return state?.classId === classId && state.isDragging ? state.previewX : fallback;
  }
  private previewY(classId: string, fallback: number): number {
    const state = this.dragState();
    return state?.classId === classId && state.isDragging ? state.previewY : fallback;
  }
  private limitPosition(value: number, canvasSize: number, itemSize: number): number {
    return Math.max(0, Math.min(value, Math.max(0, canvasSize - itemSize)));
  }

  startAddAttribute(): void {
    if (!this.selectedClass()) return;
    this.editingAttributeId.set(null);
    this.attributeError.set('');
    this.advancedAttributeOptionsOpen.set(false);
    this.attributeDraft.set({
      name: '',
      type: 'String',
      visibility: 'PRIVATE',
      isStatic: false,
      isFinal: false,
      defaultValue: '',
      primaryKey: false,
    });
  }

  startEditAttribute(attribute: UmlAttribute): void {
    this.editingAttributeId.set(attribute.id);
    this.attributeError.set('');
    this.advancedAttributeOptionsOpen.set(
      attribute.primaryKey || attribute.isStatic || attribute.isFinal || !!attribute.defaultValue,
    );
    this.attributeDraft.set({
      name: attribute.name,
      type: attribute.type,
      visibility: attribute.visibility as Visibility,
      isStatic: attribute.isStatic,
      isFinal: attribute.isFinal,
      defaultValue: attribute.defaultValue ?? '',
      primaryKey: attribute.primaryKey,
    });
  }

  updateAttributeDraft(field: keyof AttributeDraft, value: string | boolean): void {
    this.attributeDraft.update((draft) =>
      draft ? ({ ...draft, [field]: value } as AttributeDraft) : draft,
    );
  }

  cancelAttributeEdit(): void {
    if (!this.attributeSaving()) {
      this.attributeDraft.set(null);
      this.editingAttributeId.set(null);
      this.attributeError.set('');
      this.advancedAttributeOptionsOpen.set(false);
    }
  }

  saveAttribute(): void {
    if (this.attributeSaving()) return;
    const currentDiagram = this.diagram();
    const currentClass = this.selectedClass()?.umlClass;
    const draft = this.attributeDraft();
    if (!currentDiagram || !currentClass || !draft) return;
    const name = draft.name.trim();
    const type = draft.type.trim();
    if (!name || !type) {
      this.attributeError.set('Nombre y tipo son obligatorios.');
      return;
    }
    if (!this.visibilityOptions.includes(draft.visibility)) {
      this.attributeError.set('La visibilidad no es válida.');
      return;
    }
    const editingId = this.editingAttributeId();
    if (
      currentClass.attributes.some(
        (attribute) =>
          attribute.id !== editingId && attribute.name.toLowerCase() === name.toLowerCase(),
      )
    ) {
      this.attributeError.set('Ya existe un atributo con ese nombre.');
      return;
    }
    const existing = currentClass.attributes.find((attribute) => attribute.id === editingId);
    if (
      existing &&
      existing.name === name &&
      existing.type === type &&
      existing.visibility === draft.visibility &&
      existing.isStatic === draft.isStatic &&
      existing.isFinal === draft.isFinal &&
      (existing.defaultValue ?? '') === draft.defaultValue.trim() &&
      existing.primaryKey === draft.primaryKey
    ) {
      this.cancelAttributeEdit();
      return;
    }
    const payload = editingId
      ? {
          classId: currentClass.id,
          attributeId: editingId,
          name,
          type,
          visibility: draft.visibility,
          isStatic: draft.isStatic,
          isFinal: draft.isFinal,
          defaultValue: draft.defaultValue.trim() || null,
          primaryKey: draft.primaryKey,
        }
      : {
          classId: currentClass.id,
          attribute: {
            id: crypto.randomUUID(),
            name,
            type,
            visibility: draft.visibility,
            isStatic: draft.isStatic,
            isFinal: draft.isFinal,
            defaultValue: draft.defaultValue.trim() || null,
            primaryKey: draft.primaryKey,
          },
        };
    this.attributeSaving.set(true);
    this.attributeError.set('');
    this.operationService
      .execute(this.diagramId, {
        operation: {
          operationId: crypto.randomUUID(),
          diagramId: this.diagramId,
          userId: DEV_USER_ID,
          baseVersion: currentDiagram.version,
          type: editingId ? 'UPDATE_ATTRIBUTE' : 'ADD_ATTRIBUTE',
          payload,
        },
      })
      .subscribe({
        next: (response) => {
          this.applyOperationResponse(response);
          this.attributeSaving.set(false);
          this.cancelAttributeEdit();
        },
        error: (error: unknown) => {
          this.attributeSaving.set(false);
          this.attributeError.set(this.operationError(error, 'No se pudo guardar el atributo.'));
          console.error(
            'No se pudo guardar el atributo.',
            error instanceof HttpErrorResponse ? error.status : 'Error HTTP',
          );
        },
      });
  }

  removeAttribute(attribute: UmlAttribute): void {
    if (!this.selectedClass() || this.attributeSaving()) return;
    this.attributeError.set('');
    this.pendingAttributeDeletion.set(attribute);
  }

  cancelAttributeDeletion(): void {
    if (!this.attributeSaving()) this.pendingAttributeDeletion.set(null);
  }

  confirmAttributeDeletion(): void {
    const currentDiagram = this.diagram();
    const currentClass = this.selectedClass()?.umlClass;
    const attribute = this.pendingAttributeDeletion();
    if (!currentDiagram || !currentClass || !attribute || this.attributeSaving()) return;
    this.attributeSaving.set(true);
    this.attributeError.set('');
    this.operationService
      .execute(this.diagramId, {
        operation: {
          operationId: crypto.randomUUID(),
          diagramId: this.diagramId,
          userId: DEV_USER_ID,
          baseVersion: currentDiagram.version,
          type: 'REMOVE_ATTRIBUTE',
          payload: { classId: currentClass.id, attributeId: attribute.id },
        },
      })
      .subscribe({
        next: (response) => {
          this.applyOperationResponse(response);
          this.attributeSaving.set(false);
          this.pendingAttributeDeletion.set(null);
        },
        error: (error: unknown) => {
          this.attributeSaving.set(false);
          this.attributeError.set(this.operationError(error, 'No se pudo eliminar el atributo.'));
          console.error(
            'No se pudo eliminar el atributo.',
            error instanceof HttpErrorResponse ? error.status : 'Error HTTP',
          );
        },
      });
  }

  private applyOperationResponse(response: {
    newVersion: number;
    canonicalModel: DiagramDetail['canonicalModel'];
    viewState: DiagramDetail['viewState'];
  }): void {
    this.diagram.update((diagram) =>
      diagram
        ? {
            ...diagram,
            version: response.newVersion,
            canonicalModel: response.canonicalModel,
            viewState: response.viewState,
          }
        : diagram,
    );
  }

  createClassAt(x: number, y: number): void {
    const currentDiagram = this.diagram();
    if (!currentDiagram || !this.diagramId) return;
    const classId = crypto.randomUUID();
    const name = this.nextClassName(currentDiagram);
    this.operationService
      .execute(this.diagramId, {
        operation: {
          operationId: crypto.randomUUID(),
          diagramId: this.diagramId,
          userId: DEV_USER_ID,
          baseVersion: currentDiagram.version,
          type: 'CREATE_CLASS',
          payload: { classId, name, isAbstract: false, x, y, width: 240, height: 180 },
        },
      })
      .subscribe({
        next: (response) => {
          this.diagram.update((diagram) =>
            diagram
              ? {
                  ...diagram,
                  version: response.newVersion,
                  canonicalModel: response.canonicalModel,
                  viewState: response.viewState,
                }
              : diagram,
          );
          this.selectedClassId.set(classId);
          this.activeTool.set('SELECT');
          this.beginInlineEdit(classId);
        },
        error: (error: unknown) => {
          this.activeTool.set('SELECT');
          this.error.set(this.operationError(error, 'No se pudo crear la clase.'));
          console.error(
            'No se pudo crear la clase.',
            error instanceof HttpErrorResponse ? error.status : 'Error HTTP',
          );
        },
      });
  }

  private nextClassName(diagram: DiagramDetail): string {
    const names = new Set(
      diagram.canonicalModel.classes.map((umlClass) => umlClass.name.toLowerCase()),
    );
    let index = 1;
    while (names.has(`clase${index}`.toLowerCase())) index++;
    return `Clase${index}`;
  }

  beginInlineEdit(classId: string): void {
    const umlClass = this.diagram()?.canonicalModel.classes.find(
      (candidate) => candidate.id === classId,
    );
    if (!umlClass) return;
    this.selectedClassId.set(classId);
    this.renameError.set('');
    this.editingName.set(umlClass.name);
    this.editingClassId.set(classId);
    setTimeout(() => {
      const input = document.querySelector<HTMLInputElement>(`[data-class-name="${classId}"]`);
      input?.focus();
      input?.select();
    });
  }

  cancelInlineEdit(): void {
    this.editingClassId.set(null);
    this.renameError.set('');
  }

  confirmInlineEdit(): void {
    if (this.renaming()) return;
    const classId = this.editingClassId();
    const currentDiagram = this.diagram();
    const name = this.editingName().trim();
    if (!classId || !currentDiagram) return;
    if (!name) {
      this.renameError.set('El nombre no puede estar vacío.');
      return;
    }
    const duplicate = currentDiagram.canonicalModel.classes.some(
      (umlClass) => umlClass.id !== classId && umlClass.name.toLowerCase() === name.toLowerCase(),
    );
    if (duplicate) {
      this.renameError.set('Ya existe una clase con ese nombre.');
      return;
    }
    const currentClass = currentDiagram.canonicalModel.classes.find(
      (umlClass) => umlClass.id === classId,
    );
    if (currentClass?.name === name) {
      this.cancelInlineEdit();
      return;
    }
    this.renaming.set(true);
    this.operationService
      .execute(this.diagramId, {
        operation: {
          operationId: crypto.randomUUID(),
          diagramId: this.diagramId,
          userId: DEV_USER_ID,
          baseVersion: currentDiagram.version,
          type: 'RENAME_CLASS',
          payload: { classId, name },
        },
      })
      .subscribe({
        next: (response) => {
          this.diagram.update((diagram) =>
            diagram
              ? {
                  ...diagram,
                  version: response.newVersion,
                  canonicalModel: response.canonicalModel,
                  viewState: response.viewState,
                }
              : diagram,
          );
          this.renaming.set(false);
          this.cancelInlineEdit();
        },
        error: (error: unknown) => {
          this.renaming.set(false);
          this.renameError.set(this.operationError(error, 'No se pudo renombrar la clase.'));
          console.error(
            'No se pudo renombrar la clase.',
            error instanceof HttpErrorResponse ? error.status : 'Error HTTP',
          );
        },
      });
  }

  private operationError(error: unknown, fallback: string): string {
    return error instanceof HttpErrorResponse && error.status === 409
      ? 'El diagrama cambió en otra sesión. Recarga para obtener la versión más reciente.'
      : fallback;
  }

  isAbstract(umlClass: UmlClass): boolean {
    return umlClass.isAbstract ?? umlClass.abstract ?? false;
  }
  isStatic(member: { isStatic: boolean; static?: boolean }): boolean {
    return member.isStatic ?? member.static ?? false;
  }
  visibilitySymbol(visibility: string | undefined): string {
    return { PUBLIC: '+', PRIVATE: '-', PROTECTED: '#', PACKAGE: '~' }[visibility ?? ''] ?? '~';
  }
  formatAttribute(attribute: { name: string; type: string; defaultValue?: string | null }): string {
    const defaultValue = attribute.defaultValue?.trim();
    return `${attribute.name}: ${attribute.type}${defaultValue ? ` = ${defaultValue}` : ''}`;
  }
  formatMethod(method: UmlClass['methods'][number]): string {
    return `${method.name}(${method.parameters.map((parameter) => `${parameter.name}: ${parameter.type}`).join(', ')}): ${method.returnType}`;
  }
}
