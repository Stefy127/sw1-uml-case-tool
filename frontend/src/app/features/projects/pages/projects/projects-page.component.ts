import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../../auth/services/auth.service';
import { Project } from '../../models/project.model';
import { ProjectService } from '../../services/project.service';

@Component({
  selector: 'app-projects-page',
  imports: [RouterLink],
  templateUrl: './projects-page.component.html',
  styleUrl: './projects-page.component.scss',
})
export class ProjectsPageComponent {
  private readonly projectService = inject(ProjectService);
  readonly auth = inject(AuthService, { optional: true });
  readonly query = signal('');
  readonly projects = signal<Project[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly showCreate = signal(false);
  readonly creating = signal(false);
  readonly createName = signal('');
  readonly createDescription = signal('');
  readonly createError = signal('');
  readonly filtered = computed(() =>
    this.projects().filter((project) =>
      project.name.toLowerCase().includes(this.query().toLowerCase()),
    ),
  );

  constructor() {
    this.loadProjects();
  }

  loadProjects(): void {
    this.loading.set(true);
    this.error.set('');
    const userId = this.auth?.currentUser()?.id;
    if (!userId) { this.projects.set([]); this.loading.set(false); return; }
    this.projectService.getProjectsByOwner(userId).subscribe({
      next: (projects) => {
        this.projects.set(projects);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('No se pudieron cargar los proyectos.');
        this.loading.set(false);
      },
    });
  }

  openCreate(): void {
    this.createError.set('');
    this.showCreate.set(true);
  }

  closeCreate(): void {
    if (!this.creating()) {
      this.showCreate.set(false);
      this.createName.set('');
      this.createDescription.set('');
      this.createError.set('');
    }
  }

  createProject(): void {
    const name = this.createName().trim();
    if (!name) {
      this.createError.set('El nombre del proyecto es obligatorio.');
      return;
    }
    this.creating.set(true);
    this.createError.set('');
    this.projectService
      .createProject({
        name,
        description: this.createDescription().trim(),
        ownerUserId: this.auth?.currentUser()?.id ?? '',
      })
      .subscribe({
        next: () => {
          this.creating.set(false);
          this.closeCreate();
          this.loadProjects();
        },
        error: () => {
          this.creating.set(false);
          this.createError.set('No se pudo crear el proyecto.');
        },
      });
  }

  formatDate(value: string): string {
    return value ? new Date(value).toLocaleDateString('es-ES') : 'Sin actividad';
  }
}
