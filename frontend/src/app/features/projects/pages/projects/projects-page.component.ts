import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../../auth/services/auth.service';
import { Project, ProjectMemberRole } from '../../models/project.model';
import { ProjectService } from '../../services/project.service';
import { ShareProjectModalComponent } from '../../components/share-project-modal/share-project-modal.component';

@Component({
  selector: 'app-projects-page',
  imports: [RouterLink, ShareProjectModalComponent],
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
  readonly openMenuId = signal<string | null>(null);
  readonly openMenuUp = signal(false);
  readonly shareOpen = signal(false);
  readonly shareProjectId = signal('');
  readonly shareRole = signal<ProjectMemberRole | null>(null);
  readonly editingProject = signal<Project | null>(null);
  readonly editName = signal('');
  readonly editDescription = signal('');
  readonly editLoading = signal(false);
  readonly editError = signal('');
  readonly deletingProject = signal<Project | null>(null);
  readonly deleteLoading = signal(false);
  readonly deleteError = signal('');
  readonly duplicatingProjectId = signal<string | null>(null);
  readonly duplicateError = signal('');
  private readonly router = inject(Router);
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

  todayLabel(): string {
    return new Intl.DateTimeFormat('es-ES', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })
      .format(new Date()).toUpperCase();
  }

  openProject(projectId: string): void {
    this.openMenuId.set(null);
    this.openMenuUp.set(false);
    void this.router.navigate(['/projects', projectId]);
  }

  onCardKeydown(event: KeyboardEvent, projectId: string): void {
    if (event.key !== 'Enter' && event.key !== ' ') return;
    event.preventDefault();
    this.openProject(projectId);
  }

  toggleMenu(event: Event, projectId: string): void {
    event.stopPropagation();
    this.duplicateError.set('');
    if (this.openMenuId() === projectId) {
      this.openMenuId.set(null);
      this.openMenuUp.set(false);
      return;
    }
    const trigger = event.currentTarget as HTMLElement | null;
    this.openMenuUp.set(!!trigger && window.innerHeight - trigger.getBoundingClientRect().bottom < 250);
    this.openMenuId.set(projectId);
  }

  openShare(event: Event, projectId: string): void {
    event.stopPropagation();
    this.openMenuId.set(null);
    this.openMenuUp.set(false);
    this.projectService.getMyRole(projectId).subscribe({
      next: ({ role }) => { this.shareProjectId.set(projectId); this.shareRole.set(role); this.shareOpen.set(true); },
      error: () => { this.shareProjectId.set(projectId); this.shareRole.set(null); this.shareOpen.set(true); },
    });
  }

  openEdit(event: Event, project: Project): void {
    event.stopPropagation();
    this.openMenuId.set(null);
    this.openMenuUp.set(false);
    this.editingProject.set(project);
    this.editName.set(project.name);
    this.editDescription.set(project.description ?? '');
    this.editError.set('');
  }

  closeEdit(): void {
    if (!this.editLoading()) this.editingProject.set(null);
  }

  saveEdit(): void {
    const project = this.editingProject();
    const name = this.editName().trim();
    const description = this.editDescription();
    if (!project || !name || name.length > 150 || (name === project.name && description === (project.description ?? ''))) return;
    this.editLoading.set(true);
    this.editError.set('');
    this.projectService.updateProject(project.id, { name, description }).subscribe({
      next: (updated) => {
        this.projects.update((items) => items.map((item) => item.id === updated.id ? updated : item));
        this.editLoading.set(false);
        this.editingProject.set(null);
      },
      error: () => { this.editLoading.set(false); this.editError.set('No se pudo actualizar el proyecto.'); },
    });
  }

  openDelete(event: Event, project: Project): void {
    event.stopPropagation();
    this.openMenuId.set(null);
    this.openMenuUp.set(false);
    this.deletingProject.set(project);
    this.deleteError.set('');
  }

  duplicateProject(event: Event, project: Project): void {
    event.stopPropagation();
    if (this.duplicatingProjectId()) return;
    this.duplicateError.set('');
    this.openMenuId.set(null);
    this.openMenuUp.set(false);
    this.duplicatingProjectId.set(project.id);
    this.projectService.duplicateProject(project.id).subscribe({
      next: (duplicated) => {
        this.projects.update((items) => [duplicated, ...items]);
        this.duplicatingProjectId.set(null);
      },
      error: () => {
        this.duplicatingProjectId.set(null);
        this.duplicateError.set('No se pudo duplicar el proyecto.');
      },
    });
  }

  closeDelete(): void {
    if (!this.deleteLoading()) this.deletingProject.set(null);
  }

  confirmDelete(): void {
    const project = this.deletingProject();
    if (!project || this.deleteLoading()) return;
    this.deleteLoading.set(true);
    this.deleteError.set('');
    this.projectService.deleteProject(project.id).subscribe({
      next: () => {
        this.projects.update((items) => items.filter((item) => item.id !== project.id));
        this.deleteLoading.set(false);
        this.deletingProject.set(null);
      },
      error: () => { this.deleteLoading.set(false); this.deleteError.set('No se pudo eliminar el proyecto.'); },
    });
  }

  @HostListener('document:click')
  closeOpenMenu(): void { this.openMenuId.set(null); this.openMenuUp.set(false); }

  @HostListener('document:keydown.escape')
  closeMenuWithEscape(): void { this.openMenuId.set(null); this.openMenuUp.set(false); }
}
