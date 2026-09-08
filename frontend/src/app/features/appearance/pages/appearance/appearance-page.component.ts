import { Component, inject } from '@angular/core';
import { ThemeService } from '../../../../core/theme/theme.service';
import { AuthService } from '../../../auth/services/auth.service';

@Component({
  selector: 'app-appearance-page',
  templateUrl: './appearance-page.component.html',
  styleUrl: './appearance-page.component.scss',
})
export class AppearancePageComponent {
  readonly theme = inject(ThemeService);
  readonly auth = inject(AuthService);
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
  selectTheme(preset: (typeof this.theme.presets)[number]): void {
    this.theme.select(preset);
    if (this.auth.currentUser()) this.auth.updateTheme(preset.name.toUpperCase()).subscribe();
  }
}
