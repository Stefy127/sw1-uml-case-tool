import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { DEV_USER_ID } from '../../../../core/config/dev-user.config';
import {
  ChangeMultiplicityPayload,
  ChangeNavigabilityPayload,
  ChangeRelationRolesPayload,
  ChangeRelationTypePayload,
} from '../../models/diagram-operation.model';
import {
  DiagramDetail,
  Multiplicity,
  UmlAttribute,
  UmlClass,
  UmlMethod,
  UmlParameter,
  UmlRelation,
  AssociationClassLink,
} from '../../models/diagram.model';
import { DiagramOperationService } from '../../services/diagram-operation.service';
import { DiagramService } from '../../services/diagram.service';
import { XmiImportService } from '../../services/xmi-import.service';
import { XmiImportResponse } from '../../models/xmi-import.model';

type EditorTool = 'SELECT' | 'CLASS' | 'RELATION';
type RelationType =
  | 'ASSOCIATION'
  | 'AGGREGATION'
  | 'COMPOSITION'
  | 'INHERITANCE'
  | 'DEPENDENCY';
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

interface MethodDraft {
  name: string;
  returnType: string;
  visibility: Visibility;
  isStatic: boolean;
}

interface ParameterDraft {
  name: string;
  type: string;
}

interface RenderedUmlClass {
  umlClass: UmlClass;
  x: number;
  y: number;
  width: number;
  height: number;
  headerColor: string | null;
  bodyColor: string | null;
  borderColor: string | null;
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

interface ResizeState {
  classId: string;
  pointerId: number;
  startPointerX: number;
  startPointerY: number;
  initialWidth: number;
  initialHeight: number;
  previewWidth: number;
  previewHeight: number;
  isResizing: boolean;
  captureElement: HTMLElement;
}

interface PanState {
  pointerId: number;
  startPointerX: number;
  startPointerY: number;
  initialPanX: number;
  initialPanY: number;
  isDragging: boolean;
  captureElement: HTMLElement;
}

interface RenderedRelation {
  relation: UmlRelation;
  path: string;
  markerStart: boolean;
  markerEnd: boolean;
  sourceLabelX: number;
  sourceLabelY: number;
  targetLabelX: number;
  targetLabelY: number;
  sourceRoleX: number;
  sourceRoleY: number;
  targetRoleX: number;
  targetRoleY: number;
}

interface RenderedAssociationClassLink {
  link: AssociationClassLink;
  path: string;
}

interface RelationMultiplicityDraft {
  source: Multiplicity;
  target: Multiplicity;
}

interface RelationRolesDraft {
  source: string;
  target: string;
}

type RelationPropertyOperation =
  | { type: 'CHANGE_RELATION_TYPE'; payload: ChangeRelationTypePayload }
  | { type: 'CHANGE_MULTIPLICITY'; payload: ChangeMultiplicityPayload }
  | { type: 'CHANGE_RELATION_ROLES'; payload: ChangeRelationRolesPayload }
  | { type: 'CHANGE_NAVIGABILITY'; payload: ChangeNavigabilityPayload };

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
  private readonly xmiImportService = inject(XmiImportService);
  private readonly defaultNodeWidth = 240;
  private readonly defaultNodeHeight = 180;

