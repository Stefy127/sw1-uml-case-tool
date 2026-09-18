import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ProjectService } from '../../../projects/services/project.service';
import { AuthService } from '../../../auth/services/auth.service';
import { DiagramService } from '../../../editor/services/diagram.service';
import { TemplateDefinition } from '../../models/template.model';

@Component({
  selector: 'app-templates-page',
  templateUrl: './templates-page.component.html',
  styleUrl: './templates-page.component.scss',
})
export class TemplatesPageComponent {
  private readonly projects = inject(ProjectService);
  private readonly diagrams = inject(DiagramService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly creating = signal('');
  readonly error = signal('');
  readonly templates: TemplateDefinition[] = [
    { name: 'Biblioteca', description: 'Catálogo, préstamos y lectores para una biblioteca.', icon: '▤', color: 'lavender', classes: ['Libro', 'Usuario', 'Préstamo'], relations: 3 },
    { name: 'Tienda / E-commerce', description: 'Productos, pedidos y clientes para una tienda digital.', icon: '◇', color: 'peach', classes: ['Producto', 'Cliente', 'Pedido'], relations: 4 },
    { name: 'Clínica', description: 'Pacientes, citas y profesionales de la salud.', icon: '✚', color: 'mint', classes: ['Paciente', 'Cita', 'Médico'], relations: 3 },
    { name: 'Universidad', description: 'Alumnos, cursos y matrículas para un campus.', icon: '⌂', color: 'sky', classes: ['Alumno', 'Curso', 'Matrícula'], relations: 4 },
  ];

  useTemplate(template: TemplateDefinition): void {
    const ownerUserId = this.auth.currentUser()?.id;
    if (!ownerUserId) { this.error.set('Inicia sesión para usar una plantilla.'); return; }
    this.creating.set(template.name);
    this.error.set('');
    this.projects.createProject({ name: template.name, description: template.description, ownerUserId }).subscribe({
      next: project => this.diagrams.createDiagram(project.id, `${template.name} · Modelo UML`).subscribe({
        next: diagram => { this.creating.set(''); void this.router.navigate(['/editor', diagram.id]); },
        error: () => { this.creating.set(''); this.error.set('No se pudo crear el diagrama.'); },
      }),
      error: () => { this.creating.set(''); this.error.set('No se pudo crear el proyecto.'); },
    });
  }
}
