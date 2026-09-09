import { provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';

import { DiagramService } from '../../../editor/services/diagram.service';
import { ProjectService } from '../../services/project.service';
import { ProjectDetailPageComponent } from './project-detail-page.component';

describe('ProjectDetailPageComponent', () => {
  it('loads the project and its diagrams', async () => {
    const projectService = {
      getProjectById: () =>
        of({
          id: 'p1',
          name: 'Demo',
          description: null,
          ownerUserId: 'u1',
          createdAt: '',
          updatedAt: '',
        }),
    };
    const diagramService = { getDiagramsByProject: () => of([]) };
    await TestBed.configureTestingModule({
      imports: [ProjectDetailPageComponent],
      providers: [
        { provide: ProjectService, useValue: projectService },
        { provide: DiagramService, useValue: diagramService },
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'p1' } } } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ProjectDetailPageComponent);
    await fixture.whenStable();
    expect(fixture.componentInstance.project()?.name).toBe('Demo');
    expect(fixture.componentInstance.diagrams()).toEqual([]);
  });

  it('creates a diagram with the route project id and updates the list', async () => {
    const created = {
      id: 'd1',
      projectId: 'p1',
      name: 'Nuevo UML',
      version: 0,
      createdAt: '',
      updatedAt: '',
      canonicalModel: {},
      viewState: {},
    };
    let receivedProjectId = '';
    let receivedName = '';
    const projectService = {
      getProjectById: () =>
        of({
          id: 'p1',
          name: 'Demo',
          description: null,
          ownerUserId: 'u1',
          createdAt: '',
          updatedAt: '',
        }),
    };
    const diagramService = {
      getDiagramsByProject: () => of([]),
      createDiagram: (projectId: string, name: string) => {
        receivedProjectId = projectId;
        receivedName = name;
        return of(created);
      },
    };
    await TestBed.configureTestingModule({
      imports: [ProjectDetailPageComponent],
      providers: [
        { provide: ProjectService, useValue: projectService },
        { provide: DiagramService, useValue: diagramService },
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'p1' } } } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ProjectDetailPageComponent);
    await fixture.whenStable();
    const page = fixture.componentInstance;
    page.showCreate.set(true);
    page.createName.set(' Nuevo UML ');
    page.createDiagram();

    expect(receivedProjectId).toBe('p1');
    expect(receivedName).toBe('Nuevo UML');
    expect(page.diagrams()).toEqual([created]);
    expect(page.showCreate()).toBe(false);
    expect(page.createName()).toBe('');
  });

  it('keeps the modal open when diagram creation fails', async () => {
    const projectService = {
      getProjectById: () =>
        of({
          id: 'p1',
          name: 'Demo',
          description: null,
          ownerUserId: 'u1',
          createdAt: '',
          updatedAt: '',
        }),
    };
    const diagramService = {
      getDiagramsByProject: () => of([]),
      createDiagram: () => throwError(() => new Error('HTTP error')),
    };
    await TestBed.configureTestingModule({
      imports: [ProjectDetailPageComponent],
      providers: [
        { provide: ProjectService, useValue: projectService },
        { provide: DiagramService, useValue: diagramService },
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'p1' } } } },
      ],
    }).compileComponents();

    const page = TestBed.createComponent(ProjectDetailPageComponent).componentInstance;
    page.showCreate.set(true);
    page.createName.set('Diagrama');
    page.createDiagram();
    expect(page.showCreate()).toBe(true);
    expect(page.createError()).toBe('No se pudo crear el diagrama.');
  });

  it('opens the share modal, loads members and closes it', async () => {
    let requestedProjectId = '';
    const projectService = {
      getProjectById: () => of({ id: 'p1', name: 'Demo', description: null, ownerUserId: 'u1', createdAt: '', updatedAt: '' }),
      getMembers: (projectId: string) => { requestedProjectId = projectId; return of([{ id: 'm1', userId: 'u1', firstName: 'Owner', lastName: 'One', email: 'owner@test.com', role: 'OWNER' as const }]); },
      getMyRole: () => of({ role: 'OWNER' as const }),
    };
    const diagramService = { getDiagramsByProject: () => of([]) };
    await TestBed.configureTestingModule({
      imports: [ProjectDetailPageComponent],
      providers: [
        { provide: ProjectService, useValue: projectService },
        { provide: DiagramService, useValue: diagramService },
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'p1' } } } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(ProjectDetailPageComponent);
    await fixture.whenStable();
    const page = fixture.componentInstance;
    page.currentUserRole.set('OWNER');
    fixture.detectChanges();
    const shareButton = (Array.from(fixture.nativeElement.querySelectorAll('button')) as HTMLButtonElement[]).find((button) => button.textContent?.includes('Compartir')) as HTMLButtonElement;
    shareButton.click();
    fixture.detectChanges();

    expect(page.shareOpen()).toBe(true);
    expect(requestedProjectId).toBe('p1');
    expect(fixture.nativeElement.querySelector('.share-panel')).not.toBeNull();
    expect(fixture.nativeElement.textContent).toContain('owner@test.com');

    (fixture.nativeElement.querySelector('.share-panel .close-button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(page.shareOpen()).toBe(false);
  });
});
