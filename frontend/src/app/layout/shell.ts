import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AuthService } from '../core/auth.service';
import { localZone, localZoneOffset, setTzMode, tzLabel, tzMode } from '../core/util';

interface NavItem {
  path: string;
  label: string;
  icon: string;
}

@Component({
  selector: 'cf-shell',
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive,
    MatToolbarModule, MatSidenavModule, MatListModule, MatIconModule, MatButtonModule, MatMenuModule,
    MatTooltipModule,
  ],
  template: `
    <mat-toolbar class="app-toolbar">
      <button matIconButton (click)="toggle()" aria-label="Toggle navigation">
        <mat-icon>menu</mat-icon>
      </button>
      <a routerLink="/dashboard" class="brand" aria-label="cronflower home">
        <img src="default_logo.png" class="logo" alt="cronflower" />
      </a>
      <span class="flex-1"></span>
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
      <span class="brand-sub">cronsmith control plane</span>
      <button matIconButton [matMenuTriggerFor]="userMenu" aria-label="Account" class="ml-2">
        <mat-icon>account_circle</mat-icon>
      </button>
      <mat-menu #userMenu="matMenu">
        <div class="menu-user">Signed in as <strong>{{ auth.user() }}</strong></div>
        <button mat-menu-item (click)="logout()"><mat-icon>logout</mat-icon> Sign out</button>
      </mat-menu>
    </mat-toolbar>

    <mat-sidenav-container class="app-container">
      <mat-sidenav [opened]="opened()" mode="side" class="app-sidenav">
        <mat-nav-list>
          @for (item of nav; track item.path) {
            <a mat-list-item [routerLink]="item.path" routerLinkActive="active-link">
              <mat-icon matListItemIcon>{{ item.icon }}</mat-icon>
              <span matListItemTitle>{{ item.label }}</span>
            </a>
          }
        </mat-nav-list>
      </mat-sidenav>

      <mat-sidenav-content class="app-content">
        <div class="content-inner">
          <router-outlet />
        </div>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: [`
    :host { display: block; height: 100vh; }
    .app-toolbar { position: sticky; top: 0; z-index: 10; color: #0f2c4d; background: #fff;
      border-bottom: 1px solid #e3eaf3; box-shadow: 0 2px 10px rgba(21, 101, 192, 0.08); }
    .brand { display: inline-flex; align-items: center; margin-left: 0.4rem; text-decoration: none; }
    .logo { height: 46px; width: auto; display: block; }
    .brand-sub { font-size: 0.8rem; color: #7a8aa0; font-weight: 500; }
    .tz-badge { display: inline-flex; align-items: center; gap: 0.3rem; margin-right: 0.9rem;
      padding: 0.2rem 0.6rem; border: none; cursor: pointer; border-radius: 999px; background: #f1f5f9;
      color: #5b6b7f; font-size: 0.75rem; font-weight: 600; white-space: nowrap;
      transition: background .15s, color .15s; }
    .tz-badge:hover { background: #e2ebf6; color: #1565c0; }
    .tz-badge mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .tz-badge .caret { margin-left: -0.1rem; opacity: 0.7; }
    .menu-head { padding: 0.4rem 1rem 0.2rem; font-size: 0.72rem; font-weight: 700; letter-spacing: 0.03em;
      text-transform: uppercase; color: #93a2b6; }
    .flex-1 { flex: 1 1 auto; }
    .ml-2 { margin-left: 0.5rem; }
    .menu-user { padding: 0.5rem 1rem; font-size: 0.8rem; color: #5b6b7f; border-bottom: 1px solid #eef2f7; }
    .app-container { height: calc(100vh - 64px); background: #f4f7fb; }
    .app-sidenav { width: 240px; border-right: 1px solid #e3eaf3; background: #fff; padding-top: 0.5rem; }
    .app-sidenav .active-link { background: #e8f1fd; border-right: 3px solid #1565c0; font-weight: 600; }
    .app-sidenav .active-link mat-icon { color: #1565c0; }
    .app-content { background: #f4f7fb; }
    .content-inner { padding: 1.5rem 1.5rem 2.5rem; min-height: 100%; box-sizing: border-box; }
  `],
})
export class Shell {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly opened = signal(true);
  protected readonly zone = localZone();
  protected readonly zoneOffset = localZoneOffset();
  protected readonly tzLabel = tzLabel;
  protected readonly mode = tzMode;

  protected useTz(mode: 'utc' | 'local'): void {
    setTzMode(mode);
  }

  protected readonly nav: NavItem[] = [
    { path: '/dashboard', label: 'Dashboard', icon: 'dashboard' },
    { path: '/tasks', label: 'Tasks', icon: 'list_alt' },
    { path: '/executors', label: 'Executors', icon: 'memory' },
    { path: '/cluster', label: 'Cluster', icon: 'hub' },
    { path: '/health', label: 'System Health', icon: 'monitor_heart' },
  ];

  protected toggle(): void {
    this.opened.update((v) => !v);
  }

  protected logout(): void {
    this.auth.logout();
    this.router.navigateByUrl('/login');
  }
}
