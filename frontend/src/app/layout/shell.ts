import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, switchMap, timer } from 'rxjs';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AuthService } from '../core/auth.service';
import { CronsmithApi } from '../core/api.service';
import { CronflowFeature } from '../core/cronflow-feature';
import { localZone, localZoneOffset, setTzMode, tzLabel, tzMode } from '../core/util';

interface NavItem {
  path: string;
  label: string;
  icon: string;
  /** Whether the signed-in user may see this item. */
  can: () => boolean;
}

@Component({
  selector: 'cf-shell',
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive,
    MatToolbarModule, MatSidenavModule, MatListModule, MatIconModule, MatButtonModule, MatMenuModule,
    MatTooltipModule,
  ],
  template: `
    <mat-sidenav-container class="app-container" [autosize]="true">
      <mat-sidenav [opened]="opened()" mode="side" class="app-sidenav"
          [class.collapsed]="collapsed()">
        <a routerLink="/dashboard" class="sidenav-brand" aria-label="cronflower home">
          <img src="default_logo.png" class="logo" alt="cronflower" />
        </a>
        <mat-nav-list class="nav-list">
          @for (item of nav(); track item.path) {
            <a mat-list-item [disableRipple]="true" [routerLink]="item.path" routerLinkActive="active-link"
               [matTooltip]="collapsed() ? item.label : ''" matTooltipPosition="right">
              <mat-icon matListItemIcon>{{ item.icon }}</mat-icon>
              <span matListItemTitle class="nav-label">{{ item.label }}</span>
            </a>
          }
        </mat-nav-list>
      </mat-sidenav>

      <mat-sidenav-content class="app-content">
        <mat-toolbar class="app-toolbar">
          <button matIconButton (click)="toggleCollapse()" aria-label="Toggle navigation"
                  [matTooltip]="collapsed() ? 'Expand menu' : 'Collapse menu'">
            <mat-icon>menu</mat-icon>
          </button>
          <span class="flex-1"></span>
          @if (env(); as e) {
            <span class="env-badge" [class.up]="healthUp()" [class.down]="!healthUp()"
              [matTooltip]="'Deployment environment — cluster health ' + (healthUp() ? 'UP' : 'DOWN')">
              {{ e }}
            </span>
          }
          <button matIconButton type="button" (click)="toggleDark()" class="mr-1"
            [matTooltip]="dark() ? 'Switch to light theme' : 'Switch to dark theme'"
            aria-label="Toggle dark theme">
            <mat-icon>{{ dark() ? 'light_mode' : 'dark_mode' }}</mat-icon>
          </button>
          <button type="button" class="tz-badge" [matMenuTriggerFor]="tzMenu"
            matTooltip="Times are UTC by default — click to switch time zone">
            <mat-icon>schedule</mat-icon>{{ tzLabel() }}<mat-icon class="caret">expand_more</mat-icon>
          </button>
          <mat-menu #tzMenu="matMenu">
            <div class="menu-head">Show times in</div>
            <button mat-menu-item (click)="useTz('utc')">
              <mat-icon>{{ mode() === 'utc' ? 'check' : 'schedule' }}</mat-icon> UTC
            </button>
            <button mat-menu-item (click)="useTz('local')">
              <mat-icon>{{ mode() === 'local' ? 'check' : 'public' }}</mat-icon>
              Local — {{ zone }} · {{ zoneOffset }}
            </button>
          </mat-menu>
          <button matIconButton [matMenuTriggerFor]="userMenu" aria-label="Account" class="ml-2">
            <mat-icon>account_circle</mat-icon>
          </button>
          <mat-menu #userMenu="matMenu">
            <div class="menu-user">
              Signed in as <strong>{{ auth.user() }}</strong>
              <span class="menu-role">{{ roleLabel() }}</span>
            </div>
            <button mat-menu-item (click)="logout()"><mat-icon>logout</mat-icon> Sign out</button>
          </mat-menu>
        </mat-toolbar>

        <div class="content-inner">
          <router-outlet />
        </div>
        <footer class="app-footer">
          <div class="ft-cols">
            <div class="ft-col">
              <h4>Distributed</h4>
              <ul>
                <li>Gossip-based cluster with leader election, or group sharding across nodes</li>
                <li>Store replicated per node, or shared cluster-wide (H2 / MySQL / PostgreSQL)</li>
                <li>One console entry point, self-balancing over every node with failover</li>
              </ul>
            </div>
            <div class="ft-col">
              <h4>Scheduling</h4>
              <ul>
                <li>Timing-wheel engine driving cron and year-based YCRON schedules</li>
                <li>Retries, misfire policies, and pause / resume / cancel on every task</li>
                <li>Bean tasks on executors, or HTTP-API tasks straight from the scheduler</li>
              </ul>
            </div>
            <div class="ft-col">
              <h4>DAG workflow</h4>
              <ul>
                <li>DAG orchestration on the openspreader engine, run across the cluster</li>
                <li>Channels with computing reducers, conditional routing, and subgraphs</li>
                <li>Trigger a flow by hand, from a finished task, or on a schedule</li>
              </ul>
            </div>
          </div>
          <div class="ft-bar">
            <span class="ft-tagline">Cronsmith scheduling with cronflow DAG orchestration</span>
            <span class="ft-sep">·</span>
            <span>© 2026 cronflower</span>
            <span class="flex-1"></span>
            <a class="ft-gh" href="https://github.com/paganini2008/cronflower" target="_blank"
               rel="noopener" aria-label="cronflower on GitHub" matTooltip="View source on GitHub">
              <svg viewBox="0 0 16 16" width="20" height="20" aria-hidden="true" fill="currentColor">
                <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38
                  0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01
                  1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95
                  0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27
                  2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82
                  1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01
                  2.2 0 .21.15.46.55.38A8.01 8.01 0 0016 8c0-4.42-3.58-8-8-8z"/>
              </svg>
            </a>
            <img src="default_logo.png" class="ft-logo" alt="cronflower" />
          </div>
        </footer>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: [`
    :host { display: block; height: 100vh; }
    .app-toolbar { position: sticky; top: 0; z-index: 10; color: var(--foreground);
      background: var(--background); border-bottom: 1px solid var(--border); box-shadow: none; }
    .sidenav-brand { display: flex; align-items: center; height: 68px; padding-left: 1.1rem;
      text-decoration: none; flex: 0 0 auto; border-bottom: 1px solid var(--sidebar-border); }
    .sidenav-brand .logo { height: 46px; width: auto; display: block; }
    .app-sidenav.collapsed .sidenav-brand { padding-left: 0.7rem; }
    .app-sidenav.collapsed .sidenav-brand .logo { height: 40px; }
    @media (max-width: 900px) { .brand-tagline { display: none; } }
    .env-badge { margin-left: 0.7rem; padding: 0.1rem 0.5rem; border-radius: 999px; font-size: 0.7rem;
      font-weight: 700; text-transform: uppercase; letter-spacing: 0.04em; }
    .env-badge.up { color: #ffffff; background: #16a34a; border: 1px solid #16a34a; }
    .env-badge.down { color: #ffffff; background: #dc2626; border: 1px solid #dc2626; }
    .tz-badge { display: inline-flex; align-items: center; gap: 0.3rem; margin-right: 0.9rem;
      padding: 0.25rem 0.65rem; border: 1px solid var(--border); cursor: pointer; border-radius: 8px;
      background: var(--background); color: var(--muted-foreground); font-size: 0.75rem; font-weight: 600;
      white-space: nowrap; transition: background .15s, color .15s, border-color .15s; }
    .tz-badge:hover { background: var(--accent); color: var(--foreground); }
    .tz-badge mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .tz-badge .caret { margin-left: -0.1rem; opacity: 0.7; }
    .menu-head { padding: 0.4rem 1rem 0.2rem; font-size: 0.72rem; font-weight: 700; letter-spacing: 0.03em;
      text-transform: uppercase; color: var(--muted-foreground); }
    .flex-1 { flex: 1 1 auto; }
    .ml-2 { margin-left: 0.5rem; }
    .mr-1 { margin-right: 0.25rem; }
    .menu-user { padding: 0.5rem 1rem; font-size: 0.8rem; color: var(--muted-foreground);
      border-bottom: 1px solid var(--border); }
    .menu-role { display: block; margin-top: 0.15rem; font-size: 0.72rem; color: var(--muted-foreground);
      text-transform: capitalize; }
    .app-container { height: 100vh; background: var(--background); }
    .app-container ::ng-deep .mat-drawer-content { transition: margin-left 0.18s ease; }
    .app-sidenav, .app-sidenav ::ng-deep .mat-drawer-inner-container { border-radius: 0; }
    /* Light shadcn sidebar: near-white surface, hairline right border, blue active pill. */
    .app-sidenav { width: 236px; background: var(--sidebar); border-right: 1px solid var(--sidebar-border);
      padding-top: 0; display: flex; flex-direction: column; transition: width 0.18s ease; overflow-x: hidden; }
    .app-sidenav.collapsed { width: 68px; }
    .app-sidenav .nav-list { padding: 0.6rem 0.6rem; }
    .app-sidenav .mat-mdc-list-item, .app-sidenav .mdc-list-item {
      border-radius: 8px !important; margin: 2px 0; overflow: visible; min-height: 42px; }
    .app-sidenav .nav-label,
    .app-sidenav .mat-mdc-list-item .mdc-list-item__primary-text { color: var(--sidebar-foreground);
      font-weight: 500; font-size: 0.9rem; }
    .app-sidenav .mat-mdc-list-item mat-icon { color: var(--muted-foreground); }
    .app-sidenav .mat-mdc-list-item:hover { background: var(--sidebar-accent); }
    /* Active item: a solid brand-blue pill with white label/icon. */
    .app-sidenav .active-link { background: var(--primary); position: relative; }
    .app-sidenav .active-link:hover { background: var(--primary); }
    .app-sidenav .active-link .nav-label,
    .app-sidenav .active-link .mdc-list-item__primary-text { color: var(--primary-foreground); font-weight: 600; }
    .app-sidenav .active-link mat-icon { color: var(--primary-foreground); }
    .app-sidenav.collapsed .nav-label { display: none; }
    .app-content { background: var(--background); display: flex; flex-direction: column; min-height: 100%; }
    .content-inner { flex: 1 0 auto; padding: 1.5rem 1.5rem 2rem; box-sizing: border-box; }
    /* Neutral footer that sits quietly under the content. */
    .app-footer { flex: 0 0 auto; border-top: 1px solid var(--border); background: var(--muted);
      color: var(--muted-foreground); font-size: 0.78rem; padding: 1.6rem 1.75rem 1.1rem; }
    .app-footer .ft-cols { display: grid; grid-template-columns: repeat(3, 1fr); gap: 1.25rem 2.5rem;
      max-width: 1200px; margin: 0 auto; }
    @media (max-width: 820px) { .app-footer .ft-cols { grid-template-columns: 1fr; } }
    .app-footer .ft-col h4 { margin: 0 0 0.6rem; color: var(--foreground); font-size: 0.82rem; font-weight: 700;
      letter-spacing: 0.02em; }
    .app-footer .ft-col ul { list-style: none; margin: 0; padding: 0; display: flex;
      flex-direction: column; gap: 0.4rem; }
    .app-footer .ft-col li { position: relative; padding-left: 1rem; line-height: 1.45;
      color: var(--muted-foreground); }
    .app-footer .ft-col li::before { content: ''; position: absolute; left: 0; top: 0.5rem;
      width: 5px; height: 5px; border-radius: 50%; background: var(--primary); opacity: 0.6; }
    .app-footer .ft-bar { display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap;
      max-width: 1200px; margin: 1.3rem auto 0; padding-top: 0.9rem;
      border-top: 1px solid var(--border); }
    .app-footer .ft-tagline { color: var(--foreground); font-weight: 500; }
    .app-footer .ft-sep { opacity: 0.5; }
    .app-footer .flex-1 { flex: 1 1 auto; }
    .app-footer .ft-gh { display: inline-flex; align-items: center; color: var(--muted-foreground);
      margin-right: 0.9rem; transition: color .15s; }
    .app-footer .ft-gh:hover { color: var(--foreground); }
    .app-footer .ft-logo { height: 22px; width: auto; opacity: 0.9; }
    /* The logo art is dark; in dark mode render it light so it stays legible on the dark chrome. */
    :host-context(:root.dark) .sidenav-brand .logo,
    :host-context(:root.dark) .app-footer .ft-logo { filter: brightness(0) invert(1); }
  `],
})
export class Shell {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly opened = signal(true);
  protected readonly collapsed = signal<boolean>(this.restoreCollapsed());
  protected readonly zone = localZone();
  protected readonly zoneOffset = localZoneOffset();
  protected readonly tzLabel = tzLabel;
  protected readonly mode = tzMode;

  private readonly api = inject(CronsmithApi);
  /** Deployment environment (dev / prod) shown in the header; blank until loaded or on error. */
  protected readonly env = toSignal(
    this.api.meta().pipe(map((m) => m.env), catchError(() => of(''))),
    { initialValue: '' },
  );
  /** Live cluster health, polled — drives the env badge colour (green UP / red DOWN). A 503 or an
   *  unreachable cluster (the call errors) counts as DOWN. */
  protected readonly healthUp = toSignal(
    timer(0, 8000).pipe(switchMap(() => this.api.health().pipe(
      map((h) => (h?.status ?? 'UP').toUpperCase() === 'UP'),
      catchError(() => of(false)),
    ))),
    { initialValue: true },
  );

  /** Dark theme toggle — adds/removes the `dark` class on <html> (the Spartan/shadcn dark variant),
   *  persisted per browser. */
  protected readonly dark = signal<boolean>(this.restoreDark());

  protected toggleDark(): void {
    this.dark.update((v) => !v);
    this.applyDark(this.dark());
    try {
      localStorage.setItem('cf.theme.dark', String(this.dark()));
    } catch {
      /* storage unavailable */
    }
  }

  private restoreDark(): boolean {
    let on = false;
    try {
      on = localStorage.getItem('cf.theme.dark') === 'true';
    } catch {
      on = false;
    }
    this.applyDark(on);
    return on;
  }

  private applyDark(on: boolean): void {
    document.documentElement.classList.toggle('dark', on);
  }

  protected useTz(mode: 'utc' | 'local'): void {
    setTzMode(mode);
  }

  /** Collapse the sidebar to an icon-only rail (persisted per browser). */
  protected toggleCollapse(): void {
    this.collapsed.update((v) => !v);
    try {
      localStorage.setItem('cf.nav.collapsed', String(this.collapsed()));
    } catch {
      /* storage unavailable */
    }
  }

  private restoreCollapsed(): boolean {
    try {
      return localStorage.getItem('cf.nav.collapsed') === 'true';
    } catch {
      return false;
    }
  }

  private readonly feature = inject(CronflowFeature);

  // Menu gated by role: Dashboard = anyone; Tasks = admin/scheduler_admin; System = admin;
  // DAG = admin/workflow_admin (and only when the backend has the cronflow add-on).
  private readonly allNav: NavItem[] = [
    { path: '/dashboard', label: 'Dashboard', icon: 'dashboard', can: () => this.auth.isAuthed() },
    { path: '/tasks', label: 'Tasks', icon: 'list_alt', can: () => this.auth.canScheduler() },
    {
      path: '/dag', label: 'DAG', icon: 'account_tree',
      can: () => this.auth.canWorkflow() && !!this.feature.available(),
    },
    { path: '/system', label: 'System', icon: 'dns', can: () => this.auth.canSystem() },
  ];

  protected readonly nav = computed<NavItem[]>(() => this.allNav.filter((i) => i.can()));

  /** Roles for the account menu, shown lower-cased (admin / scheduler_admin / ...). */
  protected readonly roleLabel = computed(() =>
    this.auth.roles().map((r) => r.toLowerCase()).join(', ') || 'none');

  constructor() {
    this.feature.ensure();
  }

  protected logout(): void {
    this.auth.logout();
    this.router.navigateByUrl('/login');
  }
}
