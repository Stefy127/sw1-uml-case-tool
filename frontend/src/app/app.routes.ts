import { Routes } from '@angular/router';
import { AppShellComponent } from './layout/shell/app-shell.component';
import { AppearancePageComponent } from './features/appearance/pages/appearance/appearance-page.component';
import { EditorPageComponent } from './features/editor/pages/editor/editor-page.component';
import { LoginPageComponent } from './features/auth/pages/login/login-page.component';
import { RegisterPageComponent } from './features/auth/pages/register/register-page.component';
import { authGuard } from './features/auth/guards/auth.guard';
import { AccountPageComponent } from './features/auth/pages/account/account-page.component';
import { ProjectDetailPageComponent } from './features/projects/pages/project-detail/project-detail-page.component';
import { ProjectsPageComponent } from './features/projects/pages/projects/projects-page.component';
import { SharedPageComponent } from './features/shared/pages/shared/shared-page.component';
import { TemplatesPageComponent } from './features/templates/pages/templates/templates-page.component';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  { path: 'login', component: LoginPageComponent },
  { path: 'register', component: RegisterPageComponent },
  {
    path: '',
    component: AppShellComponent,
    children: [
      { path: 'dashboard', component: ProjectsPageComponent },
      { path: 'projects', component: ProjectsPageComponent, canActivate: [authGuard] },
      { path: 'projects/:id', component: ProjectDetailPageComponent, canActivate: [authGuard] },
      { path: 'editor/:diagramId', component: EditorPageComponent, canActivate: [authGuard] },
      { path: 'settings/appearance', component: AppearancePageComponent },
      { path: 'appearance', component: AppearancePageComponent },
      { path: 'account', component: AccountPageComponent, canActivate: [authGuard] },
      { path: 'shared', component: SharedPageComponent },
      { path: 'templates', component: TemplatesPageComponent },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
