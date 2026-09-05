import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-shell',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss',
})
export class AppShellComponent {
  collapsed = false;
  nav = [
    { label: 'Dashboard', icon: '▦', link: '/dashboard' },
    { label: 'Mis proyectos', icon: '▱', link: '/projects' },
    { label: 'Compartidos conmigo', icon: '♧', link: '/shared' },
    { label: 'Plantillas', icon: '◇', link: '/templates' },
  ];
}
