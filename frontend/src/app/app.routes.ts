import { Routes } from '@angular/router';
import { AppShellComponent } from './layout/shell/app-shell.component';
import { AppearancePageComponent } from './features/appearance/pages/appearance/appearance-page.component';
import { EditorPageComponent } from './features/editor/pages/editor/editor-page.component';
import { LoginPageComponent } from './features/auth/pages/login/login-page.component';
import { ProjectDetailPageComponent } from './features/projects/pages/project-detail/project-detail-page.component';
import { ProjectsPageComponent } from './features/projects/pages/projects/projects-page.component';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'projects' },
  { path: 'login', component: LoginPageComponent },
  {
    path: '',
    component: AppShellComponent,
    children: [
      { path: 'projects', component: ProjectsPageComponent },
      { path: 'projects/:id', component: ProjectDetailPageComponent },
      { path: 'editor/:diagramId', component: EditorPageComponent },
      { path: 'settings/appearance', component: AppearancePageComponent },
    ],
  },
  { path: '**', redirectTo: 'projects' },
];
