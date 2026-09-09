import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { NEVER, of, throwError } from 'rxjs';

import { DiagramDetail, UmlAttribute, UmlMethod, UmlParameter, UmlRelation } from '../../models/diagram.model';
import { ExecuteDiagramOperationRequest } from '../../models/diagram-operation.model';
import { DiagramOperationService } from '../../services/diagram-operation.service';
import { DiagramService } from '../../services/diagram.service';
import { XmiImportService } from '../../services/xmi-import.service';
import { ImageImportService } from '../../services/image-import.service';
import { EditorPageComponent } from './editor-page.component';
import { ProjectService } from '../../../projects/services/project.service';

const detail: DiagramDetail = {
  id: 'd1',
  projectId: 'p1',
  name: 'Modelo real',
  version: 7,
  createdAt: '',
  updatedAt: '',
  canonicalModel: { id: 'd1', name: 'Modelo real', version: 7, classes: [], relations: [] },
  viewState: { diagramId: 'd1', nodes: [], relations: [] },
};

function configure(operationService: object, current = detail, xmiImport: object = {}, imageImport: object = {}, projectApi: object = { getMyRole: () => of({ role: 'OWNER' }), getMembers: () => of([]) }): Promise<void> {
  return TestBed.configureTestingModule({
    imports: [EditorPageComponent],
    providers: [
      { provide: DiagramService, useValue: { getDiagramById: () => of(current) } },
      { provide: DiagramOperationService, useValue: operationService },
      { provide: XmiImportService, useValue: { preview: () => NEVER, apply: () => NEVER, ...xmiImport } },
      { provide: ImageImportService, useValue: { preview: () => NEVER, apply: () => NEVER, ...imageImport } },
      { provide: ProjectService, useValue: projectApi },
      provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'd1' } } } },
    ],
  }).compileComponents();
}

