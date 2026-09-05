import { Component } from '@angular/core';

@Component({
  selector: 'app-shared-page',
  template: `
    <div class="page">
      <span class="eyebrow">Workspace</span>
      <h1>Compartidos conmigo</h1>
      <p class="muted">Aquí aparecerán los proyectos compartidos contigo.</p>
    </div>
  `,
})
export class SharedPageComponent {}
