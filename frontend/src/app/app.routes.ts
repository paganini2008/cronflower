import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    title: 'Sign in · cronflower',
    loadComponent: () => import('./pages/login/login').then((m) => m.Login),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'dashboard',
        title: 'Dashboard · cronflower',
        loadComponent: () => import('./pages/dashboard/dashboard').then((m) => m.Dashboard),
      },
      {
        path: 'tasks',
        title: 'Tasks · cronflower',
        loadComponent: () => import('./pages/task-list/task-list').then((m) => m.TaskList),
      },
      {
        path: 'tasks/new',
        title: 'New Task · cronflower',
        loadComponent: () => import('./pages/task-form/task-form').then((m) => m.TaskForm),
      },
      {
        path: 'tasks/:group/:name/edit',
        title: 'Edit Task · cronflower',
        loadComponent: () => import('./pages/task-form/task-form').then((m) => m.TaskForm),
      },
      {
        path: 'tasks/:group/:name',
        title: 'Task · cronflower',
        loadComponent: () => import('./pages/task-detail/task-detail').then((m) => m.TaskDetail),
      },
      {
        path: 'executors',
        title: 'Executors · cronflower',
        loadComponent: () => import('./pages/executors/executors').then((m) => m.Executors),
      },
      {
        path: 'cluster',
        title: 'Cluster · cronflower',
        loadComponent: () => import('./pages/cluster/cluster').then((m) => m.Cluster),
      },
      {
        path: 'health',
        title: 'System Health · cronflower',
        loadComponent: () => import('./pages/health/health').then((m) => m.Health),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
