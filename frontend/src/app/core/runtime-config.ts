import { Injectable, inject, signal } from '@angular/core';

/**
 * Runtime configuration, loaded from `config.json` at startup (served from `public/`, so it can be
 * edited on a deployed build without recompiling — just refresh). Falls back to the defaults below
 * when the file is missing or unreadable.
 *
 * Note: the login credentials here gate a client-side demo only — the cronsmith server ships without
 * auth. Because the file is fetched by the browser, its contents are not secret. Real protection has
 * to live on the server.
 */
export interface RuntimeConfig {
  auth: { username: string; password: string };
  /**
   * Base URL of the cronsmith backend. Defaults to `http://localhost:19090` (a local scheduler) when
   * the key is absent. Set it to **`""` (empty)** to call the API on the SAME origin as the console —
   * for the dev server proxy or the deployed Node/nginx proxy, which forward the API prefix +
   * `/actuator` to the backend (no CORS). Set it to any other backend/gateway origin (e.g.
   * `http://localhost:7500/cs` behind KONG) when the console is served WITHOUT a proxy; the backend
   * must then allow CORS.
   */
  apiBaseUrl: string;
  /**
   * The backend's REST API prefix — must match the scheduler's `cronsmith.server.api-prefix`
   * (default `/cronsmith`). All API calls are made under it; `/actuator` is separate and never
   * prefixed. Change this only if you changed the backend prefix (then also update the dev/Node
   * proxies, which forward this same prefix).
   */
  apiPrefix: string;
}

export const DEFAULT_CONFIG: RuntimeConfig = {
  auth: { username: 'admin', password: 'admin' },
  apiBaseUrl: 'http://localhost:19090',
  apiPrefix: '/cronsmith',
};

@Injectable({ providedIn: 'root' })
export class ConfigService {
  private readonly _config = signal<RuntimeConfig>(DEFAULT_CONFIG);
  readonly config = this._config.asReadonly();

  set(config: RuntimeConfig): void {
    this._config.set(config);
  }

  get auth(): RuntimeConfig['auth'] {
    return this._config().auth;
  }

  /** Backend base URL with any trailing slash trimmed; '' means same-origin. */
  get apiBaseUrl(): string {
    return (this._config().apiBaseUrl ?? '').replace(/\/+$/, '');
  }

  /**
   * REST API prefix, normalized to a leading slash and no trailing slash; '' (or '/') means the API
   * is served at the root. Defaults to '/cronsmith'.
   */
  get apiPrefix(): string {
    let p = (this._config().apiPrefix ?? '/cronsmith').trim();
    if (p === '' || p === '/') {
      return '';
    }
    if (!p.startsWith('/')) {
      p = '/' + p;
    }
    return p.replace(/\/+$/, '');
  }
}

/**
 * App initializer: fetch `config.json` before the app renders, so the auth gate already has the
 * configured credentials by the time the login route activates. Keeps the built-in defaults on any
 * failure.
 */
export async function loadRuntimeConfig(): Promise<void> {
  const config = inject(ConfigService);
  try {
    const res = await fetch('config.json', { cache: 'no-store' });
    if (res.ok) {
      const json = (await res.json()) as Partial<RuntimeConfig>;
      config.set({
        auth: { ...DEFAULT_CONFIG.auth, ...(json?.auth ?? {}) },
        apiBaseUrl: json?.apiBaseUrl ?? DEFAULT_CONFIG.apiBaseUrl,
        apiPrefix: json?.apiPrefix ?? DEFAULT_CONFIG.apiPrefix,
      });
    }
  } catch {
    /* config.json absent or invalid — keep defaults */
  }
}
