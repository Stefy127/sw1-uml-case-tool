import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-editor-page',
  imports: [RouterLink],
  templateUrl: './editor-page.component.html',
  styleUrl: './editor-page.component.scss',
})
export class EditorPageComponent {
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
}
