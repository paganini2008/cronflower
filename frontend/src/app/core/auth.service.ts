import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { ConfigService } from './runtime-config';

interface LoginResponse {
  token: string;
  tokenType: string;
  username: string;
  roles: string[];
  expiresInSeconds: number;
}

interface StoredAuth {
  token: string;
  username: string;
  roles: string[];
}

/**
 * Real backend authentication for cronflower. Signs in against the Cronflow server's
 * {@code POST /auth/login}, stores the returned bearer JWT (+ username + roles) in localStorage, and
 * exposes role helpers used to gate menus and routes. The token is attached to API calls by
 * {@code authInterceptor}. Cronflower has no self-registration: accounts are provisioned server side
 * (users.xml).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private static readonly KEY = 'cf.auth';
  private readonly http = inject(HttpClient);
  private readonly config = inject(ConfigService);

  private readonly _auth = signal<StoredAuth | null>(this.restore());
  readonly user = computed(() => this._auth()?.username ?? null);
  readonly roles = computed(() => this._auth()?.roles ?? []);
  readonly isAuthed = computed(() => this._auth() !== null);

  // Role helpers (backend roles are upper-case, no ROLE_ prefix: ADMIN / SCHEDULER_ADMIN / ...).
  readonly isAdmin = computed(() => this.hasRole('ADMIN'));
  /** Manage cronsmith (tasks). */
  readonly canScheduler = computed(() => this.isAdmin() || this.hasRole('SCHEDULER_ADMIN'));
  /** Manage cronflow (DAG). */
  readonly canWorkflow = computed(() => this.isAdmin() || this.hasRole('WORKFLOW_ADMIN'));
  /** System (executors / cluster / health) is admin only. */
  readonly canSystem = computed(() => this.isAdmin());

  token(): string | null {
    return this._auth()?.token ?? null;
  }

  hasRole(role: string): boolean {
    return this.roles().includes(role.toUpperCase());
  }

  login(username: string, password: string): Observable<LoginResponse> {
    const url = `${this.config.apiBaseUrl}/auth/login`;
    return this.http.post<LoginResponse>(url, { username: username.trim(), password }).pipe(
      tap((res) => this.persist({
        token: res.token,
        username: res.username,
        roles: (res.roles ?? []).map((r) => r.toUpperCase()),
      })),
    );
  }

  logout(): void {
    // Stateless server side (the client just drops the token); fire and forget.
    try {
      this.http.post(`${this.config.apiBaseUrl}/auth/logout`, {}).subscribe({ error: () => {} });
    } catch {
      /* ignore */
    }
    this.clear();
  }

  /** Drop the stored session (also called by the interceptor on a 401). */
  clear(): void {
    try {
      localStorage.removeItem(AuthService.KEY);
    } catch {
      /* storage unavailable */
    }
    this._auth.set(null);
  }

  private persist(auth: StoredAuth): void {
    try {
      localStorage.setItem(AuthService.KEY, JSON.stringify(auth));
    } catch {
      /* storage unavailable */
    }
    this._auth.set(auth);
  }

  private restore(): StoredAuth | null {
    try {
      const raw = localStorage.getItem(AuthService.KEY);
      if (!raw) {
        return null;
      }
      const parsed = JSON.parse(raw) as StoredAuth;
      return parsed && parsed.token ? parsed : null;
    } catch {
      return null;
    }
  }
}
