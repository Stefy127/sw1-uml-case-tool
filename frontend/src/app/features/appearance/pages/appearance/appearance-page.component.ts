import { Component, inject } from '@angular/core';
import { ThemeService } from '../../../../core/theme/theme.service';

@Component({
  selector: 'app-appearance-page',
  templateUrl: './appearance-page.component.html',
  styleUrl: './appearance-page.component.scss',
})
export class AppearancePageComponent {
  readonly theme = inject(ThemeService);
  readonly keys = [
    'primary',
    'secondary',
    'background',
    'surface',
    'canvas',
    'classHeader',
    'border',
  ];
  readonly labels: Record<string, string> = {
    primary: 'Principal',
    secondary: 'Secundario',
    background: 'Fondo',
    surface: 'Superficie',
    canvas: 'Canvas',
    classHeader: 'Encabezado de clase UML',
    border: 'Borde',
  };
}
