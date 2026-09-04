import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { DiagramDetail } from '../../models/diagram.model';
import { ExecuteDiagramOperationRequest } from '../../models/diagram-operation.model';
import { DiagramOperationService } from '../../services/diagram-operation.service';
import { DiagramService } from '../../services/diagram.service';
import { EditorPageComponent } from './editor-page.component';

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

function configure(operationService: object, current = detail): Promise<void> {
  return TestBed.configureTestingModule({
    imports: [EditorPageComponent],
    providers: [
      { provide: DiagramService, useValue: { getDiagramById: () => of(current) } },
      { provide: DiagramOperationService, useValue: operationService },
      provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'd1' } } } },
    ],
  }).compileComponents();
}

describe('EditorPageComponent', () => {
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
    await fixture.whenStable();
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
        const classId = value.operation.payload.classId;
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
    expect(page.selectedClassId()).toBe(request?.operation.payload.classId);
    expect(page.editingClassId()).toBe(request?.operation.payload.classId);
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
});
