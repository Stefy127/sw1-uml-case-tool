import { Component, computed, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-projects-page',
  imports: [RouterLink],
  templateUrl: './projects-page.component.html',
  styleUrl: './projects-page.component.scss',
})
export class ProjectsPageComponent {
  readonly query = signal('');
  readonly projects = [
    {
      id: 'library',
      name: 'Sistema de Biblioteca',
      color: 'lavender',
      classes: 12,
      diagrams: 3,
      edited: 'Hoy, 10:32',
      avatars: ['MG', 'JR'],
    },
    {
      id: 'commerce',
      name: 'E-commerce Platform',
      color: 'sky',
      classes: 28,
      diagrams: 5,
      edited: 'Ayer, 16:48',
      avatars: ['MG'],
    },
    {
      id: 'booking',
      name: 'App de Reservas',
      color: 'mint',
      classes: 8,
      diagrams: 2,
      edited: '12 Jun, 09:15',
      avatars: ['MG'],
    },
    {
      id: 'university',
      name: 'Proyecto Universidad',
      color: 'peach',
      classes: 16,
      diagrams: 4,
      edited: '10 Jun, 14:20',
      avatars: ['JR', 'AS'],
    },
  ];
  readonly filtered = computed(() =>
    this.projects.filter((p) => p.name.toLowerCase().includes(this.query().toLowerCase())),
  );
}
