import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ProjectService } from '../../../projects/services/project.service';
import { Project } from '../../../projects/models/project.model';

@Component({
  selector: 'app-shared-page',
  imports: [RouterLink],
  template: `<div class="page"><span class="eyebrow">Workspace</span><h1>Compartidos conmigo</h1>@if (loading()) { <p class="muted">Cargando proyectos...</p> } @else if (error()) { <p class="error-message">{{ error() }}</p> } @else if (!projects().length) { <p class="muted">No tienes proyectos compartidos.</p> } @else { @for (project of projects(); track project.id) { <article class="card"><h2>{{ project.name }}</h2><p class="muted">{{ project.description || 'Proyecto UML' }}</p><a [routerLink]="['/projects', project.id]">Abrir proyecto</a></article> } }</div>`,
})
export class SharedPageComponent {
  private readonly service = inject(ProjectService); readonly projects=signal<Project[]>([]); readonly loading=signal(true); readonly error=signal('');
  constructor() { this.service.getSharedProjects().subscribe({next:p=>{this.projects.set(p);this.loading.set(false);},error:()=>{this.error.set('No se pudieron cargar los proyectos compartidos.');this.loading.set(false);}}); }
}
