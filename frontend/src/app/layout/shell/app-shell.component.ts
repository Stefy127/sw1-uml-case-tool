import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../features/auth/services/auth.service';

@Component({
  selector: 'app-shell',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss',
})
export class AppShellComponent {
  readonly auth = inject(AuthService);
  collapsed = false;
  userMenuOpen = false;
  get initials(): string { const user = this.auth.currentUser(); return user ? `${user.firstName[0] ?? ''}${user.lastName[0] ?? ''}`.toUpperCase() : ''; }
  toggleUserMenu(): void { this.userMenuOpen = !this.userMenuOpen; }
  nav = [
    { label: 'Dashboard', icon: '▦', link: '/dashboard' },
    { label: 'Mis proyectos', icon: '▱', link: '/projects' },
    { label: 'Compartidos conmigo', icon: '♧', link: '/shared' },
    { label: 'Plantillas', icon: '◇', link: '/templates' },
  ];
}
