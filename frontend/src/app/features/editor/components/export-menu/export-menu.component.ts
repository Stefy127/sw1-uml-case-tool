import { Component, Input, inject, signal } from '@angular/core';
import { DiagramService } from '../../services/diagram.service';

@Component({
  selector: 'app-export-menu',
  standalone: true,
  templateUrl: './export-menu.component.html',
  styleUrl: './export-menu.component.scss',
})
export class ExportMenuComponent {
  @Input() diagramId = '';
  readonly open = signal(false);
  readonly loading = signal(false);
  readonly error = signal('');
  private readonly diagrams = inject(DiagramService);

  toggle(): void { if (!this.loading()) this.open.update((value) => !value); }
  exportXmi(): void {
    if (!this.diagramId || this.loading()) return;
    this.loading.set(true); this.error.set('');
    this.diagrams.exportXmi(this.diagramId).subscribe({
      next: (response) => {
        if (!response.body) { this.error.set('La exportación no devolvió ningún archivo.'); this.loading.set(false); return; }
        const filename = response.headers.get('content-disposition')?.match(/filename="?([^";]+)"?/i)?.[1] ?? 'diagrama.xmi';
        const url = URL.createObjectURL(response.body);
        const anchor = document.createElement('a'); anchor.href = url; anchor.download = filename; anchor.click(); URL.revokeObjectURL(url);
        this.loading.set(false); this.open.set(false);
      },
      error: () => { this.error.set('No se pudo exportar el diagrama XMI.'); this.loading.set(false); },
    });
  }
}
