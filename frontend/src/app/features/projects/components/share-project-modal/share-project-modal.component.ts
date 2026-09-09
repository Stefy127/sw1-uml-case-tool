import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, inject, signal } from '@angular/core';
import { ProjectMember, ProjectMemberRole, ProjectShareMode, ShareLinkResponse } from '../../models/project.model';
import { ProjectService } from '../../services/project.service';

@Component({
  selector: 'app-share-project-modal',
  imports: [],
  templateUrl: './share-project-modal.component.html',
  styleUrl: './share-project-modal.component.scss',
})
export class ShareProjectModalComponent implements OnChanges {
  private readonly projectService = inject(ProjectService);
  @Input() projectId = '';
  @Input() currentUserRole: ProjectMemberRole | null = null;
  @Input() open = false;
  @Output() close = new EventEmitter<void>();
  @Output() membersChanged = new EventEmitter<ProjectMember[]>();
  readonly members = signal<ProjectMember[]>([]);
  readonly memberEmail = signal('');
  readonly memberRole = signal<ProjectMemberRole>('EDITOR');
  readonly loading = signal(false);
  readonly error = signal('');
  readonly message = signal('');
  readonly shareMode = signal<ProjectShareMode>('RESTRICTED');
  readonly shareUrl = signal<string | null>(null);
  readonly linkLoading = signal(false);

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['open']?.currentValue || changes['projectId']) && this.open && this.projectId) this.loadMembers();
  }

  loadMembers(): void {
    this.error.set('');
    this.projectService.getMembers(this.projectId).subscribe({
      next: (members) => { this.members.set(members); this.membersChanged.emit(members); },
      error: () => this.error.set('No se pudieron cargar los colaboradores.'),
    });
    if (this.currentUserRole === 'OWNER' && typeof this.projectService.getShareLink === 'function') this.projectService.getShareLink(this.projectId).subscribe({ next: (link) => this.applyLink(link) });
  }

  changeShareMode(mode: ProjectShareMode): void { if (this.currentUserRole !== 'OWNER') return; this.linkLoading.set(true); this.projectService.setShareLink(this.projectId, mode).subscribe({ next: (link) => { this.applyLink(link); this.message.set(mode === 'RESTRICTED' ? 'Enlace desactivado.' : 'Acceso mediante enlace actualizado.'); this.linkLoading.set(false); }, error: () => { this.error.set('No se pudo actualizar el acceso mediante enlace.'); this.linkLoading.set(false); } }); }
  regenerateLink(): void { if (this.currentUserRole !== 'OWNER') return; this.linkLoading.set(true); this.projectService.regenerateShareLink(this.projectId).subscribe({ next: (link) => { this.applyLink(link); this.message.set('Enlace regenerado.'); this.linkLoading.set(false); }, error: () => { this.error.set('No se pudo regenerar el enlace.'); this.linkLoading.set(false); } }); }
  copyLink(): void { const url = this.shareUrl(); if (!url) return; void navigator.clipboard.writeText(url).then(() => this.message.set('Enlace copiado.')); }
  private applyLink(link: ShareLinkResponse): void { this.shareMode.set(link.mode); this.shareUrl.set(link.url); }

  addMember(): void {
    if (this.currentUserRole !== 'OWNER') return;
    const email = this.memberEmail().trim();
    if (!email) { this.error.set('Escribe un correo electrónico.'); return; }
    this.loading.set(true); this.error.set('');
    this.projectService.addMember(this.projectId, email, this.memberRole()).subscribe({
      next: (member) => { this.updateMembers([...this.members().filter((item) => item.id !== member.id), member]); this.memberEmail.set(''); this.message.set('Colaborador agregado correctamente.'); this.loading.set(false); },
      error: (error: { error?: { message?: string } }) => { this.error.set(error.error?.message ?? 'No se pudo agregar el colaborador.'); this.loading.set(false); },
    });
  }

  changeRole(member: ProjectMember, role: ProjectMemberRole): void {
    if (this.currentUserRole !== 'OWNER' || member.role === 'OWNER' || role === 'OWNER') return;
    this.loading.set(true);
    this.projectService.changeMemberRole(this.projectId, member.id, role).subscribe({
      next: (updated) => { this.updateMembers(this.members().map((item) => item.id === updated.id ? updated : item)); this.message.set('Rol actualizado.'); this.loading.set(false); },
      error: () => { this.error.set('No se pudo actualizar el rol.'); this.loading.set(false); },
    });
  }

  removeMember(member: ProjectMember): void {
    if (this.currentUserRole !== 'OWNER') return;
    if (member.role === 'OWNER' && this.members().filter((item) => item.role === 'OWNER').length <= 1) { this.error.set('El proyecto debe conservar al menos un propietario.'); return; }
    this.loading.set(true);
    this.projectService.removeMember(this.projectId, member.id).subscribe({
      next: () => { this.updateMembers(this.members().filter((item) => item.id !== member.id)); this.message.set('Colaborador eliminado.'); this.loading.set(false); },
      error: () => { this.error.set('No se pudo eliminar el colaborador.'); this.loading.set(false); },
    });
  }

  initials(member: ProjectMember): string { return `${member.firstName?.[0] ?? ''}${member.lastName?.[0] ?? ''}`.toUpperCase() || member.email[0].toUpperCase(); }

  private updateMembers(members: ProjectMember[]): void { this.members.set(members); this.membersChanged.emit(members); }
}
