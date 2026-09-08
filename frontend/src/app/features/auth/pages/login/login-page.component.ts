import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login-page',
  imports: [RouterLink, FormsModule],
  templateUrl: './login-page.component.html',
  styleUrl: './login-page.component.scss',
})
export class LoginPageComponent {
  private readonly auth = inject(AuthService); private readonly router = inject(Router);
  email = ''; password = ''; readonly loading = signal(false); readonly error = signal('');
  submit(): void { this.error.set(''); if (!this.email.trim() || !this.password) { this.error.set('Introduce tu correo y contraseña.'); return; } this.loading.set(true); this.auth.login(this.email, this.password).subscribe({ next: () => { this.loading.set(false); void this.router.navigate(['/dashboard']); }, error: (e) => { this.loading.set(false); this.error.set(e?.error?.message || 'No pudimos iniciar sesión. Inténtalo nuevamente.'); } }); }
}
