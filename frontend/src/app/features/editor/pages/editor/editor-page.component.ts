import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { DiagramDetail } from '../../models/diagram.model';
import { DiagramService } from '../../services/diagram.service';

@Component({
  selector: 'app-editor-page',
  imports: [RouterLink],
  templateUrl: './editor-page.component.html',
  styleUrl: './editor-page.component.scss',
})
export class EditorPageComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly diagramService = inject(DiagramService);
  readonly diagram = signal<DiagramDetail | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  tab = 'Propiedades';
  tools = ['⌁', '□', '⌁', '◇', '◈', '↗'];
  classes = [
    {
      name: 'Usuario',
      x: '8%',
      y: '19%',
      attrs: ['id: UUID', 'nombre: String', 'email: String'],
      methods: ['prestar()', 'devolverLibro()'],
    },
    {
      name: 'Libro',
      x: '48%',
      y: '10%',
      attrs: ['isbn: String', 'título: String', 'disponible: Bool'],
      methods: ['reservar()'],
    },
    {
      name: 'Autor',
      x: '76%',
      y: '29%',
      attrs: ['nombre: String', 'nacionalidad: String'],
      methods: ['getLibros()'],
    },
    {
      name: 'Préstamo',
      x: '48%',
      y: '57%',
      attrs: ['fechaInicio: Date', 'fechaFin: Date'],
      methods: ['estáVencido()'],
    },
  ];

  constructor() {
    const diagramId = this.route.snapshot.paramMap.get('diagramId');
    if (!diagramId) {
      this.error.set('No se pudo identificar el diagrama.');
      this.loading.set(false);
      return;
    }
    this.diagramService.getDiagramById(diagramId).subscribe({
      next: (diagram) => {
        this.diagram.set(diagram);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('No se pudo cargar el diagrama.');
        this.loading.set(false);
      },
    });
  }
}
