import { Component, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ProjectService } from '../../../projects/services/project.service';

@Component({ selector: 'app-shared-link-page', standalone: true, template: '<p class="muted">Abriendo proyecto compartido...</p>' })
export class SharedLinkPageComponent {
  private readonly route = inject(ActivatedRoute); private readonly router = inject(Router); private readonly projects = inject(ProjectService);
  constructor() { const token = this.route.snapshot.paramMap.get('token') ?? ''; this.projects.resolveSharedProject(token).subscribe({ next: ({ project }) => void this.router.navigate(['/projects', project.id]), error: () => void this.router.navigate(['/shared']) }); }
}