describe('EditorPageComponent', () => {
  it('accepts XMI and image files and rejects unsupported formats', async () => {
    await configure({ execute: () => NEVER });
    const page = TestBed.createComponent(EditorPageComponent).componentInstance;
    const select = (file: File) => page.selectImportFile({ target: { files: [file] } } as unknown as Event);
    select(new File(['xmi'], 'model.xmi', { type: 'application/xml' }));
    expect(page.importKind()).toBe('XMI');
    select(new File(['png'], 'diagram.png', { type: 'image/png' }));
    expect(page.importKind()).toBe('IMAGE');
    select(new File(['bad'], 'diagram.pdf', { type: 'application/pdf' }));
    expect(page.importFile()).toBeNull();
    expect(page.importError()).toContain('Formato no compatible');
  });

  it('uses image preview for dropped images and can remove the selected file', async () => {
    let previews = 0;
    const response = { canonicalModel: detail.canonicalModel, viewState: detail.viewState, warnings: [], statistics: { classes: 1, attributes: 0, methods: 0, relations: 0, associationClasses: 0 }, confidence: .9, detectedClassNames: ['Cliente'] };
    await configure({ execute: () => NEVER }, detail, {}, { preview: () => { previews++; return of(response); } });
    const page = TestBed.createComponent(EditorPageComponent).componentInstance;
    const file = new File(['png'], 'diagram.png', { type: 'image/png' });
    page.onImportDrop({ preventDefault: () => undefined, dataTransfer: { files: [file] } } as unknown as DragEvent);
    page.analyzeImport();
    expect(previews).toBe(1);
    expect(page.importPreview()?.confidence).toBe(.9);
    page.removeImportFile();
    expect(page.importFile()).toBeNull();
  });

  it('opens the voice dialog without changing the active tool', async () => {
    await configure({ execute: () => NEVER });
    const page = TestBed.createComponent(EditorPageComponent).componentInstance;
    page.setActiveTool('CLASS');
    page.openVoiceDialog();
    expect(page.voiceDialogOpen()).toBe(true);
    expect(page.activeTool()).toBe('CLASS');
    page.closeVoiceDialog();
  });

  it('opens the share modal from the editor for an owner and loads members', async () => {
    let memberRequests = 0;
    await configure({ execute: () => NEVER }, detail, {}, {}, {
      getMyRole: () => of({ role: 'OWNER' }),
      getMembers: () => { memberRequests++; return of([{ id: 'm1', userId: 'u1', firstName: 'Owner', lastName: 'One', email: 'owner@test.com', role: 'OWNER' }]); },
    });
    const fixture = TestBed.createComponent(EditorPageComponent);
    await fixture.whenStable();
    fixture.componentInstance.openShare();
    fixture.detectChanges();
    expect(fixture.componentInstance.shareOpen()).toBe(true);
    expect(memberRequests).toBeGreaterThan(0);
    expect(fixture.nativeElement.querySelector('app-share-project-modal .share-panel')).not.toBeNull();
    (fixture.nativeElement.querySelector('app-share-project-modal .close-button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.componentInstance.shareOpen()).toBe(false);
  });

  function diagramWithClass(): DiagramDetail {
    return {
      ...detail,
      canonicalModel: {
        ...detail.canonicalModel,
        classes: [{ id: 'c1', name: 'Cliente', isAbstract: false, attributes: [], methods: [] }],
      },
      viewState: {
        ...detail.viewState,
        nodes: [{ classId: 'c1', x: 40, y: 50, width: 240, height: 180 }],
      },
    };
  }

  it('opens class deletion and applies DELETE_CLASS response authoritatively', async () => {
    const current = diagramWithClass();
    let request: ExecuteDiagramOperationRequest | undefined;
    await configure(
      {
        execute: (_id: string, value: ExecuteDiagramOperationRequest) => {
          request = value;
          return of({
            newVersion: 8,
            canonicalModel: { ...current.canonicalModel, classes: [] },
            viewState: { ...current.viewState, nodes: [] },
          });
        },
      },
      current,
    );
    const page = TestBed.createComponent(EditorPageComponent).componentInstance;
    page.selectedClassId.set('c1');
    page.removeSelectedClass();
    expect(page.pendingClassDeletion()?.name).toBe('Cliente');
    page.confirmClassDeletion();
    expect(request?.operation.type).toBe('DELETE_CLASS');
    expect(request?.operation.diagramId).toBe('d1');
    expect(request?.operation.baseVersion).toBe(7);
    expect(request?.operation.payload).toEqual({ classId: 'c1' });
    expect(page.diagram()?.canonicalModel.classes).toHaveLength(0);
    expect(page.diagram()?.viewState.nodes).toHaveLength(0);
    expect(page.diagram()?.version).toBe(8);
    expect(page.selectedClassId()).toBeNull();
    expect(page.pendingClassDeletion()).toBeNull();
  });

  it('does not execute deletion without a selected class', async () => {
    let executions = 0;
    await configure({
      execute: () => {
        executions++;
        return of({});
      },
    });
    const page = TestBed.createComponent(EditorPageComponent).componentInstance;
    page.removeSelectedClass();
    expect(executions).toBe(0);
    expect(page.classDeleteError()).toBe('Selecciona una clase para eliminarla.');
    expect(page.pendingClassDeletion()).toBeNull();
  });

  it('edits multiplicities and roles through typed relation operations', async () => {
    const current = diagramWithClass();
    const relation: UmlRelation = {
      id: 'r1',
      sourceClassId: 'c1',
      targetClassId: 'c1',
      type: 'ASSOCIATION',
      sourceMultiplicity: { lower: '1', upper: '1' },
      targetMultiplicity: { lower: '1', upper: '1' },
      sourceRole: null,
      targetRole: null,
      sourceNavigable: false,
      targetNavigable: false,
    };
    current.canonicalModel.relations = [relation];
    let request: ExecuteDiagramOperationRequest | undefined;
    await configure(
      {
        execute: (_id: string, value: ExecuteDiagramOperationRequest) => {
          request = value;
          const payload = value.operation.payload as {
            relationId: string;
            sourceMultiplicity?: UmlRelation['sourceMultiplicity'];
            targetMultiplicity?: UmlRelation['targetMultiplicity'];
            sourceRole?: string;
            targetRole?: string;
          };
          const updated = {
            ...relation,
            ...(value.operation.type === 'CHANGE_MULTIPLICITY'
              ? { sourceMultiplicity: payload.sourceMultiplicity, targetMultiplicity: payload.targetMultiplicity }
              : { sourceRole: payload.sourceRole ?? null, targetRole: payload.targetRole ?? null }),
          };
          return of({
            newVersion: current.version + 1,
            canonicalModel: { ...current.canonicalModel, relations: [updated] },
            viewState: current.viewState,
          });
        },
      },
      current,
    );
    const page = TestBed.createComponent(EditorPageComponent).componentInstance;
    page.selectRelation(relation, { stopPropagation: () => {} } as unknown as MouseEvent);
    page.updateRelationMultiplicityDraft('source', '0..1');
    page.updateRelationMultiplicityDraft('target', '0..*');
    page.saveRelationMultiplicity();
    expect(request?.operation.type).toBe('CHANGE_MULTIPLICITY');
    expect(request?.operation.baseVersion).toBe(7);
    expect(request?.operation.payload).toMatchObject({
      relationId: 'r1',
      sourceMultiplicity: { lower: '0', upper: '1' },
      targetMultiplicity: { lower: '0', upper: '*' },
    });
    expect(page.diagram()?.canonicalModel.relations[0].targetMultiplicity.upper).toBe('*');

    page.updateRelationRoleDraft('source', 'supervisor');
    page.updateRelationRoleDraft('target', 'subordinados');
    page.saveRelationRoles();
    expect(request?.operation.type).toBe('CHANGE_RELATION_ROLES');
    expect(request?.operation.payload).toMatchObject({
      relationId: 'r1',
      sourceRole: 'supervisor',
      targetRole: 'subordinados',
    });
    expect(page.diagram()?.canonicalModel.relations[0].sourceClassId).toBe(
      page.diagram()?.canonicalModel.relations[0].targetClassId,
    );
  });

  it('edits navigability and renders a recursive relation loop with both labels', async () => {
    const current = diagramWithClass();
    const relation: UmlRelation = {
      id: 'r1',
      sourceClassId: 'c1',
      targetClassId: 'c1',
      type: 'ASSOCIATION',
      sourceMultiplicity: { lower: '0', upper: '1' },
      targetMultiplicity: { lower: '0', upper: '*' },
      sourceRole: 'supervisor',
      targetRole: 'subordinados',
      sourceNavigable: false,
      targetNavigable: false,
    };
    current.canonicalModel.relations = [relation];
    let request: ExecuteDiagramOperationRequest | undefined;
    await configure(
      {
        execute: (_id: string, value: ExecuteDiagramOperationRequest) => {
          request = value;
          return of({
            newVersion: 8,
            canonicalModel: {
              ...current.canonicalModel,
              relations: [{ ...relation, sourceNavigable: true }],
            },
            viewState: current.viewState,
          });
        },
      },
      current,
    );
    const fixture = TestBed.createComponent(EditorPageComponent);
    await fixture.whenStable();
    const page = fixture.componentInstance;
    page.selectRelation(relation, { stopPropagation: () => {} } as unknown as MouseEvent);
    page.updateRelationNavigability('source', true);
    page.saveRelationNavigability();
    expect(request?.operation.type).toBe('CHANGE_NAVIGABILITY');
    expect(request?.operation.payload).toMatchObject({ relationId: 'r1', sourceNavigable: true });
    expect(page.renderedRelations()[0].path).toContain('C');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('supervisor');
    expect(fixture.nativeElement.textContent).toContain('subordinados');
    expect(page.diagram()?.canonicalModel.relations[0].sourceClassId).toBe('c1');
    expect(page.diagram()?.canonicalModel.relations[0].targetClassId).toBe('c1');
  });

  it('changes relation type with the authoritative response', async () => {
    const current = diagramWithClass();
    const relation: UmlRelation = {
      id: 'r1',
      sourceClassId: 'c1',
      targetClassId: 'c1',
      type: 'ASSOCIATION',
      sourceMultiplicity: { lower: '1', upper: '1' },
      targetMultiplicity: { lower: '1', upper: '1' },
      sourceRole: null,
      targetRole: null,
      sourceNavigable: false,
      targetNavigable: false,
    };
    current.canonicalModel.relations = [relation];
    let request: ExecuteDiagramOperationRequest | undefined;
    await configure(
      {
        execute: (_id: string, value: ExecuteDiagramOperationRequest) => {
          request = value;
          return of({
            newVersion: 8,
            canonicalModel: {
              ...current.canonicalModel,
              relations: [{ ...relation, type: 'COMPOSITION' }],
            },
            viewState: current.viewState,
          });
        },
      },
      current,
    );
    const page = TestBed.createComponent(EditorPageComponent).componentInstance;
    page.selectRelation(relation, { stopPropagation: () => {} } as unknown as MouseEvent);
    page.changeSelectedRelationType('COMPOSITION');
    expect(request?.operation.type).toBe('CHANGE_RELATION_TYPE');
    expect(request?.operation.baseVersion).toBe(7);
    expect(request?.operation.payload).toEqual({ relationId: 'r1', type: 'COMPOSITION' });
    expect(page.diagram()?.canonicalModel.relations[0].type).toBe('COMPOSITION');
    expect(page.diagram()?.version).toBe(8);
    expect(page.selectedRelationId()).toBe('r1');
  });

  it('creates an association class atomically and renders its dashed link', async () => {
    const current = diagramWithClass();
    const relation: UmlRelation = {
      id: 'r1',
      sourceClassId: 'c1',
      targetClassId: 'c1',
      type: 'ASSOCIATION',
      sourceMultiplicity: { lower: '1', upper: '1' },
      targetMultiplicity: { lower: '1', upper: '1' },
      sourceRole: null,
      targetRole: null,
      sourceNavigable: false,
      targetNavigable: false,
    };
    current.canonicalModel.relations = [relation];
    let request: ExecuteDiagramOperationRequest | undefined;
    await configure(
      {
        execute: (_id: string, value: ExecuteDiagramOperationRequest) => {
          request = value;
          return of({
            newVersion: 8,
            canonicalModel: {
              ...current.canonicalModel,
              classes: [
                ...current.canonicalModel.classes,
                { id: 'association-class', name: 'ClaseAsociacion1', isAbstract: false, attributes: [], methods: [] },
              ],
              associationClassLinks: [{ id: 'link-1', relationId: 'r1', classId: 'association-class' }],
            },
            viewState: {
              ...current.viewState,
              nodes: [...current.viewState.nodes, { classId: 'association-class', x: 80, y: 300, width: 240, height: 180 }],
            },
          });
        },
      },
      current,
    );
    const page = TestBed.createComponent(EditorPageComponent).componentInstance;
    page.selectRelation(relation, { stopPropagation: () => {} } as unknown as MouseEvent);
    page.createAssociationClass();
    expect(request?.operation.type).toBe('CREATE_ASSOCIATION_CLASS');
    expect(request?.operation.payload).toMatchObject({ relationId: 'r1', name: 'ClaseAsociacion1' });
    expect(page.diagram()?.canonicalModel.associationClassLinks?.[0].relationId).toBe('r1');
    expect(page.renderedAssociationClassLinks()[0].path).toContain('L');
  });

  it('keeps the class and modal open on a version conflict', async () => {
    const current = diagramWithClass();
    await configure(
      {
        execute: () =>
          throwError(() => new HttpErrorResponse({ status: 409, statusText: 'Conflict' })),
      },
      current,
    );
    const page = TestBed.createComponent(EditorPageComponent).componentInstance;
    page.selectedClassId.set('c1');
    page.removeSelectedClass();
    page.confirmClassDeletion();
    expect(page.pendingClassDeletion()?.id).toBe('c1');
    expect(page.diagram()?.canonicalModel.classes).toHaveLength(1);
    expect(page.classDeleteError()).toContain('diagrama cambi');
  });

  it('previews resize locally and sends one RESIZE_CLASS on release', async () => {
    const current = diagramWithClass();
    let executions = 0;
    let request: ExecuteDiagramOperationRequest | undefined;
    await configure(
      {
        execute: (_id: string, value: ExecuteDiagramOperationRequest) => {
          executions++;
          request = value;
          return of({
            newVersion: 8,
            canonicalModel: current.canonicalModel,
            viewState: {
              ...current.viewState,
              nodes: [{ ...current.viewState.nodes[0], width: 300, height: 220 }],
            },
          });
        },
      },
      current,
    );
    const fixture = TestBed.createComponent(EditorPageComponent);
    await fixture.whenStable();
    const page = fixture.componentInstance;
    const card = fixture.nativeElement.querySelector('.uml-class') as HTMLElement;
    const item = page.renderedClasses()[0];
    page.selectedClassId.set('c1');
    page.onResizePointerDown(item, {
      currentTarget: card,
      stopPropagation: () => {},
      pointerId: 2,
      clientX: 300,
      clientY: 250,
    } as unknown as PointerEvent);
    page.onCanvasPointerMove({
      currentTarget: fixture.nativeElement.querySelector('.canvas'),
      pointerId: 2,
      clientX: 360,
      clientY: 290,
    } as unknown as PointerEvent);
    expect(executions).toBe(0);
    expect(page.renderedClasses()[0].width).toBe(300);
    expect(page.renderedClasses()[0].height).toBe(220);
    page.onCanvasPointerUp({ pointerId: 2 } as unknown as PointerEvent);
    expect(executions).toBe(1);
    expect(request?.operation.type).toBe('RESIZE_CLASS');
    expect(request?.operation.baseVersion).toBe(7);
    expect(request?.operation.payload).toEqual({ classId: 'c1', width: 300, height: 220 });
    expect(page.diagram()?.version).toBe(8);
  });

  it('persists class appearance through viewState without changing canonicalModel', async () => {
    const current = diagramWithClass();
    let request: ExecuteDiagramOperationRequest | undefined;
    await configure(
      {
        execute: (_id: string, value: ExecuteDiagramOperationRequest) => {
          request = value;
          return of({
            newVersion: 8,
            canonicalModel: current.canonicalModel,
            viewState: {
              ...current.viewState,
              nodes: [{ ...current.viewState.nodes[0], headerColor: '#eee8ff' }],
            },
          });
        },
      },
      current,
    );
    const page = TestBed.createComponent(EditorPageComponent).componentInstance;
    page.selectedClassId.set('c1');
    page.changeClassStyle('headerColor', { target: { value: '#eee8ff' } } as unknown as Event);
    expect(request?.operation.type).toBe('UPDATE_CLASS_STYLE');
    expect(request?.operation.baseVersion).toBe(7);
    expect(request?.operation.payload).toEqual({
      classId: 'c1',
      headerColor: '#eee8ff',
      bodyColor: null,
      borderColor: null,
    });
    expect(page.diagram()?.viewState.nodes[0].headerColor).toBe('#eee8ff');
    expect(page.diagram()?.canonicalModel).toBe(current.canonicalModel);
    expect(page.diagram()?.version).toBe(8);
  });

  it('previews a selected class during drag and sends one MOVE_CLASS on release', async () => {
    const current = diagramWithClass();
    let executions = 0;
    let request: ExecuteDiagramOperationRequest | undefined;
    await configure(
      {
        execute: (_id: string, value: ExecuteDiagramOperationRequest) => {
          executions++;
          request = value;
          return of({
            newVersion: 8,
            canonicalModel: current.canonicalModel,
            viewState: {
              ...current.viewState,
              nodes: [{ ...current.viewState.nodes[0], x: 220, y: 180 }],
            },
          });
        },
      },
      current,
    );
    const fixture = TestBed.createComponent(EditorPageComponent);
    await fixture.whenStable();
    const page = fixture.componentInstance;
    const card = fixture.nativeElement.querySelector('.uml-class') as HTMLElement;
    const canvas = fixture.nativeElement.querySelector('.canvas') as HTMLElement;
    Object.defineProperty(canvas, 'getBoundingClientRect', {
      value: () => ({ left: 100, top: 50 }),
    });
    Object.defineProperty(canvas, 'clientWidth', { value: 800 });
    Object.defineProperty(canvas, 'clientHeight', { value: 600 });
    page.onClassPointerDown(current.canonicalModel.classes[0], {
      currentTarget: card,
      stopPropagation: () => {},
      pointerId: 1,
      clientX: 160,
      clientY: 120,
    } as unknown as PointerEvent);
    page.onCanvasPointerMove({
      currentTarget: canvas,
      pointerId: 1,
      clientX: 340,
      clientY: 250,
    } as unknown as PointerEvent);
    expect(executions).toBe(0);
    expect(page.dragState()?.isDragging).toBe(true);
    expect(page.renderedClasses()[0].x).toBe(220);
    page.onCanvasPointerUp({ currentTarget: canvas, pointerId: 1 } as unknown as PointerEvent);
    expect(executions).toBe(1);
    expect(request?.operation.type).toBe('MOVE_CLASS');
    expect(request?.operation.baseVersion).toBe(7);
    expect(request?.operation.payload).toEqual({ classId: 'c1', x: 220, y: 180 });
    expect(page.diagram()?.version).toBe(8);
    expect(page.dragState()).toBeNull();
  });

  it('does not drag in CLASS mode or send a request for a click below the threshold', async () => {
    const current = diagramWithClass();
    let executions = 0;
    await configure(
      {
        execute: () => {
          executions++;
          return of({});
        },
      },
      current,
    );
    const fixture = TestBed.createComponent(EditorPageComponent);
    fixture.detectChanges();
    const page = fixture.componentInstance;
    const card = fixture.nativeElement.querySelector('.uml-class') as HTMLElement;
    const canvas = fixture.nativeElement.querySelector('.canvas') as HTMLElement;
    Object.defineProperty(canvas, 'getBoundingClientRect', {
      value: () => ({ left: 100, top: 50 }),
    });
    page.setActiveTool('CLASS');
    page.onClassPointerDown(current.canonicalModel.classes[0], {
      currentTarget: card,
      stopPropagation: () => {},
      pointerId: 1,
      clientX: 160,
      clientY: 120,
    } as unknown as PointerEvent);
    expect(page.dragState()).toBeNull();
    page.setActiveTool('SELECT');
    page.onClassPointerDown(current.canonicalModel.classes[0], {
      currentTarget: card,
      stopPropagation: () => {},
      pointerId: 2,
      clientX: 160,
      clientY: 120,
    } as unknown as PointerEvent);
    page.onCanvasPointerMove({
      currentTarget: canvas,
      pointerId: 2,
      clientX: 162,
      clientY: 121,
    } as unknown as PointerEvent);
    page.onCanvasPointerUp({ currentTarget: canvas, pointerId: 2 } as unknown as PointerEvent);
    expect(executions).toBe(0);
    expect(page.selectedClassId()).toBe('c1');
  });

  it('activates class insertion and creates at the real canvas coordinates', async () => {
    let request: ExecuteDiagramOperationRequest | undefined;
    await configure({
      execute: (_id: string, value: ExecuteDiagramOperationRequest) => {
        request = value;
        const classId = (value.operation.payload as unknown as { classId: string }).classId;
        return of({
          newVersion: 8,
          canonicalModel: {
            ...detail.canonicalModel,
            classes: [
              { id: classId, name: 'Clase1', isAbstract: false, attributes: [], methods: [] },
            ],
          },
          viewState: {
            diagramId: 'd1',
            nodes: [{ classId, x: 0, y: 120, width: 240, height: 180 }],
            relations: [],
          },
        });
      },
    });
    const fixture = TestBed.createComponent(EditorPageComponent);
    await fixture.whenStable();
    const page = fixture.componentInstance;
    page.setActiveTool('CLASS');
    const canvas = fixture.nativeElement.querySelector('.canvas') as HTMLElement;
    Object.defineProperty(canvas, 'getBoundingClientRect', {
      value: () => ({ left: 100, top: 50 }),
    });
    canvas.dispatchEvent(new MouseEvent('click', { bubbles: true, clientX: 220, clientY: 200 }));
    expect(request?.operation.type).toBe('CREATE_CLASS');
    expect(request?.operation.baseVersion).toBe(7);
    expect((request?.operation.payload as { name: string }).name).toBe('Clase1');
    expect((request?.operation.payload as { x: number }).x).toBe(0);
    expect((request?.operation.payload as { y: number }).y).toBe(120);
    expect(page.activeTool()).toBe('SELECT');
    const createdClassId = (request?.operation.payload as unknown as { classId: string }).classId;
    expect(page.selectedClassId()).toBe(createdClassId);
    expect(page.editingClassId()).toBe(createdClassId);
  });

  it('renames inline with RENAME_CLASS and uses the response as authority', async () => {
    const current: DiagramDetail = {
      ...detail,
      canonicalModel: {
        ...detail.canonicalModel,
        classes: [{ id: 'c1', name: 'Clase1', isAbstract: false, attributes: [], methods: [] }],
      },
      viewState: {
        ...detail.viewState,
        nodes: [{ classId: 'c1', x: 10, y: 20, width: 240, height: 180 }],
      },
    };
    let request: ExecuteDiagramOperationRequest | undefined;
    const renamed = {
      ...current,
      version: 8,
      canonicalModel: {
        ...current.canonicalModel,
        classes: [{ ...current.canonicalModel.classes[0], name: 'Cliente' }],
      },
    };
    await configure(
      {
        execute: (_id: string, value: ExecuteDiagramOperationRequest) => {
          request = value;
          return of({
            newVersion: 8,
            canonicalModel: renamed.canonicalModel,
            viewState: renamed.viewState,
          });
        },
      },
      current,
    );
    const page = TestBed.createComponent(EditorPageComponent).componentInstance;
    page.beginInlineEdit('c1');
    page.editingName.set('Cliente');
    page.confirmInlineEdit();
    expect(request?.operation.type).toBe('RENAME_CLASS');
    expect(request?.operation.baseVersion).toBe(7);
    expect(page.diagram()?.canonicalModel.classes[0].name).toBe('Cliente');
    expect(page.diagram()?.version).toBe(8);
    page.cancelInlineEdit();
  });

  it('cancels inline editing with Escape without deleting the class', async () => {
    const current = {
      ...detail,
      canonicalModel: {
        ...detail.canonicalModel,
        classes: [{ id: 'c1', name: 'Clase1', isAbstract: false, attributes: [], methods: [] }],
      },
    };
    await configure({ execute: () => of({}) }, current);
    const page = TestBed.createComponent(EditorPageComponent).componentInstance;
    page.beginInlineEdit('c1');
    page.cancelInlineEdit();
    expect(page.diagram()?.canonicalModel.classes).toHaveLength(1);
    expect(page.editingClassId()).toBeNull();
  });

  it('rejects duplicate names without executing and keeps the selection flow safe', async () => {
    const current = {
      ...detail,
      canonicalModel: {
        ...detail.canonicalModel,
        classes: [
          { id: 'c1', name: 'Cliente', isAbstract: false, attributes: [], methods: [] },
          { id: 'c2', name: 'Pedido', isAbstract: false, attributes: [], methods: [] },
        ],
      },
    };
    let executions = 0;
    await configure(
      {
        execute: () => {
          executions++;
          return of({});
        },
      },
      current,
    );
    const page = TestBed.createComponent(EditorPageComponent).componentInstance;
    page.beginInlineEdit('c2');
    page.editingName.set(' cliente ');
    page.confirmInlineEdit();
    expect(executions).toBe(0);
    expect(page.renameError()).toContain('Ya existe');
  });

  it('returns to select and reports a conflict when creation fails', async () => {
    await configure({ execute: () => throwError(() => new HttpErrorResponse({ status: 409 })) });
    const page = TestBed.createComponent(EditorPageComponent).componentInstance;
    page.setActiveTool('CLASS');
    page.createClassAt(10, 20);
    expect(page.activeTool()).toBe('SELECT');
    expect(page.error()).toContain('cambió en otra sesión');
    expect(page.diagram()?.canonicalModel.classes).toHaveLength(0);
  });

  it('adds an attribute with the selected class and applies the authoritative response', async () => {
    const current = diagramWithClass();
    let request: ExecuteDiagramOperationRequest | undefined;
    const attribute: UmlAttribute = {
      id: 'a1',
      name: 'email',
      type: 'String',
      visibility: 'PRIVATE',
      isStatic: false,
      isFinal: false,
      defaultValue: null,
      primaryKey: false,
    };
    await configure(
      {
        execute: (_id: string, value: ExecuteDiagramOperationRequest) => {
          request = value;
          return of({
            newVersion: 8,
            canonicalModel: {
              ...current.canonicalModel,
              classes: [{ ...current.canonicalModel.classes[0], attributes: [attribute] }],
            },
            viewState: current.viewState,
          });
        },
      },
      current,
    );
    const page = TestBed.createComponent(EditorPageComponent).componentInstance;
    page.selectedClassId.set('c1');
    page.startAddAttribute();
    page.updateAttributeDraft('name', ' email ');
    page.saveAttribute();

    expect(request?.operation.type).toBe('ADD_ATTRIBUTE');
    expect(request?.operation.baseVersion).toBe(7);
    expect(request?.operation.payload).toMatchObject({ classId: 'c1' });
    expect(page.diagram()?.canonicalModel.classes[0].attributes[0].name).toBe('email');
    expect(page.diagram()?.version).toBe(8);
    expect(page.selectedClassId()).toBe('c1');
    expect(page.attributeDraft()).toBeNull();
  });

  it('rejects duplicate attribute names without sending an operation', async () => {
    const current = diagramWithClass();
    current.canonicalModel.classes[0].attributes = [
      {
        id: 'a1',
        name: 'email',
        type: 'String',
        visibility: 'PRIVATE',
        isStatic: false,
        isFinal: false,
        defaultValue: null,
        primaryKey: false,
      },
    ];
    let executions = 0;
    await configure(
      {
        execute: () => {
          executions++;
          return of({});
        },
      },
      current,
    );
    const page = TestBed.createComponent(EditorPageComponent).componentInstance;
    page.selectedClassId.set('c1');
    page.startAddAttribute();
    page.updateAttributeDraft('name', 'EMAIL');
    page.saveAttribute();
    expect(executions).toBe(0);
    expect(page.attributeError()).toContain('Ya existe');
  });

  it('updates and removes an attribute through their atomic operations', async () => {
    const current = diagramWithClass();
    const attribute: UmlAttribute = {
      id: 'a1',
      name: 'email',
      type: 'String',
      visibility: 'PRIVATE',
      isStatic: false,
      isFinal: false,
      defaultValue: null,
      primaryKey: false,
    };
    current.canonicalModel.classes[0].attributes = [attribute];
    const requests: ExecuteDiagramOperationRequest[] = [];
    await configure(
      {
        execute: (_id: string, request: ExecuteDiagramOperationRequest) => {
          requests.push(request);
          const operation =
            request.operation.type === 'UPDATE_ATTRIBUTE'
              ? { ...attribute, visibility: 'PUBLIC' }
              : undefined;
          return of({
            newVersion: current.version + requests.length,
            canonicalModel: {
              ...current.canonicalModel,
              classes: [
                { ...current.canonicalModel.classes[0], attributes: operation ? [operation] : [] },
              ],
            },
            viewState: current.viewState,
          });
        },
      },
      current,
    );
    const page = TestBed.createComponent(EditorPageComponent).componentInstance;
    page.selectedClassId.set('c1');
    page.startEditAttribute(attribute);
    page.updateAttributeDraft('visibility', 'PUBLIC');
    page.saveAttribute();
    expect(requests[0].operation.type).toBe('UPDATE_ATTRIBUTE');
    expect(page.diagram()?.canonicalModel.classes[0].attributes[0].visibility).toBe('PUBLIC');

    page.removeAttribute(page.diagram()!.canonicalModel.classes[0].attributes[0]);
    expect(page.pendingAttributeDeletion()?.name).toBe('email');
    expect(requests).toHaveLength(1);
    page.confirmAttributeDeletion();
    expect(requests[1].operation.type).toBe('REMOVE_ATTRIBUTE');
    expect(page.diagram()?.canonicalModel.classes[0].attributes).toHaveLength(0);
    expect(page.selectedClassId()).toBe('c1');
  });

  it('opens the custom deletion modal and keeps advanced options collapsed for new attributes', async () => {
    const current = diagramWithClass();
    const attribute: UmlAttribute = {
      id: 'a1',
      name: 'id',
      type: 'Long',
      visibility: 'PRIVATE',
      isStatic: true,
      isFinal: true,
      defaultValue: '1',
      primaryKey: true,
    };
    current.canonicalModel.classes[0].attributes = [attribute];
    let executions = 0;
    await configure(
      {
        execute: () => {
          executions++;
          return of({});
        },
      },
      current,
    );
    const page = TestBed.createComponent(EditorPageComponent).componentInstance;
    page.selectedClassId.set('c1');
    page.startAddAttribute();
    expect(page.advancedAttributeOptionsOpen()).toBe(false);
    page.removeAttribute(attribute);
    expect(page.pendingAttributeDeletion()).toBe(attribute);
    page.cancelAttributeDeletion();
    expect(page.pendingAttributeDeletion()).toBeNull();
    expect(executions).toBe(0);
    page.startEditAttribute(attribute);
    expect(page.advancedAttributeOptionsOpen()).toBe(true);
  });

  it('prevents a second delete request while the first one is saving', async () => {
    const current = diagramWithClass();
    const attribute: UmlAttribute = {
      id: 'a1',
      name: 'email',
      type: 'String',
      visibility: 'PRIVATE',
      isStatic: false,
      isFinal: false,
      defaultValue: null,
      primaryKey: false,
    };
    current.canonicalModel.classes[0].attributes = [attribute];
    let executions = 0;
    await configure(
      {
        execute: () => {
          executions++;
          return NEVER;
        },
      },
      current,
    );
    const page = TestBed.createComponent(EditorPageComponent).componentInstance;
    page.selectedClassId.set('c1');
    page.removeAttribute(attribute);
    page.confirmAttributeDeletion();
    page.confirmAttributeDeletion();
    expect(executions).toBe(1);
    expect(page.attributeSaving()).toBe(true);
  });

  it('manages methods and parameters with atomic operations', async () => {
    const current = diagramWithClass();
    let serverMethod: UmlMethod | undefined = {
      id: 'm1',
      name: 'buscar',
      returnType: 'Cliente',
      visibility: 'PUBLIC',
      isStatic: false,
      parameters: [],
    };
    const requests: ExecuteDiagramOperationRequest[] = [];
    await configure(
      {
        execute: (_id: string, request: ExecuteDiagramOperationRequest) => {
          requests.push(request);
          const type = request.operation.type;
          if (type === 'ADD_METHOD')
            serverMethod = (request.operation.payload as { method: UmlMethod }).method;
          if (type === 'UPDATE_METHOD' && serverMethod)
            serverMethod = {
              ...serverMethod,
              ...(request.operation.payload as {
                name: string;
                returnType: string;
                visibility: string;
                isStatic: boolean;
              }),
            };
          if (type === 'ADD_PARAMETER' && serverMethod)
            serverMethod = {
              ...serverMethod,
              parameters: [
                ...serverMethod.parameters,
                (
                  request.operation.payload as {
                    parameter: { id: string; name: string; type: string };
                  }
                ).parameter,
              ],
            };
          if (type === 'UPDATE_PARAMETER' && serverMethod)
            serverMethod = {
              ...serverMethod,
              parameters: serverMethod.parameters.map((parameter) =>
                parameter.id === (request.operation.payload as { parameterId: string }).parameterId
                  ? {
                      ...parameter,
                      ...(request.operation.payload as { name: string; type: string }),
                    }
                  : parameter,
              ),
            };
          if (type === 'REMOVE_PARAMETER' && serverMethod)
            serverMethod = { ...serverMethod, parameters: [] };
          if (type === 'REMOVE_METHOD') serverMethod = undefined;
          const methods = serverMethod ? [serverMethod] : [];
          return of({
            newVersion: current.version + requests.length,
            canonicalModel: {
              ...current.canonicalModel,
              classes: [{ ...current.canonicalModel.classes[0], methods }],
            },
            viewState: current.viewState,
          });
        },
      },
      current,
    );
    const page = TestBed.createComponent(EditorPageComponent).componentInstance;
    page.selectedClassId.set('c1');
    page.startAddMethod();
    page.updateMethodDraft('name', 'buscar');
    page.updateMethodDraft('returnType', 'Cliente');
    page.saveMethod();
    expect(requests[0].operation.type).toBe('ADD_METHOD');
    page.startAddParameter();
    page.updateParameterDraft('name', 'id');
    page.updateParameterDraft('type', 'Long');
    page.saveParameter();
    expect(requests[1].operation.type).toBe('ADD_PARAMETER');
    expect(page.formatMethod(page.diagram()!.canonicalModel.classes[0].methods[0])).toBe(
      'buscar(id: Long): Cliente',
    );
    page.startEditMethod(page.diagram()!.canonicalModel.classes[0].methods[0]);
    page.updateMethodDraft('visibility', 'PRIVATE');
    page.saveMethod();
    expect(requests[2].operation.type).toBe('UPDATE_METHOD');
    page.startEditMethod(page.diagram()!.canonicalModel.classes[0].methods[0]);
    page.removeParameter(page.diagram()!.canonicalModel.classes[0].methods[0].parameters[0]);
    page.confirmParameterDeletion();
    expect(requests[3].operation.type).toBe('REMOVE_PARAMETER');
    page.removeMethod(page.diagram()!.canonicalModel.classes[0].methods[0]);
    page.confirmMethodDeletion();
    expect(requests[4].operation.type).toBe('REMOVE_METHOD');
    expect(page.selectedClassId()).toBe('c1');
  });

  it('keeps zoom controls bounded and reset preserves pan', async () => {
    await configure({ execute: () => NEVER });
    const page = TestBed.createComponent(EditorPageComponent).componentInstance;

    page.zoom.set(0.25);
    page.zoomOut();
    expect(page.zoom()).toBe(0.25);
    page.zoomIn();
    expect(page.zoom()).toBeGreaterThan(0.25);

    page.panX.set(-240);
    page.panY.set(120);
    page.zoom.set(1.5);
    page.resetZoom();
    expect(page.zoom()).toBe(1);
    expect(page.panX()).toBe(-240);
    expect(page.panY()).toBe(120);
  });

  it('converts screen coordinates to world coordinates using local viewport state', async () => {
    await configure({ execute: () => NEVER });
    const page = TestBed.createComponent(EditorPageComponent).componentInstance;
    const viewport = document.createElement('main');
    viewport.getBoundingClientRect = () => ({
      x: 100,
      y: 50,
      top: 50,
      left: 100,
      right: 1100,
      bottom: 750,
      width: 1000,
      height: 700,
      toJSON: () => ({}),
    });
    page.zoom.set(0.5);
    page.panX.set(-500);
    page.panY.set(-200);
    expect(page.screenToWorld(350, 200, viewport)).toEqual({ x: 1500, y: 700 });
  });

  it('pans freely without issuing an operation request', async () => {
    let executions = 0;
    await configure({
      execute: () => {
        executions++;
        return NEVER;
      },
    });
    const page = TestBed.createComponent(EditorPageComponent).componentInstance;
    const viewport = document.createElement('main');
    const event = (values: Partial<PointerEvent>): PointerEvent =>
      ({
        pointerId: 1,
        clientX: 0,
        clientY: 0,
        button: 0,
        target: viewport,
        currentTarget: viewport,
        preventDefault: () => undefined,
        ...values,
      }) as PointerEvent;

    page.spacePressed.set(true);
    page.onCanvasPointerDown(event({ clientX: 10, clientY: 20 }));
    page.onCanvasPointerMove(event({ clientX: 1510, clientY: -180 }));
    expect(page.panX()).toBe(1500);
    expect(page.panY()).toBe(-200);
    page.onCanvasPointerUp(event({ clientX: 1510, clientY: -180 }));
    expect(executions).toBe(0);
  });

  it('fits visible nodes using the actual viewport dimensions', async () => {
    const current = diagramWithClass();
    current.viewState.nodes = [{ classId: 'c1', x: 100, y: 100, width: 240, height: 180 }];
    await configure({ execute: () => NEVER }, current);
    const page = TestBed.createComponent(EditorPageComponent).componentInstance;
    const viewport = document.createElement('main');
    viewport.classList.add('canvas');
    Object.defineProperties(viewport, { clientWidth: { value: 1000 }, clientHeight: { value: 700 } });
    const button = document.createElement('button');
    viewport.append(button);
    page.fitToContent({ currentTarget: button } as unknown as Event);
    expect(page.zoom()).toBeGreaterThan(0.25);
    expect(Number.isFinite(page.panX())).toBe(true);
    expect(Number.isFinite(page.panY())).toBe(true);
  });
});
