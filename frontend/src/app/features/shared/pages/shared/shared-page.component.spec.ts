import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ProjectService } from '../../../projects/services/project.service';
import { SharedPageComponent } from './shared-page.component';

describe('SharedPageComponent', () => {
  const project = { id: 'p1', name: 'Prueba', description: 'Proyecto compartido', ownerUserId: 'owner', createdAt: '', updatedAt: '' };

  function setup(service: Partial<ProjectService>) {
    return TestBed.configureTestingModule({
      imports: [SharedPageComponent],
      providers: [
        { provide: ProjectService, useValue: service },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => null } } } },
      ],
    }).compileComponents();
  }

  it('renders a shared project with the Editor badge and CTA', async () => {
    await setup({ getSharedProjects: () => of([project]), getMyRole: () => of({ role: 'EDITOR' }) });
    const fixture = TestBed.createComponent(SharedPageComponent);
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Prueba');
    expect(fixture.nativeElement.textContent).toContain('Editor');
    expect(fixture.nativeElement.textContent).toContain('Abrir proyecto');
  });

  it('renders Solo lectura for a Viewer', async () => {
    await setup({ getSharedProjects: () => of([project]), getMyRole: () => of({ role: 'VIEWER' }) });
    const fixture = TestBed.createComponent(SharedPageComponent);
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Solo lectura');
  });

  it('renders the empty state', async () => {
    await setup({ getSharedProjects: () => of([]) });
    const fixture = TestBed.createComponent(SharedPageComponent);
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nadie ha compartido proyectos contigo todavía.');
  });

  it('renders retryable error state', async () => {
    await setup({ getSharedProjects: () => throwError(() => new Error('failure')) });
    const fixture = TestBed.createComponent(SharedPageComponent);
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No pudimos cargar tus proyectos compartidos.');
    expect(fixture.nativeElement.textContent).toContain('Reintentar');
  });
});
