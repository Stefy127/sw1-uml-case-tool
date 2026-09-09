import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { DiagramService } from '../../../editor/services/diagram.service';
import { DiagramSummary } from '../../../editor/models/diagram.model';
import { Project, ProjectMember, ProjectMemberRole } from '../../models/project.model';
import { ProjectService } from '../../services/project.service';
import { ShareProjectModalComponent } from '../../components/share-project-modal/share-project-modal.component';

@Component({
  selector: 'app-project-detail',
  imports: [RouterLink, ShareProjectModalComponent],
  templateUrl: './project-detail-page.component.html',
  styleUrl: './project-detail-page.component.scss',
})
export class ProjectDetailPageComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly projectService = inject(ProjectService);
  private readonly diagramService = inject(DiagramService);
  readonly project = signal<Project | null>(null);
  readonly diagrams = signal<DiagramSummary[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly showCreate = signal(false);
  readonly creating = signal(false);
  readonly createName = signal('');
  readonly createError = signal('');
  readonly members = signal<ProjectMember[]>([]);
  readonly currentUserRole = signal<ProjectMemberRole | null>(null);
  readonly shareOpen = signal(false);
  readonly projectId = this.route.snapshot.paramMap.get('id') ?? '';

  constructor() {
    if (!this.projectId) {
      this.error.set('No se pudo identificar el proyecto.');
      this.loading.set(false);
      return;
    }
    this.load();
  }

  load(): void {
    this.loading.set(true);
    forkJoin({
      project: this.projectService.getProjectById(this.projectId),
      diagrams: this.diagramService.getDiagramsByProject(this.projectId),
    }).subscribe({
      next: (data) => {
        this.project.set(data.project);
        this.diagrams.set(data.diagrams);
        const projectApi = this.projectService as ProjectService & {
          getMembers?: (id: string) => ReturnType<ProjectService['getMembers']>;
          getMyRole?: (id: string) => ReturnType<ProjectService['getMyRole']>;
        };
        projectApi.getMembers?.(this.projectId).subscribe({ next: (members) => this.members.set(members) });
        projectApi.getMyRole?.(this.projectId).subscribe({ next: (result) => this.currentUserRole.set(result.role) });
        this.loading.set(false);
      },
      error: () => {
        this.error.set('No se pudieron cargar los datos del proyecto.');
        this.loading.set(false);
      },
    });
  }

  createDiagram(): void {
    if (!this.projectId) {
      this.createError.set('No se pudo identificar el proyecto.');
      return;
    }
    if (this.creating()) {
      return;
    }
    const name = this.createName().trim();
    if (!name) {
      this.createError.set('El nombre del diagrama es obligatorio.');
      return;
    }
    this.creating.set(true);
    this.diagramService.createDiagram(this.projectId, name).subscribe({
      next: (diagram) => {
        this.creating.set(false);
        this.showCreate.set(false);
        this.createName.set('');
        this.createError.set('');
        this.diagrams.update((diagrams) => [...diagrams, diagram]);
      },
      error: (error: unknown) => {
        this.creating.set(false);
        this.createError.set('No se pudo crear el diagrama.');
        console.error(
          'No se pudo crear el diagrama.',
          error instanceof Error ? error.message : 'Error HTTP',
        );
      },
    });
  }

  openShare(): void {
    this.shareOpen.set(true);
  }

  memberInitials(member: ProjectMember): string {
    return `${member.firstName?.[0] ?? ''}${member.lastName?.[0] ?? ''}`.toUpperCase() || member.email[0].toUpperCase();
  }
}
