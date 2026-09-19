import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';

/**
 * DAG: workflow definitions and their run history under one menu, split by a tab bar (Workflows /
 * Runs). Each tab is a routed child, so deep links keep working and the old {@code /workflows} and
 * {@code /dag-runs} paths redirect in. The full-screen canvas editor lives at {@code /dag/new},
 * outside the tabs.
 */
@Component({
  selector: 'cf-dag',
  imports: [RouterLink, RouterLinkActive, RouterOutlet, MatIconModule],
  template: `
    <h1 class="page-title">DAG</h1>
    <p class="page-sub">Workflow definitions and their run history. Build, trigger and watch DAGs.</p>

    <nav class="tabs">
      <a routerLink="/dag/workflows" routerLinkActive="on"><mat-icon>account_tree</mat-icon> Workflows</a>
      <a routerLink="/dag/runs" routerLinkActive="on"><mat-icon>history</mat-icon> Runs</a>
    </nav>

    <div class="tab-body">
      <router-outlet />
    </div>
  `,
  styles: [`
    .page-title { margin: 0; }
    .page-sub { margin: 0.15rem 0 1rem; }
    .tabs { display: flex; gap: 0.3rem; border-bottom: 1px solid #e3eaf3; margin-bottom: 1.25rem; }
    .tabs a { display: inline-flex; align-items: center; gap: 0.4rem; text-decoration: none;
      color: #64748b; font-weight: 600; font-size: 0.9rem; padding: 0.6rem 0.9rem; border-bottom: 2px solid transparent;
      margin-bottom: -1px; }
    .tabs a:hover { color: #0f2c4d; }
    .tabs a.on { color: #1565c0; border-bottom-color: #1565c0; }
    .tabs mat-icon { font-size: 20px; width: 20px; height: 20px; }
  `],
})
export class DagPage {}
