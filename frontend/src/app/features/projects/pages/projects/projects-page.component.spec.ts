import { provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { Component } from '@angular/core';
import { of } from 'rxjs';
import { Router } from '@angular/router';

import { ProjectService } from '../../services/project.service';
import { ProjectsPageComponent } from './projects-page.component';

@Component({ standalone: true, template: '' })
class TestRouteComponent {}

describe('ProjectsPageComponent', () => {
  it('loads projects and supports an empty state', async () => {
    const projectService = { getProjectsByOwner: () => of([]) };
    await TestBed.configureTestingModule({
      imports: [ProjectsPageComponent],
      providers: [{ provide: ProjectService, useValue: projectService }, provideRouter([])],
    }).compileComponents();

    const fixture = TestBed.createComponent(ProjectsPageComponent);
    await fixture.whenStable();
    expect(fixture.componentInstance.projects()).toEqual([]);
    expect(fixture.nativeElement.textContent).toContain('No hay proyectos');
  });

  it('opens a project when clicking the card or pressing Enter/Space', async () => {
    await TestBed.configureTestingModule({
      imports: [ProjectsPageComponent],
      providers: [{ provide: ProjectService, useValue: { getProjectsByOwner: () => of([]) } }, provideRouter([])],
    }).compileComponents();
    const fixture = TestBed.createComponent(ProjectsPageComponent);
    const page = fixture.componentInstance;
    page.projects.set([project()]);
    page.loading.set(false);
    fixture.detectChanges();
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const card = fixture.nativeElement.querySelector('.project-card') as HTMLElement;

    card.click();
    page.onCardKeydown(new KeyboardEvent('keydown', { key: 'Enter' }), 'p1');
    page.onCardKeydown(new KeyboardEvent('keydown', { key: ' ' }), 'p1');

    expect(navigate).toHaveBeenCalledTimes(3);
    expect(navigate).toHaveBeenCalledWith(['/projects', 'p1']);
  });

  it('opens and closes the card menu without navigating', async () => {
    await TestBed.configureTestingModule({
      imports: [ProjectsPageComponent],
      providers: [{ provide: ProjectService, useValue: { getProjectsByOwner: () => of([]) } }, provideRouter([])],
    }).compileComponents();
    const fixture = TestBed.createComponent(ProjectsPageComponent);
    const page = fixture.componentInstance;
    page.projects.set([project()]);
    page.loading.set(false);
    fixture.detectChanges();
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const trigger = fixture.nativeElement.querySelector('.project-menu-trigger') as HTMLButtonElement;

    trigger.click();
    fixture.detectChanges();
    expect(page.openMenuId()).toBe('p1');
    expect(fixture.nativeElement.querySelector('.project-menu')).not.toBeNull();
    expect((fixture.nativeElement.querySelector('.project-card') as HTMLElement).classList.contains('menu-open')).toBe(true);
    expect(getComputedStyle(fixture.nativeElement.querySelector('.project-card')).overflow).toBe('visible');
    expect(navigate).not.toHaveBeenCalled();

    document.dispatchEvent(new MouseEvent('click'));
    expect(page.openMenuId()).toBeNull();
  });

  it('does not bubble the open-project link into the card handler', async () => {
    await TestBed.configureTestingModule({
      imports: [ProjectsPageComponent],
      providers: [{ provide: ProjectService, useValue: { getProjectsByOwner: () => of([]) } }, provideRouter([{ path: 'projects/:id', component: TestRouteComponent }])],
    }).compileComponents();
    const fixture = TestBed.createComponent(ProjectsPageComponent);
    const page = fixture.componentInstance;
    page.projects.set([project()]);
    page.loading.set(false);
    fixture.detectChanges();
    const openProject = vi.spyOn(page, 'openProject');

    (fixture.nativeElement.querySelector('.project-foot a') as HTMLAnchorElement).click();
    await fixture.whenStable();

    expect(openProject).not.toHaveBeenCalled();
  });

  it('opens sharing from the menu and keeps unsupported actions disabled', async () => {
    const projectService = {
      getProjectsByOwner: () => of([]),
      getMyRole: () => of({ role: 'OWNER' }),
      getMembers: () => of([]),
    };
    await TestBed.configureTestingModule({
      imports: [ProjectsPageComponent],
      providers: [{ provide: ProjectService, useValue: projectService }, provideRouter([])],
    }).compileComponents();
    const fixture = TestBed.createComponent(ProjectsPageComponent);
    const page = fixture.componentInstance;
    page.projects.set([project()]);
    page.loading.set(false);
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('.project-menu-trigger') as HTMLButtonElement).click();
    fixture.detectChanges();
    const menuButtons = Array.from(fixture.nativeElement.querySelectorAll('.project-menu button')) as HTMLButtonElement[];
    expect(menuButtons.filter((button) => button.disabled)).toHaveLength(1);
    (Array.from(fixture.nativeElement.querySelectorAll('.project-menu button')) as HTMLButtonElement[])
      .find((button) => button.textContent?.includes('Compartir'))?.click();
    fixture.detectChanges();

    expect(page.shareOpen()).toBe(true);
    expect(page.shareProjectId()).toBe('p1');
  });

  it('edits a project through the modal and updates the card locally', async () => {
    const current = project();
    const updated = { ...current, name: 'Nuevo nombre', description: 'Nueva descripción' };
    const projectService = { getProjectsByOwner: () => of([]), updateProject: () => of(updated) };
    await TestBed.configureTestingModule({ imports: [ProjectsPageComponent], providers: [{ provide: ProjectService, useValue: projectService }, provideRouter([])] }).compileComponents();
    const fixture = TestBed.createComponent(ProjectsPageComponent);
    const page = fixture.componentInstance;
    page.projects.set([current]); page.loading.set(false); fixture.detectChanges();
    (fixture.nativeElement.querySelector('.project-menu-trigger') as HTMLButtonElement).click(); fixture.detectChanges();
    (Array.from(fixture.nativeElement.querySelectorAll('.project-menu button')) as HTMLButtonElement[]).find((button) => button.textContent?.includes('Editar proyecto'))?.click();
    expect(page.editingProject()?.name).toBe('Proyecto demo');
    page.editName.set('Nuevo nombre'); page.editDescription.set('Nueva descripción'); page.saveEdit();

    expect(page.projects()[0].name).toBe('Nuevo nombre');
    expect(page.editingProject()).toBeNull();
  });

  it('confirms deletion and removes the card locally', async () => {
    const current = project();
    const deleteProject = vi.fn(() => of(void 0));
    const projectService = { getProjectsByOwner: () => of([]), deleteProject };
    await TestBed.configureTestingModule({ imports: [ProjectsPageComponent], providers: [{ provide: ProjectService, useValue: projectService }, provideRouter([])] }).compileComponents();
    const fixture = TestBed.createComponent(ProjectsPageComponent);
    const page = fixture.componentInstance;
    page.projects.set([current]); page.loading.set(false); fixture.detectChanges();
    (fixture.nativeElement.querySelector('.project-menu-trigger') as HTMLButtonElement).click(); fixture.detectChanges();
    (Array.from(fixture.nativeElement.querySelectorAll('.project-menu button')) as HTMLButtonElement[]).find((button) => button.textContent?.includes('Eliminar proyecto'))?.click();
    expect(page.deletingProject()?.id).toBe('p1');
    page.confirmDelete();

    expect(deleteProject).toHaveBeenCalledWith('p1');
    expect(page.projects()).toEqual([]);
  });

  function project() {
    return { id: 'p1', name: 'Proyecto demo', description: 'Descripción', ownerUserId: 'u1', createdAt: '', updatedAt: '' };
  }
});
