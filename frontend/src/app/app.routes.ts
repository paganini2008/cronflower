import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { cronflowGuard } from './core/cronflow-feature';

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
      // Executors, Cluster and Health are unified under one "System" section (a tabbed page). The old
      // top-level paths redirect in, so existing links keep working.
      { path: 'executors', redirectTo: 'system/executors', pathMatch: 'full' },
      { path: 'cluster', redirectTo: 'system/cluster', pathMatch: 'full' },
      { path: 'health', redirectTo: 'system/health', pathMatch: 'full' },
      {
        path: 'system',
        title: 'System · cronflower',
        loadComponent: () => import('./pages/system/system').then((m) => m.SystemPage),
        children: [
          { path: '', redirectTo: 'executors', pathMatch: 'full' },
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
      // Workflows + Runs are unified under one "DAG" section (a tabbed page). Old paths redirect in.
      { path: 'workflows', redirectTo: 'dag/workflows', pathMatch: 'full' },
      { path: 'workflows/new', redirectTo: 'dag/new', pathMatch: 'full' },
      { path: 'dag-runs', redirectTo: 'dag/runs', pathMatch: 'full' },
      { path: 'dag-runs/:runId', redirectTo: 'dag/runs/:runId', pathMatch: 'full' },
      {
        // Full-screen canvas editor, outside the DAG tab shell. Declared before the 'dag' parent.
        path: 'dag/new',
        title: 'New workflow · cronflower',
        canActivate: [cronflowGuard],
        loadComponent: () => import('./pages/dag-new/dag-new').then((m) => m.DagNewPage),
      },
      {
        path: 'dag',
        title: 'DAG · cronflower',
        canActivate: [cronflowGuard],
        loadComponent: () => import('./pages/dag/dag').then((m) => m.DagPage),
        children: [
          { path: '', redirectTo: 'workflows', pathMatch: 'full' },
          {
            path: 'workflows',
            title: 'Workflows · cronflower',
            loadComponent: () => import('./pages/dags/dags').then((m) => m.Dags),
          },
          {
            path: 'runs',
            title: 'DAG Runs · cronflower',
            loadComponent: () => import('./pages/dag-runs/dag-runs').then((m) => m.DagRuns),
          },
          {
            path: 'runs/:runId',
            title: 'DAG Run · cronflower',
            loadComponent: () =>
              import('./pages/dag-run-detail/dag-run-detail').then((m) => m.DagRunDetailPage),
          },
        ],
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
