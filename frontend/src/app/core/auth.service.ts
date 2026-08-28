import { Injectable, computed, inject, signal } from '@angular/core';
import { ConfigService } from './runtime-config';

/**
 * Demo authentication for cronflower. The cronsmith-server ships without auth, so this is a
 * client-side gate only: it remembers a signed-in user in localStorage and guards the routes. The
 * accepted credentials come from {@link ConfigService} (config.json). Replace {@link login} with a
 * real credentials call when the server grows auth.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private static readonly KEY = 'cf.auth.user';
  private readonly config = inject(ConfigService);

  private readonly _user = signal<string | null>(this.restore());
  readonly user = this._user.asReadonly();
  readonly isAuthed = computed(() => this._user() !== null);

  login(username: string, password: string): boolean {
    const creds = this.config.auth;
    const ok = username.trim() === creds.username && password === creds.password;
    if (ok) {
      this.persist(username.trim());
      this._user.set(username.trim());
    }
    return ok;
  }

  logout(): void {
    try {
      localStorage.removeItem(AuthService.KEY);
    } catch {
      /* storage unavailable */
    }
    this._user.set(null);
  }

  private restore(): string | null {
    try {
      return localStorage.getItem(AuthService.KEY);
    } catch {
      return null;
    }
  }

  private persist(user: string): void {
    try {
      localStorage.setItem(AuthService.KEY, user);
    } catch {
      /* storage unavailable */
    }
  }
}
