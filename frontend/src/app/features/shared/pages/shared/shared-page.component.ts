import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ProjectService } from '../../../projects/services/project.service';
import { Project, ProjectMemberRole } from '../../../projects/models/project.model';

@Component({
  selector: 'app-shared-page',
  imports: [RouterLink],
  templateUrl: './shared-page.component.html',
  styleUrl: './shared-page.component.scss',
})
export class SharedPageComponent {
  private readonly service = inject(ProjectService);
  readonly projects = signal<Project[]>([]);
  readonly roles = signal<Record<string, ProjectMemberRole>>({});
  readonly loading = signal(true);
  readonly error = signal('');

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.service.getSharedProjects().subscribe({
      next: (projects) => {
        this.projects.set(projects);
        if (!projects.length) {
          this.loading.set(false);
          return;
        }
        forkJoin(projects.map((project) => this.service.getMyRole(project.id).pipe(catchError(() => of(null))))).subscribe({
          next: (results) => {
            const roles: Record<string, ProjectMemberRole> = {};
            results.forEach((result, index) => {
              if (result) roles[projects[index].id] = result.role;
            });
            this.roles.set(roles);
            this.loading.set(false);
          },
          error: () => this.loading.set(false),
        });
      },
      error: () => {
        this.error.set('No pudimos cargar tus proyectos compartidos.');
        this.loading.set(false);
      },
    });
  }

  roleLabel(projectId: string): string {
    return this.roles()[projectId] === 'VIEWER' ? 'Solo lectura' : 'Editor';
  }

  roleClass(projectId: string): string {
    return this.roles()[projectId] === 'VIEWER' ? 'viewer' : 'editor';
  }

  formatDate(value: string): string {
    if (!value) return 'Actualizado recientemente';
    return `Actualizado ${new Intl.DateTimeFormat('es-BO', { day: 'numeric', month: 'short', year: 'numeric' }).format(new Date(value))}`;
  }
}
