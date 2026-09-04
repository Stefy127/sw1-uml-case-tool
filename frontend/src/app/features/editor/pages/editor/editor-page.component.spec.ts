import { ActivatedRoute, provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { DiagramDetail } from '../../models/diagram.model';
import { DiagramService } from '../../services/diagram.service';
import { EditorPageComponent } from './editor-page.component';

const detail: DiagramDetail = {
  id: 'd1',
  projectId: 'p1',
  name: 'Modelo real',
  version: 7,
  createdAt: '',
  updatedAt: '',
  canonicalModel: {
    id: 'd1',
    name: 'Modelo real',
    version: 7,
    classes: [
      {
        id: 'class-1',
        name: 'Cliente',
        isAbstract: true,
        attributes: [
          {
            id: 'a1',
            name: 'email',
            type: 'String',
            visibility: 'PRIVATE',
            isStatic: false,
            isFinal: false,
            defaultValue: null,
            primaryKey: true,
          },
        ],
        methods: [
          {
            id: 'm1',
            name: 'guardar',
            returnType: 'void',
            visibility: 'PUBLIC',
            isStatic: false,
            parameters: [{ id: 'p', name: 'valor', type: 'String' }],
          },
        ],
      },
    ],
    relations: [],
  },
  viewState: {
    diagramId: 'd1',
    nodes: [{ classId: 'class-1', x: 120, y: 140, width: 280, height: 210 }],
    relations: [],
  },
};

describe('EditorPageComponent', () => {
  it('renders canonical classes using their visual node state', async () => {
    await TestBed.configureTestingModule({
      imports: [EditorPageComponent],
      providers: [
        { provide: DiagramService, useValue: { getDiagramById: () => of(detail) } },
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'd1' } } } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(EditorPageComponent);
    await fixture.whenStable();
    const card = fixture.nativeElement.querySelector('.uml-class') as HTMLElement;
    expect(fixture.componentInstance.renderedClasses().map((item) => item.umlClass.name)).toEqual([
      'Cliente',
    ]);
    expect(card.textContent).toContain('email: String');
    expect(card.textContent).toContain('guardar(valor: String): void');
    expect(card.style.left).toBe('120px');
    expect(card.style.top).toBe('140px');
    expect(fixture.nativeElement.textContent).toContain('versión 7');
    expect(fixture.nativeElement.textContent).not.toContain('Usuario');
  });

  it('uses deterministic fallback coordinates and shows an empty state', async () => {
    const empty = {
      ...detail,
      canonicalModel: { ...detail.canonicalModel, classes: [] },
      viewState: { ...detail.viewState, nodes: [] },
    };
    await TestBed.configureTestingModule({
      imports: [EditorPageComponent],
      providers: [
        { provide: DiagramService, useValue: { getDiagramById: () => of(empty) } },
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'd1' } } } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(EditorPageComponent);
    await fixture.whenStable();
    expect(fixture.componentInstance.renderedClasses()).toEqual([]);
    expect(fixture.nativeElement.textContent).toContain('Este diagrama aún no tiene clases');
  });
});
