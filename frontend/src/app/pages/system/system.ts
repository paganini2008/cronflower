import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';

/**
 * System: one place for the operational views that used to be three separate menu items — the live
 * executors, the scheduler cluster, and the health checks. A tab bar switches between them; each tab
 * is its own routed child, so deep links (and the old {@code /executors|/cluster|/health} paths,
 * which now redirect here) keep working.
 */
@Component({
  selector: 'cf-system',
  imports: [RouterLink, RouterLinkActive, RouterOutlet, MatIconModule],
  template: `
    <h1 class="page-title">System</h1>
    <p class="page-sub">Executors, the scheduler cluster, and health. The operational view of the deployment.</p>

    <nav class="tabs">
      <a routerLink="/system/executors" routerLinkActive="on"><mat-icon>memory</mat-icon> Executors</a>
      <a routerLink="/system/cluster" routerLinkActive="on"><mat-icon>hub</mat-icon> Cluster</a>
      <a routerLink="/system/health" routerLinkActive="on"><mat-icon>monitor_heart</mat-icon> Health</a>
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
    /* The child pages render their own titles; keep them but tighten the top gap under the tabs. */
    .tab-body :first-child ::ng-deep .page-title { font-size: 1.15rem; }
  `],
})
export class SystemPage {}
