import { provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { ProjectService } from '../../services/project.service';
import { ProjectsPageComponent } from './projects-page.component';

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
});