  readonly diagramId = this.route.snapshot.paramMap.get('diagramId') ?? '';
  readonly diagram = signal<DiagramDetail | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly activeTool = signal<EditorTool>('SELECT');
  readonly relationCreationType = signal<RelationType>('ASSOCIATION');
  readonly selectedClassId = signal<string | null>(null);
  readonly selectedRelationId = signal<string | null>(null);
  readonly relationSourceClassId = signal<string | null>(null);
  readonly relationSaving = signal(false);
  readonly relationError = signal('');
  readonly pendingRelationDeletion = signal<UmlRelation | null>(null);
  readonly relationMultiplicityDraft = signal<RelationMultiplicityDraft | null>(null);
  readonly relationRolesDraft = signal<RelationRolesDraft | null>(null);
  readonly relationNavigabilityDraft = signal({ source: false, target: false });
  readonly relationPropertySaving = signal(false);
  readonly associationClassSaving = signal(false);
  readonly associationClassError = signal('');
  readonly editingClassId = signal<string | null>(null);
  readonly editingName = signal('');
  readonly renaming = signal(false);
  readonly renameError = signal('');
  readonly dragState = signal<DragState | null>(null);
  readonly moving = signal(false);
  readonly resizeState = signal<ResizeState | null>(null);
  readonly resizing = signal(false);
  readonly zoom = signal(1);
  readonly panX = signal(0);
  readonly panY = signal(0);
  readonly spacePressed = signal(false);
  readonly panState = signal<PanState | null>(null);
  readonly suppressCanvasClick = signal(false);
  readonly worldTransform = computed(() => `translate(${this.panX()}px, ${this.panY()}px) scale(${this.zoom()})`);
  readonly zoomPercent = computed(() => Math.round(this.zoom() * 100));
  readonly styleSaving = signal(false);
  readonly styleError = signal('');
  readonly importDialogOpen = signal(false);
  readonly importFile = signal<File | null>(null);
  readonly importPreview = signal<XmiImportResponse | null>(null);
  readonly importLoading = signal(false);
  readonly importError = signal('');
  readonly selectedClass = computed(() => {
    const umlClass = this.diagram()?.canonicalModel.classes.find(
      (candidate) => candidate.id === this.selectedClassId(),
    );
    return umlClass ? { umlClass } : null;
  });
  readonly selectedNode = computed(() => {
    const currentDiagram = this.diagram();
    const classId = this.selectedClassId();
    return currentDiagram?.viewState.nodes.find((node) => node.classId === classId) ?? null;
  });
  readonly selectedRelation = computed(() => {
    const currentDiagram = this.diagram();
    const relation = currentDiagram?.canonicalModel.relations.find(
      (candidate) => candidate.id === this.selectedRelationId(),
    );
    if (!relation || !currentDiagram) return null;
    return {
      relation,
      sourceName:
        currentDiagram.canonicalModel.classes.find((item) => item.id === relation.sourceClassId)
          ?.name ?? relation.sourceClassId,
      targetName:
        currentDiagram.canonicalModel.classes.find((item) => item.id === relation.targetClassId)
          ?.name ?? relation.targetClassId,
    };
  });
  readonly selectedAssociationClassLink = computed(() => {
    const relationId = this.selectedRelationId();
    return this.diagram()?.canonicalModel.associationClassLinks?.find(
      (link) => link.relationId === relationId,
    ) ?? null;
  });
  readonly editingMethod = computed(() => {
    const umlClass = this.selectedClass()?.umlClass;
    const methodId = this.editingMethodId();
    return umlClass?.methods.find((method) => method.id === methodId) ?? null;
  });
  readonly attributeDraft = signal<AttributeDraft | null>(null);
  readonly editingAttributeId = signal<string | null>(null);
  readonly attributeSaving = signal(false);
  readonly attributeError = signal('');
  readonly pendingAttributeDeletion = signal<UmlAttribute | null>(null);
  readonly advancedAttributeOptionsOpen = signal(false);
  readonly methodDraft = signal<MethodDraft | null>(null);
  readonly editingMethodId = signal<string | null>(null);
  readonly parameterDraft = signal<ParameterDraft | null>(null);
  readonly editingParameterId = signal<string | null>(null);
  readonly pendingMethodDeletion = signal<UmlMethod | null>(null);
  readonly pendingParameterDeletion = signal<UmlParameter | null>(null);
  readonly pendingClassDeletion = signal<UmlClass | null>(null);
  readonly classDeleteSaving = signal(false);
  readonly classDeleteError = signal('');
  readonly methodSaving = signal(false);
  readonly methodError = signal('');
  readonly parameterError = signal('');
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
        width: this.previewWidth(node?.classId ?? umlClass.id, node?.width ?? 240),
        height: this.previewHeight(node?.classId ?? umlClass.id, node?.height ?? 180),
        headerColor: node?.headerColor ?? null,
        bodyColor: node?.bodyColor ?? null,
        borderColor: node?.borderColor ?? null,
      };
    });
  });
  readonly renderedRelations = computed<RenderedRelation[]>(() => {
    const currentDiagram = this.diagram();
    if (!currentDiagram) return [];
    const classes = this.renderedClasses();
    return currentDiagram.canonicalModel.relations
      .map((relation) => this.renderRelation(relation, classes))
      .filter((item): item is RenderedRelation => item !== null);
  });
  readonly renderedAssociationClassLinks = computed<RenderedAssociationClassLink[]>(() => {
    const currentDiagram = this.diagram();
    if (!currentDiagram) return [];
    const classes = this.renderedClasses();
    return (currentDiagram.canonicalModel.associationClassLinks ?? [])
      .map((link) => {
        const associationClass = classes.find((item) => item.umlClass.id === link.classId);
        const relation = currentDiagram.canonicalModel.relations.find(
          (item) => item.id === link.relationId,
        );
        if (!associationClass || !relation) return null;
        const midpoint = this.relationMidpoint(relation, classes);
        return {
          link,
          path: `M ${associationClass.x + associationClass.width / 2} ${associationClass.y} L ${midpoint.x} ${midpoint.y}`,
        };
      })
      .filter((item): item is RenderedAssociationClassLink => item !== null);
  });
  tab = 'Propiedades';
  tools = ['⌁', '□', '⌁', '◇', '◈', '↗', '⇢'];
  toolLabels = [
    'Seleccionar',
    'Clase',
    'Relación',
    'Agregación',
    'Composición',
    'Herencia',
    'Dependencia',
  ];
  readonly relationTypeOptions: Array<{ value: RelationType; label: string }> = [
    { value: 'ASSOCIATION', label: 'Asociación' },
    { value: 'AGGREGATION', label: 'Agregación' },
    { value: 'COMPOSITION', label: 'Composición' },
    { value: 'INHERITANCE', label: 'Herencia' },
    { value: 'DEPENDENCY', label: 'Dependencia' },
  ];

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
    if (tool !== 'RELATION') this.relationSourceClassId.set(null);
    if (tool !== 'RELATION') this.relationError.set('');
  }

  setRelationTool(type: RelationType): void {
    this.relationCreationType.set(type);
    this.setActiveTool('RELATION');
  }

  toolbarRelationType(index: number): RelationType | null {
    return ({
      2: 'ASSOCIATION',
      3: 'AGGREGATION',
      4: 'COMPOSITION',
      5: 'INHERITANCE',
      6: 'DEPENDENCY',
    } as Record<number, RelationType>)[index] ?? null;
  }

  activateToolbarTool(index: number): void {
    if (index === 0) {
      this.setActiveTool('SELECT');
      return;
    }
    if (index === 1) {
      this.setActiveTool('CLASS');
      return;
    }
    const relationType = this.toolbarRelationType(index);
    if (relationType) this.setRelationTool(relationType);
  }

  onEditorKeydown(event: KeyboardEvent): void {
    if (event.code === 'Space' && !this.isEditableTarget(event.target)) {
      this.spacePressed.set(true);
      event.preventDefault();
      return;
    }
    if (event.key === 'Escape' && this.activeTool() === 'RELATION') {
      this.setActiveTool('SELECT');
      event.preventDefault();
    }
  }

  onEditorKeyup(event: KeyboardEvent): void {
    if (event.code === 'Space') this.spacePressed.set(false);
  }

  openImportDialog(): void {
    this.importDialogOpen.set(true);
    this.importError.set('');
  }

  closeImportDialog(): void {
    if (this.importLoading()) return;
    this.importDialogOpen.set(false);
    this.importFile.set(null);
    this.importPreview.set(null);
    this.importError.set('');
  }

  selectImportFile(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.importFile.set(file);
    this.importPreview.set(null);
    this.importError.set('');
  }

  analyzeImport(): void {
    const file = this.importFile();
    if (!file || this.importLoading()) return;
    this.importLoading.set(true);
    this.importError.set('');
    this.xmiImportService.preview(file).subscribe({
      next: (preview) => {
        this.importPreview.set(preview);
        this.importLoading.set(false);
      },
      error: () => {
        this.importError.set('No se pudo analizar el archivo XMI.');
        this.importLoading.set(false);
      },
    });
  }

  applyImport(): void {
    const file = this.importFile();
    const current = this.diagram();
    if (!file || !current || this.importLoading()) return;
    this.importLoading.set(true);
    this.importError.set('');
    this.xmiImportService.apply(this.diagramId, current.version, file).subscribe({
      next: (result) => {
        this.diagram.set({
          ...current,
          canonicalModel: result.canonicalModel,
          viewState: result.viewState,
          version: result.canonicalModel.version,
          updatedAt: new Date().toISOString(),
        });
        this.importLoading.set(false);
        this.closeImportDialog();
        this.resetViewport();
      },
      error: (error: HttpErrorResponse) => {
        this.importError.set(error.status === 409
          ? 'El diagrama cambió en otra sesión. Recarga para obtener la versión más reciente.'
          : 'No se pudo importar el diagrama.');
        this.importLoading.set(false);
      },
    });
  }

  hasDiagramContent(): boolean {
    const current = this.diagram();
    return Boolean(current && (
      current.canonicalModel.classes.length > 0 ||
      current.canonicalModel.relations.length > 0
    ));
  }

  handleToolbarClick(event: MouseEvent): void {
    const button = (event.target as HTMLElement).closest('button');
    const toolbar = button?.closest('.toolbar');
    if (!button || !toolbar) return;
    const buttons = Array.from(toolbar.querySelectorAll('button'));
    if (buttons.indexOf(button) === 9) {
      if (this.selectedRelationId()) {
        this.removeSelectedRelation();
      } else {
        this.removeSelectedClass();
      }
    }
  }

  removeSelectedClass(): void {
    const currentClass = this.selectedClass()?.umlClass;
    if (!currentClass || this.classDeleteSaving()) {
      if (!currentClass) {
        const message = 'Selecciona una clase para eliminarla.';
        this.classDeleteError.set(message);
        this.error.set(message);
      }
      return;
    }
    this.classDeleteError.set('');
    this.pendingClassDeletion.set(currentClass);
  }

  cancelClassDeletion(): void {
    if (this.classDeleteSaving()) return;
    this.pendingClassDeletion.set(null);
    this.classDeleteError.set('');
  }

  confirmClassDeletion(): void {
    const currentDiagram = this.diagram();
    const currentClass = this.pendingClassDeletion();
    if (!currentDiagram || !currentClass || this.classDeleteSaving()) return;

    this.classDeleteSaving.set(true);
    this.classDeleteError.set('');
    this.operationService
      .execute(this.diagramId, {
        operation: {
          operationId: crypto.randomUUID(),
          diagramId: this.diagramId,
          userId: DEV_USER_ID,
          baseVersion: currentDiagram.version,
          type: 'DELETE_CLASS',
          payload: { classId: currentClass.id },
        },
      })
      .subscribe({
        next: (response) => {
          this.applyOperationResponse(response);
          this.classDeleteSaving.set(false);
          this.pendingClassDeletion.set(null);
          this.classDeleteError.set('');
          this.selectedClassId.set(null);
          this.editingClassId.set(null);
          this.editingMethodId.set(null);
          this.parameterDraft.set(null);
        },
        error: (error: unknown) => {
          this.classDeleteSaving.set(false);
          this.classDeleteError.set(
            this.operationError(error, 'No se pudo eliminar la clase.'),
          );
          console.error(
            'No se pudo eliminar la clase.',
            error instanceof HttpErrorResponse ? error.status : 'Error HTTP',
          );
        },
      });
  }

  changeClassStyle(
    field: 'headerColor' | 'bodyColor' | 'borderColor',
    event: Event,
  ): void {
    const color = (event.target as HTMLInputElement).value;
    const node = this.selectedNode();
    if (!node || this.styleSaving()) return;
    this.executeClassStyle({
      classId: node.classId,
      headerColor: field === 'headerColor' ? color : (node.headerColor ?? null),
      bodyColor: field === 'bodyColor' ? color : (node.bodyColor ?? null),
      borderColor: field === 'borderColor' ? color : (node.borderColor ?? null),
    });
  }

  resetClassStyle(): void {
    const node = this.selectedNode();
    if (!node || this.styleSaving()) return;
    this.executeClassStyle({
      classId: node.classId,
      headerColor: null,
      bodyColor: null,
      borderColor: null,
    });
  }

  classStyleColor(color: string | null | undefined, variable: string, fallback: string): string {
    if (color) return color;
    if (typeof document === 'undefined') return fallback;
    return getComputedStyle(document.documentElement).getPropertyValue(variable).trim() || fallback;
  }

  private executeClassStyle(payload: {
    classId: string;
    headerColor: string | null;
    bodyColor: string | null;
    borderColor: string | null;
  }): void {
    const currentDiagram = this.diagram();
    if (!currentDiagram) return;
    this.styleSaving.set(true);
    this.styleError.set('');
    this.operationService
      .execute(this.diagramId, {
        operation: {
          operationId: crypto.randomUUID(),
          diagramId: this.diagramId,
          userId: DEV_USER_ID,
          baseVersion: currentDiagram.version,
          type: 'UPDATE_CLASS_STYLE',
          payload,
        },
      })
      .subscribe({
        next: (response) => {
          this.applyOperationResponse(response);
          this.styleSaving.set(false);
        },
        error: (error: unknown) => {
          this.styleSaving.set(false);
          this.styleError.set(this.operationError(error, 'No se pudo actualizar la apariencia.'));
          console.error(
            'No se pudo actualizar la apariencia.',
            error instanceof HttpErrorResponse ? error.status : 'Error HTTP',
          );
        },
      });
  }

  onCanvasClick(event: MouseEvent): void {
    if (this.suppressCanvasClick()) {
      this.suppressCanvasClick.set(false);
      return;
    }
    if (this.activeTool() === 'CLASS') {
      const canvas = event.currentTarget as HTMLElement;
      const point = this.screenToWorld(event.clientX, event.clientY, canvas);
      this.createClassAt(point.x - 120, point.y - 30);
      return;
    }
    if (this.activeTool() === 'RELATION') return;
    this.selectedClassId.set(null);
    this.selectedRelationId.set(null);
  }

  selectClass(umlClass: UmlClass, event: MouseEvent): void {
    event.stopPropagation();
    if (this.activeTool() === 'RELATION') {
      const sourceClassId = this.relationSourceClassId();
      if (!sourceClassId) {
        this.relationSourceClassId.set(umlClass.id);
        this.selectedClassId.set(umlClass.id);
        this.selectedRelationId.set(null);
      } else {
        this.createRelation(sourceClassId, umlClass.id);
      }
      return;
    }
    if (this.activeTool() === 'SELECT') {
      this.selectedClassId.set(umlClass.id);
      this.selectedRelationId.set(null);
    }
  }

  selectRelation(relation: UmlRelation, event: MouseEvent): void {
    event.stopPropagation();
    if (this.activeTool() !== 'SELECT') return;
    this.selectedRelationId.set(relation.id);
    this.selectedClassId.set(null);
    this.syncRelationDrafts(relation);
  }

  changeSelectedRelationType(type: RelationType): void {
    const relation = this.selectedRelation()?.relation;
    const currentDiagram = this.diagram();
    if (!relation || !currentDiagram || relation.type === type || this.relationPropertySaving()) return;
    this.executeRelationProperty({
      type: 'CHANGE_RELATION_TYPE',
      payload: { relationId: relation.id, type },
    });
  }

  relationTypeLabel(type: string): string {
    return this.relationTypeOptions.find((option) => option.value === type)?.label ?? type;
  }

  usesMultiplicity(type: string): boolean {
    return type === 'ASSOCIATION' || type === 'AGGREGATION' || type === 'COMPOSITION';
  }

  usesRoles(type: string): boolean {
    return this.usesMultiplicity(type);
  }

  createAssociationClass(): void {
    const currentDiagram = this.diagram();
    const relation = this.selectedRelation()?.relation;
    if (!currentDiagram || !relation || relation.type !== 'ASSOCIATION' || this.associationClassSaving()) return;
    const midpoint = this.relationMidpoint(relation, this.renderedClasses());
    const classId = crypto.randomUUID();
    const linkId = crypto.randomUUID();
    this.associationClassSaving.set(true);
    this.associationClassError.set('');
    this.operationService.execute(this.diagramId, {
      operation: {
        operationId: crypto.randomUUID(),
        diagramId: this.diagramId,
        userId: DEV_USER_ID,
        baseVersion: currentDiagram.version,
        type: 'CREATE_ASSOCIATION_CLASS',
        payload: {
          linkId,
          relationId: relation.id,
          classId,
          name: this.nextAssociationClassName(currentDiagram),
          isAbstract: false,
          x: Math.max(0, midpoint.x - 120),
          y: Math.max(0, midpoint.y + 50),
          width: 240,
          height: 180,
        },
      },
    }).subscribe({
      next: (response) => {
        this.applyOperationResponse(response);
        this.associationClassSaving.set(false);
      },
      error: (error: unknown) => {
        this.associationClassSaving.set(false);
        this.associationClassError.set(this.operationError(error, 'No se pudo crear la clase de asociación.'));
        console.error('No se pudo crear la clase de asociación.', error instanceof HttpErrorResponse ? error.status : 'Error HTTP');
      },
    });
  }

  goToAssociationClass(): void {
    const link = this.selectedAssociationClassLink();
    if (!link) return;
    this.selectedClassId.set(link.classId);
    this.selectedRelationId.set(null);
  }

  unlinkAssociationClass(): void {
    const currentDiagram = this.diagram();
    const link = this.selectedAssociationClassLink();
    if (!currentDiagram || !link || this.associationClassSaving()) return;
    this.associationClassSaving.set(true);
    this.associationClassError.set('');
    this.operationService.execute(this.diagramId, {
      operation: {
        operationId: crypto.randomUUID(),
        diagramId: this.diagramId,
        userId: DEV_USER_ID,
        baseVersion: currentDiagram.version,
        type: 'DELETE_ASSOCIATION_CLASS_LINK',
        payload: { linkId: link.id },
      },
    }).subscribe({
      next: (response) => {
        this.applyOperationResponse(response);
        this.associationClassSaving.set(false);
      },
      error: (error: unknown) => {
        this.associationClassSaving.set(false);
        this.associationClassError.set(this.operationError(error, 'No se pudo desvincular la clase de asociación.'));
      },
    });
  }

  relationMarkerStart(relation: UmlRelation): string | null {
    if (relation.type === 'AGGREGATION') return 'url(#aggregation-diamond)';
    if (relation.type === 'COMPOSITION') return 'url(#composition-diamond)';
    if (relation.type === 'ASSOCIATION' && relation.targetNavigable) return 'url(#relation-arrow)';
    return null;
  }

  relationMarkerEnd(relation: UmlRelation): string | null {
    if (relation.type === 'INHERITANCE') return 'url(#inheritance-triangle)';
    if (relation.type === 'DEPENDENCY') return 'url(#dependency-arrow)';
    if (relation.type !== 'INHERITANCE' && relation.type !== 'DEPENDENCY' && relation.sourceNavigable) {
      return 'url(#relation-arrow)';
    }
    return null;
  }

  updateRelationMultiplicityDraft(side: 'source' | 'target', value: string): void {
    const draft = this.relationMultiplicityDraft();
    if (!draft) return;
    this.relationMultiplicityDraft.set({
      ...draft,
      [side]: this.parseMultiplicity(value),
    });
  }

  updateRelationRoleDraft(side: 'source' | 'target', value: string): void {
    const draft = this.relationRolesDraft();
    if (!draft) return;
    this.relationRolesDraft.set({ ...draft, [side]: value });
  }

  updateRelationNavigability(side: 'source' | 'target', value: boolean): void {
    this.relationNavigabilityDraft.update((draft) => ({ ...draft, [side]: value }));
  }

  saveRelationMultiplicity(): void {
    const relation = this.selectedRelation()?.relation;
    const draft = this.relationMultiplicityDraft();
    if (!relation || !draft || this.relationPropertySaving()) return;
    if (
      this.sameMultiplicity(relation.sourceMultiplicity, draft.source) &&
      this.sameMultiplicity(relation.targetMultiplicity, draft.target)
    ) {
      return;
    }
    this.executeRelationProperty({
      type: 'CHANGE_MULTIPLICITY',
      payload: {
        relationId: relation.id,
        sourceMultiplicity: draft.source,
        targetMultiplicity: draft.target,
      },
    });
  }

  saveRelationRoles(): void {
    const relation = this.selectedRelation()?.relation;
    const draft = this.relationRolesDraft();
    if (!relation || !draft || this.relationPropertySaving()) return;
    if ((relation.sourceRole ?? '') === draft.source && (relation.targetRole ?? '') === draft.target) {
      return;
    }
    this.executeRelationProperty({
      type: 'CHANGE_RELATION_ROLES',
      payload: {
        relationId: relation.id,
        sourceRole: draft.source,
        targetRole: draft.target,
      },
    });
  }

  saveRelationNavigability(): void {
    const relation = this.selectedRelation()?.relation;
    const draft = this.relationNavigabilityDraft();
    if (!relation || this.relationPropertySaving()) return;
    if (relation.sourceNavigable === draft.source && relation.targetNavigable === draft.target) {
      return;
    }
    this.executeRelationProperty({
      type: 'CHANGE_NAVIGABILITY',
      payload: {
        relationId: relation.id,
        sourceNavigable: draft.source,
        targetNavigable: draft.target,
      },
    });
  }

  multiplicityValue(multiplicity: Multiplicity | null | undefined): string {
    if (!multiplicity) return '';
    return multiplicity.lower === multiplicity.upper
      ? multiplicity.lower
      : `${multiplicity.lower}..${multiplicity.upper}`;
  }

  private syncRelationDrafts(relation: UmlRelation): void {
    this.relationMultiplicityDraft.set({
      source: { ...relation.sourceMultiplicity },
      target: { ...relation.targetMultiplicity },
    });
    this.relationRolesDraft.set({
      source: relation.sourceRole ?? '',
      target: relation.targetRole ?? '',
    });
    this.relationNavigabilityDraft.set({
      source: relation.sourceNavigable,
      target: relation.targetNavigable,
    });
  }

  private parseMultiplicity(value: string): Multiplicity {
    const normalized = value.trim() || '1';
    const parts = normalized.split('..', 2);
    return parts.length === 2
      ? { lower: parts[0].trim(), upper: parts[1].trim() }
      : { lower: normalized, upper: normalized };
  }

  private sameMultiplicity(left: Multiplicity | null | undefined, right: Multiplicity): boolean {
    return left?.lower === right.lower && left?.upper === right.upper;
  }

  private executeRelationProperty(operation: RelationPropertyOperation): void {
    const currentDiagram = this.diagram();
    if (!currentDiagram) return;
    this.relationPropertySaving.set(true);
    this.relationError.set('');
    this.operationService
      .execute(this.diagramId, {
        operation: {
          operationId: crypto.randomUUID(),
          diagramId: this.diagramId,
          userId: DEV_USER_ID,
          baseVersion: currentDiagram.version,
          type: operation.type,
          payload: operation.payload,
        },
      })
      .subscribe({
        next: (response) => {
          this.applyOperationResponse(response);
          const updated = response.canonicalModel.relations.find(
            (relation) => relation.id === operation.payload.relationId,
          );
          if (updated) this.syncRelationDrafts(updated);
          this.relationPropertySaving.set(false);
        },
        error: (error: unknown) => {
          this.relationPropertySaving.set(false);
          this.relationError.set(this.operationError(error, 'No se pudo actualizar la relación.'));
          console.error(
            'No se pudo actualizar la relación.',
            error instanceof HttpErrorResponse ? error.status : 'Error HTTP',
          );
        },
      });
  }

  private createRelation(sourceClassId: string, targetClassId: string): void {
    const currentDiagram = this.diagram();
    if (!currentDiagram || this.relationSaving()) return;
    const relationId = crypto.randomUUID();
    const relation: UmlRelation = {
      id: relationId,
      sourceClassId,
      targetClassId,
      type: this.relationCreationType(),
      sourceMultiplicity: { lower: '1', upper: '1' },
      targetMultiplicity: { lower: '1', upper: '1' },
      sourceRole: null,
      targetRole: null,
      sourceNavigable: false,
      targetNavigable: false,
    };
    this.relationSaving.set(true);
    this.relationError.set('');
    this.operationService
      .execute(this.diagramId, {
        operation: {
          operationId: crypto.randomUUID(),
          diagramId: this.diagramId,
          userId: DEV_USER_ID,
          baseVersion: currentDiagram.version,
          type: 'CREATE_RELATION',
          payload: { relation },
        },
      })
      .subscribe({
        next: (response) => {
          this.applyOperationResponse(response);
          this.relationSaving.set(false);
          this.relationSourceClassId.set(null);
          this.activeTool.set('SELECT');
          this.selectedRelationId.set(relationId);
          this.selectedClassId.set(null);
          const created = response.canonicalModel.relations.find((item) => item.id === relationId);
          if (created) this.syncRelationDrafts(created);
        },
        error: (error: unknown) => {
          this.relationSaving.set(false);
          this.relationError.set(this.operationError(error, 'No se pudo crear la relación.'));
          console.error(
            'No se pudo crear la relación.',
            error instanceof HttpErrorResponse ? error.status : 'Error HTTP',
          );
        },
      });
  }

  removeSelectedRelation(): void {
    const relation = this.selectedRelation()?.relation;
    if (!relation || this.relationSaving()) return;
    this.relationError.set('');
    this.pendingRelationDeletion.set(relation);
  }

  cancelRelationDeletion(): void {
    if (this.relationSaving()) return;
    this.pendingRelationDeletion.set(null);
    this.relationError.set('');
  }

  confirmRelationDeletion(): void {
    const currentDiagram = this.diagram();
    const relation = this.pendingRelationDeletion();
    if (!currentDiagram || !relation || this.relationSaving()) return;
    this.relationSaving.set(true);
    this.operationService
      .execute(this.diagramId, {
        operation: {
          operationId: crypto.randomUUID(),
          diagramId: this.diagramId,
          userId: DEV_USER_ID,
          baseVersion: currentDiagram.version,
          type: 'DELETE_RELATION',
          payload: { relationId: relation.id },
        },
      })
      .subscribe({
        next: (response) => {
          this.applyOperationResponse(response);
          this.relationSaving.set(false);
          this.pendingRelationDeletion.set(null);
          this.selectedRelationId.set(null);
        },
        error: (error: unknown) => {
          this.relationSaving.set(false);
          this.relationError.set(this.operationError(error, 'No se pudo eliminar la relación.'));
          console.error(
            'No se pudo eliminar la relación.',
            error instanceof HttpErrorResponse ? error.status : 'Error HTTP',
          );
        },
      });
  }

  onClassPointerDown(umlClass: UmlClass, event: PointerEvent): void {
    event.stopPropagation();
    const canvas = (event.currentTarget as HTMLElement).closest('.canvas') as HTMLElement | null;
    if (canvas && this.shouldStartPan(event)) {
      this.startPan(event, canvas);
      return;
    }
    if (this.activeTool() !== 'SELECT' || this.moving()) return;
    const currentDiagram = this.diagram();
    const rendered = this.renderedClasses().find((item) => item.umlClass.id === umlClass.id);
    if (!currentDiagram || !rendered) return;
    if (!canvas) return;
    const point = this.screenToWorld(event.clientX, event.clientY, canvas);
    const state: DragState = {
      classId: umlClass.id,
      pointerId: event.pointerId,
      startPointerX: event.clientX,
      startPointerY: event.clientY,
      initialX: rendered.x,
      initialY: rendered.y,
      offsetX: point.x - rendered.x,
      offsetY: point.y - rendered.y,
      previewX: rendered.x,
      previewY: rendered.y,
      isDragging: false,
      captureElement: event.currentTarget as HTMLElement,
    };
    this.selectedClassId.set(umlClass.id);
    this.dragState.set(state);
    state.captureElement.setPointerCapture?.(event.pointerId);
  }

  onResizePointerDown(item: RenderedUmlClass, event: PointerEvent): void {
    event.stopPropagation();
    if (this.activeTool() !== 'SELECT' || this.resizing() || this.moving()) return;
    const state: ResizeState = {
      classId: item.umlClass.id,
      pointerId: event.pointerId,
      startPointerX: event.clientX,
      startPointerY: event.clientY,
      initialWidth: item.width,
      initialHeight: item.height,
      previewWidth: item.width,
      previewHeight: item.height,
      isResizing: false,
      captureElement: event.currentTarget as HTMLElement,
    };
    this.selectedClassId.set(item.umlClass.id);
    this.resizeState.set(state);
    state.captureElement.setPointerCapture?.(event.pointerId);
  }

  onCanvasPointerDown(event: PointerEvent): void {
    const canvas = event.currentTarget as HTMLElement;
    const target = event.target as HTMLElement | null;
    if (target?.closest('.viewport-controls')) return;
    if (
      this.activeTool() === 'SELECT' &&
      event.button === 0 &&
      this.isCanvasBackgroundTarget(target)
    ) {
      this.startPan(event, canvas);
      return;
    }
    if (this.shouldStartPan(event) && this.isCanvasBackgroundTarget(target)) {
      this.startPan(event, canvas);
    }
  }

  onCanvasPointerMove(event: PointerEvent): void {
    if (this.panState()) {
      this.updatePan(event);
      return;
    }
    if (this.resizeState()) {
      this.updateResizePreview(event);
      return;
    }
    const state = this.dragState();
    if (!state || state.pointerId !== event.pointerId || this.activeTool() !== 'SELECT') return;
    const distance = Math.hypot(
      event.clientX - state.startPointerX,
      event.clientY - state.startPointerY,
    );
    if (!state.isDragging && distance < 4) return;
    const canvas = event.currentTarget as HTMLElement;
    const point = this.screenToWorld(event.clientX, event.clientY, canvas);
    state.isDragging = true;
    state.previewX = point.x - state.offsetX;
    state.previewY = point.y - state.offsetY;
    this.dragState.set({ ...state });
  }

  onCanvasPointerUp(event: PointerEvent): void {
    if (this.panState()) {
      this.finishPan(event);
      return;
    }
    if (this.resizeState()) {
      this.finishResize(event);
      return;
    }
    this.finishDrag(event);
  }
  onCanvasPointerCancel(event: PointerEvent): void {
    if (this.panState()) {
      this.finishPan(event);
      this.suppressCanvasClick.set(false);
      return;
    }
    if (this.resizeState()) {
      this.finishResize(event, true);
      return;
    }
    this.finishDrag(event, true);
  }

  private updateResizePreview(event: PointerEvent): void {
    const state = this.resizeState();
    if (!state || state.pointerId !== event.pointerId || this.activeTool() !== 'SELECT') return;
    const next = {
      ...state,
      isResizing:
        state.isResizing ||
        Math.hypot(event.clientX - state.startPointerX, event.clientY - state.startPointerY) >= 4,
      previewWidth: Math.max(180, state.initialWidth + (event.clientX - state.startPointerX) / this.zoom()),
      previewHeight: Math.max(120, state.initialHeight + (event.clientY - state.startPointerY) / this.zoom()),
    };
    this.resizeState.set(next);
  }

  private finishResize(event: PointerEvent, cancelled = false): void {
    const state = this.resizeState();
    if (!state || state.pointerId !== event.pointerId) return;
    state.captureElement.releasePointerCapture?.(event.pointerId);
    if (cancelled || !state.isResizing) {
      this.resizeState.set(null);
      return;
    }
    if (state.previewWidth === state.initialWidth && state.previewHeight === state.initialHeight) {
      this.resizeState.set(null);
      return;
    }
    const currentDiagram = this.diagram();
    if (!currentDiagram) {
      this.resizeState.set(null);
      return;
    }
    this.resizing.set(true);
    this.operationService
      .execute(this.diagramId, {
        operation: {
          operationId: crypto.randomUUID(),
          diagramId: this.diagramId,
          userId: DEV_USER_ID,
          baseVersion: currentDiagram.version,
          type: 'RESIZE_CLASS',
          payload: {
            classId: state.classId,
            width: state.previewWidth,
            height: state.previewHeight,
          },
        },
      })
      .subscribe({
        next: (response) => {
          this.applyOperationResponse(response);
          this.resizing.set(false);
          this.resizeState.set(null);
        },
        error: (error: unknown) => {
          this.resizing.set(false);
          this.resizeState.set(null);
          this.error.set(this.operationError(error, 'No se pudo cambiar el tamaño de la clase.'));
          console.error(
            'No se pudo cambiar el tamaño de la clase.',
            error instanceof HttpErrorResponse ? error.status : 'Error HTTP',
          );
        },
      });
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
  private previewWidth(classId: string, fallback: number): number {
    const state = this.resizeState();
    return state?.classId === classId && state.isResizing ? state.previewWidth : fallback;
  }
  private previewHeight(classId: string, fallback: number): number {
    const state = this.resizeState();
    return state?.classId === classId && state.isResizing ? state.previewHeight : fallback;
  }
  screenToWorld(clientX: number, clientY: number, viewport: HTMLElement): { x: number; y: number } {
    const rect = viewport.getBoundingClientRect();
    return {
      x: (clientX - rect.left - this.panX()) / this.zoom(),
      y: (clientY - rect.top - this.panY()) / this.zoom(),
    };
  }

  zoomIn(): void {
    this.setZoom(this.zoom() + 0.1);
  }

  zoomOut(): void {
    this.setZoom(this.zoom() - 0.1);
  }

  resetZoom(): void {
    this.zoom.set(1);
  }

  resetViewport(): void {
    this.zoom.set(1);
    this.panX.set(0);
    this.panY.set(0);
  }

  fitToContent(event: Event): void {
    const viewport = (event.currentTarget as HTMLElement).closest('.canvas') as HTMLElement | null;
    const nodes = this.diagram()?.viewState.nodes ?? [];
    if (!viewport || nodes.length === 0) {
      this.resetViewport();
      return;
    }
    const bounds = nodes.map((node) => ({
      x: Number.isFinite(node.x) ? node.x : 0,
      y: Number.isFinite(node.y) ? node.y : 0,
      width: Number.isFinite(node.width) && node.width > 0 ? node.width : this.defaultNodeWidth,
      height: Number.isFinite(node.height) && node.height > 0 ? node.height : this.defaultNodeHeight,
    }));
    const minX = Math.min(...bounds.map((node) => node.x));
    const minY = Math.min(...bounds.map((node) => node.y));
    const maxX = Math.max(...bounds.map((node) => node.x + node.width));
    const maxY = Math.max(...bounds.map((node) => node.y + node.height));
    const padding = 80;
    const contentWidth = Math.max(1, maxX - minX + padding * 2);
    const contentHeight = Math.max(1, maxY - minY + padding * 2);
    const viewportRect = viewport.getBoundingClientRect();
    const viewportWidth = viewportRect.width || viewport.clientWidth;
    const viewportHeight = viewportRect.height || viewport.clientHeight;
    const nextZoom = this.clampZoom(Math.min(viewportWidth / contentWidth, viewportHeight / contentHeight));
    this.zoom.set(nextZoom);
    this.panX.set((viewportWidth - contentWidth * nextZoom) / 2 - (minX - padding) * nextZoom);
    this.panY.set((viewportHeight - contentHeight * nextZoom) / 2 - (minY - padding) * nextZoom);
  }

  onCanvasWheel(event: WheelEvent): void {
    if (!event.ctrlKey || this.isEditableTarget(event.target)) return;
    event.preventDefault();
    const viewport = event.currentTarget as HTMLElement;
    const rect = viewport.getBoundingClientRect();
    const mouseX = event.clientX - rect.left;
    const mouseY = event.clientY - rect.top;
    const worldX = (mouseX - this.panX()) / this.zoom();
    const worldY = (mouseY - this.panY()) / this.zoom();
    const nextZoom = this.clampZoom(this.zoom() + (event.deltaY < 0 ? 0.1 : -0.1));
    this.zoom.set(nextZoom);
    this.panX.set(mouseX - worldX * nextZoom);
    this.panY.set(mouseY - worldY * nextZoom);
  }

  private setZoom(value: number): void {
    this.zoom.set(this.clampZoom(Math.round(value * 100) / 100));
  }

  private clampZoom(value: number): number {
    return Math.max(0.25, Math.min(2, value));
  }

  private shouldStartPan(event: PointerEvent): boolean {
    return event.button === 1 || (event.button === 0 && this.spacePressed());
  }

  private startPan(event: PointerEvent, viewport: HTMLElement): void {
    if (this.isEditableTarget(event.target)) return;
    event.preventDefault();
    this.suppressCanvasClick.set(event.button !== 0 || this.spacePressed());
    this.panState.set({
      pointerId: event.pointerId,
      startPointerX: event.clientX,
      startPointerY: event.clientY,
      initialPanX: this.panX(),
      initialPanY: this.panY(),
      isDragging: false,
      captureElement: viewport,
    });
    viewport.setPointerCapture?.(event.pointerId);
  }

  private updatePan(event: PointerEvent): void {
    const state = this.panState();
    if (!state || state.pointerId !== event.pointerId) return;
    const distance = Math.hypot(
      event.clientX - state.startPointerX,
      event.clientY - state.startPointerY,
    );
    if (!state.isDragging && distance < 4) return;
    if (!state.isDragging) {
      state.isDragging = true;
      this.suppressCanvasClick.set(true);
    }
    this.panX.set(state.initialPanX + event.clientX - state.startPointerX);
    this.panY.set(state.initialPanY + event.clientY - state.startPointerY);
  }

  private finishPan(event: PointerEvent): void {
    const state = this.panState();
    if (!state || state.pointerId !== event.pointerId) return;
    state.captureElement.releasePointerCapture?.(event.pointerId);
    this.panState.set(null);
  }

  private isCanvasBackgroundTarget(target: HTMLElement | null): boolean {
    if (!target) return true;
    return !target.closest(
      '.uml-class, .resize-handle, .relation-hit, .relation-line, .association-class-link, .viewport-controls, input, textarea, select, button, [contenteditable="true"]',
    );
  }

  private isEditableTarget(target: EventTarget | null): boolean {
    return target instanceof HTMLElement && Boolean(target.closest('input, textarea, select, [contenteditable="true"]'));
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

  startAddMethod(): void {
    if (!this.selectedClass()) return;
    this.editingMethodId.set(null);
    this.parameterDraft.set(null);
    this.editingParameterId.set(null);
    this.methodError.set('');
    this.parameterError.set('');
    this.methodDraft.set({ name: '', returnType: 'void', visibility: 'PUBLIC', isStatic: false });
  }

  startEditMethod(method: UmlMethod): void {
    this.editingMethodId.set(method.id);
    this.parameterDraft.set(null);
    this.editingParameterId.set(null);
    this.methodError.set('');
    this.parameterError.set('');
    this.methodDraft.set({
      name: method.name,
      returnType: method.returnType,
      visibility: method.visibility as Visibility,
      isStatic: method.isStatic,
    });
  }

  updateMethodDraft(field: keyof MethodDraft, value: string | boolean): void {
    this.methodDraft.update((draft) =>
      draft ? ({ ...draft, [field]: value } as MethodDraft) : draft,
    );
  }

  cancelMethodEdit(): void {
    if (this.methodSaving()) return;
    this.methodDraft.set(null);
    this.editingMethodId.set(null);
    this.parameterDraft.set(null);
    this.editingParameterId.set(null);
    this.methodError.set('');
    this.parameterError.set('');
  }

  saveMethod(): void {
    if (this.methodSaving()) return;
    const currentDiagram = this.diagram();
    const currentClass = this.selectedClass()?.umlClass;
    const draft = this.methodDraft();
    if (!currentDiagram || !currentClass || !draft) return;
    const name = draft.name.trim();
    const returnType = draft.returnType.trim();
    if (!name || !returnType) {
      this.methodError.set('Nombre y tipo de retorno son obligatorios.');
      return;
    }
    if (!this.visibilityOptions.includes(draft.visibility)) {
      this.methodError.set('La visibilidad no es válida.');
      return;
    }
    const editingId = this.editingMethodId();
    const existing = currentClass.methods.find((method) => method.id === editingId);
    const signature = this.methodSignature(name, existing?.parameters ?? []);
    if (
      currentClass.methods.some(
        (method) =>
          method.id !== editingId &&
          this.methodSignature(method.name, method.parameters) === signature,
      )
    ) {
      this.methodError.set('Ya existe un método con esa firma.');
      return;
    }
    if (
      existing &&
      existing.name === name &&
      existing.returnType === returnType &&
      existing.visibility === draft.visibility &&
      existing.isStatic === draft.isStatic
    ) {
      return;
    }
    const methodId = editingId ?? crypto.randomUUID();
    const payload = editingId
      ? {
          classId: currentClass.id,
          methodId,
          name,
          returnType,
          visibility: draft.visibility,
          isStatic: draft.isStatic,
        }
      : {
          classId: currentClass.id,
          method: {
            id: methodId,
            name,
            returnType,
            visibility: draft.visibility,
            isStatic: draft.isStatic,
            parameters: [],
          },
        };
    this.methodSaving.set(true);
    this.methodError.set('');
    this.operationService
      .execute(this.diagramId, {
        operation: {
          operationId: crypto.randomUUID(),
          diagramId: this.diagramId,
          userId: DEV_USER_ID,
          baseVersion: currentDiagram.version,
          type: editingId ? 'UPDATE_METHOD' : 'ADD_METHOD',
          payload,
        },
      })
      .subscribe({
        next: (response) => {
          this.applyOperationResponse(response);
          this.methodSaving.set(false);
          this.editingMethodId.set(methodId);
          this.parameterDraft.set(null);
          this.editingParameterId.set(null);
        },
        error: (error: unknown) => {
          this.methodSaving.set(false);
          this.methodError.set(this.operationError(error, 'No se pudo guardar el método.'));
          console.error(
            'No se pudo guardar el método.',
            error instanceof HttpErrorResponse ? error.status : 'Error HTTP',
          );
        },
      });
  }

  removeMethod(method: UmlMethod): void {
    if (this.methodSaving() || !this.selectedClass()) return;
    this.methodError.set('');
    this.pendingMethodDeletion.set(method);
  }

  cancelMethodDeletion(): void {
    if (!this.methodSaving()) this.pendingMethodDeletion.set(null);
  }

  confirmMethodDeletion(): void {
    const currentDiagram = this.diagram();
    const currentClass = this.selectedClass()?.umlClass;
    const method = this.pendingMethodDeletion();
    if (!currentDiagram || !currentClass || !method || this.methodSaving()) return;
    this.methodSaving.set(true);
    this.operationService
      .execute(this.diagramId, {
        operation: {
          operationId: crypto.randomUUID(),
          diagramId: this.diagramId,
          userId: DEV_USER_ID,
          baseVersion: currentDiagram.version,
          type: 'REMOVE_METHOD',
          payload: { classId: currentClass.id, methodId: method.id },
        },
      })
      .subscribe({
        next: (response) => {
          this.applyOperationResponse(response);
          this.methodSaving.set(false);
          this.pendingMethodDeletion.set(null);
        },
        error: (error: unknown) => {
          this.methodSaving.set(false);
          this.methodError.set(this.operationError(error, 'No se pudo eliminar el método.'));
          console.error(
            'No se pudo eliminar el método.',
            error instanceof HttpErrorResponse ? error.status : 'Error HTTP',
          );
        },
      });
  }

  startAddParameter(): void {
    if (!this.editingMethodId() || !this.methodDraft()) return;
    this.editingParameterId.set(null);
    this.parameterError.set('');
    this.parameterDraft.set({ name: '', type: '' });
  }

  startEditParameter(parameter: UmlParameter): void {
    this.editingParameterId.set(parameter.id);
    this.parameterError.set('');
    this.parameterDraft.set({ name: parameter.name, type: parameter.type });
  }

  updateParameterDraft(field: keyof ParameterDraft, value: string): void {
    this.parameterDraft.update((draft) => (draft ? { ...draft, [field]: value } : draft));
  }

  cancelParameterEdit(): void {
    if (!this.methodSaving()) {
      this.parameterDraft.set(null);
      this.editingParameterId.set(null);
      this.parameterError.set('');
    }
  }

  saveParameter(): void {
    if (this.methodSaving()) return;
    const currentDiagram = this.diagram();
    const currentClass = this.selectedClass()?.umlClass;
    const methodId = this.editingMethodId();
    const draft = this.parameterDraft();
    if (!currentDiagram || !currentClass || !methodId || !draft) return;
    const method = currentClass.methods.find((candidate) => candidate.id === methodId);
    if (!method) return;
    const name = draft.name.trim();
    const type = draft.type.trim();
    if (!name || !type) {
      this.parameterError.set('Nombre y tipo son obligatorios.');
      return;
    }
    const editingId = this.editingParameterId();
    if (
      method.parameters.some(
        (parameter) =>
          parameter.id !== editingId && parameter.name.toLowerCase() === name.toLowerCase(),
      )
    ) {
      this.parameterError.set('Ya existe un parámetro con ese nombre.');
      return;
    }
    const existing = method.parameters.find((parameter) => parameter.id === editingId);
    if (existing && existing.name === name && existing.type === type) {
      this.cancelParameterEdit();
      return;
    }
    const parameterId = editingId ?? crypto.randomUUID();
    const payload = editingId
      ? { classId: currentClass.id, methodId, parameterId, name, type }
      : { classId: currentClass.id, methodId, parameter: { id: parameterId, name, type } };
    this.methodSaving.set(true);
    this.parameterError.set('');
    this.operationService
      .execute(this.diagramId, {
        operation: {
          operationId: crypto.randomUUID(),
          diagramId: this.diagramId,
          userId: DEV_USER_ID,
          baseVersion: currentDiagram.version,
          type: editingId ? 'UPDATE_PARAMETER' : 'ADD_PARAMETER',
          payload,
        },
      })
      .subscribe({
        next: (response) => {
          this.applyOperationResponse(response);
          this.methodSaving.set(false);
          this.parameterDraft.set(null);
          this.editingParameterId.set(null);
        },
        error: (error: unknown) => {
          this.methodSaving.set(false);
          this.parameterError.set(this.operationError(error, 'No se pudo guardar el parámetro.'));
          console.error(
            'No se pudo guardar el parámetro.',
            error instanceof HttpErrorResponse ? error.status : 'Error HTTP',
          );
        },
      });
  }

  removeParameter(parameter: UmlParameter): void {
    if (this.methodSaving() || !this.editingMethodId()) return;
    this.parameterError.set('');
    this.pendingParameterDeletion.set(parameter);
  }

  cancelParameterDeletion(): void {
    if (!this.methodSaving()) this.pendingParameterDeletion.set(null);
  }

  confirmParameterDeletion(): void {
    const currentDiagram = this.diagram();
    const currentClass = this.selectedClass()?.umlClass;
    const methodId = this.editingMethodId();
    const parameter = this.pendingParameterDeletion();
    if (!currentDiagram || !currentClass || !methodId || !parameter || this.methodSaving()) return;
    this.methodSaving.set(true);
    this.operationService
      .execute(this.diagramId, {
        operation: {
          operationId: crypto.randomUUID(),
          diagramId: this.diagramId,
          userId: DEV_USER_ID,
          baseVersion: currentDiagram.version,
          type: 'REMOVE_PARAMETER',
          payload: { classId: currentClass.id, methodId, parameterId: parameter.id },
        },
      })
      .subscribe({
        next: (response) => {
          this.applyOperationResponse(response);
          this.methodSaving.set(false);
          this.pendingParameterDeletion.set(null);
        },
        error: (error: unknown) => {
          this.methodSaving.set(false);
          this.parameterError.set(this.operationError(error, 'No se pudo eliminar el parámetro.'));
          console.error(
            'No se pudo eliminar el parámetro.',
            error instanceof HttpErrorResponse ? error.status : 'Error HTTP',
          );
        },
      });
  }

  private methodSignature(name: string, parameters: UmlParameter[]): string {
    return `${name.toLowerCase()}(${parameters.map((parameter) => parameter.type.trim().toLowerCase()).join(',')})`;
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

  private renderRelation(
    relation: UmlRelation,
    classes: RenderedUmlClass[],
  ): RenderedRelation | null {
    const source = classes.find((item) => item.umlClass.id === relation.sourceClassId);
    const target = classes.find((item) => item.umlClass.id === relation.targetClassId);
    if (!source || !target) return null;

    if (source.umlClass.id === target.umlClass.id) {
      const startX = source.x + source.width;
      const startY = source.y + 34;
      const endY = source.y + Math.max(72, source.height - 34);
      const loopX = startX + 72;
      return {
        relation,
        path: `M ${startX} ${startY} C ${loopX} ${startY}, ${loopX} ${endY}, ${startX} ${endY}`,
        markerStart: relation.targetNavigable,
        markerEnd: relation.sourceNavigable,
        sourceLabelX: startX + 18,
        sourceLabelY: startY - 8,
        targetLabelX: startX + 18,
        targetLabelY: endY + 16,
        sourceRoleX: startX + 18,
        sourceRoleY: startY - 22,
        targetRoleX: startX + 18,
        targetRoleY: endY + 30,
      };
    }

    const sourceCenterX = source.x + source.width / 2;
    const sourceCenterY = source.y + source.height / 2;
    const targetCenterX = target.x + target.width / 2;
    const targetCenterY = target.y + target.height / 2;
    const deltaX = targetCenterX - sourceCenterX;
    const deltaY = targetCenterY - sourceCenterY;
    let sourceX = sourceCenterX;
    let sourceY = sourceCenterY;
    let targetX = targetCenterX;
    let targetY = targetCenterY;

    if (Math.abs(deltaX) >= Math.abs(deltaY)) {
      const sourceOnRight = deltaX >= 0;
      sourceX = sourceOnRight ? source.x + source.width : source.x;
      targetX = sourceOnRight ? target.x : target.x + target.width;
    } else {
      const sourceBelow = deltaY >= 0;
      sourceY = sourceBelow ? source.y + source.height : source.y;
      targetY = sourceBelow ? target.y : target.y + target.height;
    }

    return {
      relation,
      path: `M ${sourceX} ${sourceY} L ${targetX} ${targetY}`,
      markerStart: relation.targetNavigable,
      markerEnd: relation.sourceNavigable,
      sourceLabelX: sourceX + (targetX - sourceX) * 0.25,
      sourceLabelY: sourceY + (targetY - sourceY) * 0.25 - 8,
      targetLabelX: sourceX + (targetX - sourceX) * 0.75,
      targetLabelY: sourceY + (targetY - sourceY) * 0.75 - 8,
      sourceRoleX: sourceX + (targetX - sourceX) * 0.25,
      sourceRoleY: sourceY + (targetY - sourceY) * 0.25 - 22,
      targetRoleX: sourceX + (targetX - sourceX) * 0.75,
      targetRoleY: sourceY + (targetY - sourceY) * 0.75 - 22,
    };
  }

  private relationMidpoint(relation: UmlRelation, classes: RenderedUmlClass[]): { x: number; y: number } {
    const source = classes.find((item) => item.umlClass.id === relation.sourceClassId);
    const target = classes.find((item) => item.umlClass.id === relation.targetClassId);
    if (!source || !target) return { x: 0, y: 0 };
    if (source.umlClass.id === target.umlClass.id) {
      return { x: source.x + source.width + 72, y: source.y + source.height / 2 };
    }
    const sourceCenter = { x: source.x + source.width / 2, y: source.y + source.height / 2 };
    const targetCenter = { x: target.x + target.width / 2, y: target.y + target.height / 2 };
    const horizontal = Math.abs(targetCenter.x - sourceCenter.x) >= Math.abs(targetCenter.y - sourceCenter.y);
    const sourcePoint = horizontal
      ? { x: targetCenter.x >= sourceCenter.x ? source.x + source.width : source.x, y: sourceCenter.y }
      : { x: sourceCenter.x, y: targetCenter.y >= sourceCenter.y ? source.y + source.height : source.y };
    const targetPoint = horizontal
      ? { x: targetCenter.x >= sourceCenter.x ? target.x : target.x + target.width, y: targetCenter.y }
      : { x: targetCenter.x, y: targetCenter.y >= sourceCenter.y ? target.y : target.y + target.height };
    return { x: (sourcePoint.x + targetPoint.x) / 2, y: (sourcePoint.y + targetPoint.y) / 2 };
  }

  multiplicityLabel(multiplicity: { lower: string; upper: string } | null | undefined): string {
    if (!multiplicity) return '';
    return multiplicity.lower === multiplicity.upper
      ? multiplicity.lower
      : `${multiplicity.lower}..${multiplicity.upper}`;
  }

  relationEndpointName(classId: string): string {
    return (
      this.diagram()?.canonicalModel.classes.find((umlClass) => umlClass.id === classId)?.name ??
      classId
    );
  }

  private nextAssociationClassName(diagram: DiagramDetail): string {
    const names = new Set(diagram.canonicalModel.classes.map((umlClass) => umlClass.name.toLowerCase()));
    let index = 1;
    while (names.has(`claseasociacion${index}`)) index++;
    return `ClaseAsociacion${index}`;
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
