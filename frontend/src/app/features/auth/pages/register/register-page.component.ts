import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink, Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({ selector: 'app-register-page', imports: [FormsModule, RouterLink], templateUrl: './register-page.component.html', styleUrl: '../login/login-page.component.scss' })
export class RegisterPageComponent {
  private readonly auth = inject(AuthService); private readonly router = inject(Router);
  firstName=''; lastName=''; email=''; password=''; confirmPassword=''; readonly loading=signal(false); readonly error=signal('');
  submit(): void {
    this.error.set('');
    if (!this.firstName.trim() || !this.lastName.trim() || !this.email.trim() || this.password.length < 8) { this.error.set('Completa todos los campos y usa una contraseña de al menos 8 caracteres.'); return; }
    if (this.password !== this.confirmPassword) { this.error.set('Las contraseñas no coinciden.'); return; }
    this.loading.set(true); this.auth.register({ firstName:this.firstName, lastName:this.lastName, email:this.email, password:this.password }).subscribe({ next:()=>{ this.loading.set(false); void this.router.navigate(['/dashboard']); }, error:(e)=>{ this.loading.set(false); this.error.set(e?.error?.message || 'No se pudo crear la cuenta.'); } });
  }
}
