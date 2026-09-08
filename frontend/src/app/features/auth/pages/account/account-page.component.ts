import { Component, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({ selector: 'app-account-page', imports: [FormsModule, RouterLink], templateUrl: './account-page.component.html', styleUrl: './account-page.component.scss' })
export class AccountPageComponent {
  readonly auth = inject(AuthService); readonly saving = signal(false); readonly passwordSaving = signal(false); readonly message = signal(''); readonly error = signal(''); readonly passwordError = signal(''); readonly photo = signal<string | null>(null);
  firstName = ''; lastName = ''; email = ''; currentPassword = ''; newPassword = ''; confirmPassword = '';
  constructor() {
    effect(() => {
      const user = this.auth.currentUser();
      if (user) {
        this.firstName = user.firstName;
        this.lastName = user.lastName;
        this.email = user.email;
      }
    });
  }
  saveProfile(): void { this.error.set(''); this.message.set(''); if (!this.firstName.trim() || !this.lastName.trim() || !this.email.trim()) { this.error.set('Completa nombre, apellido y correo.'); return; } this.saving.set(true); this.auth.updateProfile({firstName:this.firstName,lastName:this.lastName,email:this.email}).subscribe({next:()=>{this.saving.set(false);this.message.set('Cambios guardados.');},error:e=>{this.saving.set(false);this.error.set(e?.error?.message || 'No se pudieron guardar los cambios.');}}); }
  changePassword(): void { this.passwordError.set(''); if (this.newPassword.length < 8) { this.passwordError.set('La nueva contraseña debe tener al menos 8 caracteres.'); return; } if (this.newPassword !== this.confirmPassword) { this.passwordError.set('Las contraseñas no coinciden.'); return; } this.passwordSaving.set(true); this.auth.changePassword(this.currentPassword,this.newPassword).subscribe({next:()=>{this.passwordSaving.set(false);this.currentPassword='';this.newPassword='';this.confirmPassword='';this.message.set('Contraseña actualizada.');},error:e=>{this.passwordSaving.set(false);this.passwordError.set(e?.error?.message || 'No se pudo cambiar la contraseña.');}}); }
  selectPhoto(event: Event): void { const input = event.target as HTMLInputElement; const file=input.files?.[0]; if (!file) return; if (!['image/png','image/jpeg','image/webp'].includes(file.type) || file.size > 2*1024*1024) { this.error.set('Selecciona una imagen PNG, JPG o WEBP de máximo 2 MB.'); return; } const reader=new FileReader(); reader.onload=()=>this.photo.set(String(reader.result)); reader.readAsDataURL(file); }
  initials(): string { const u=this.auth.currentUser(); return u ? `${u.firstName[0]??''}${u.lastName[0]??''}`.toUpperCase() : ''; }
}
