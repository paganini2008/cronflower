import { Injectable, inject, signal } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { CronsmithApi } from './api.service';

/**
 * Detects whether the backend has the cronflow (DAG) add-on, so the console can show or hide every
 * DAG menu and page. cronflow contributes a `cronflow` component to `/actuator/health` (present only
 * when it is deployed), so its presence there is the signal — a cronsmith-only backend has none.
 *
 * The probe runs once and is cached; {@link available} drives the nav, {@link ensure} gates the DAG
 * routes so a direct URL cannot open a broken page on a cronsmith-only backend.
 */
@Injectable({ providedIn: 'root' })
export class CronflowFeature {
  private readonly api = inject(CronsmithApi);

  /** undefined until the first probe resolves, then whether cronflow is available. */
  readonly available = signal<boolean | undefined>(undefined);

  private probe?: Promise<boolean>;

  /** Probe once (cached). Resolves to whether cronflow is available; never rejects. */
  ensure(): Promise<boolean> {
    if (!this.probe) {
      this.probe = firstValueFrom(this.api.health())
        .then((h) => !!h?.components?.['cronflow'])
        .catch(() => false)
        .then((ok) => {
          this.available.set(ok);
          return ok;
        });
    }
    return this.probe;
  }
}

/** Route guard: allow DAG routes only when cronflow is available, else redirect to the dashboard. */
export const cronflowGuard: CanActivateFn = async () => {
  const feature = inject(CronflowFeature);
  const router = inject(Router);
  return (await feature.ensure()) ? true : router.parseUrl('/dashboard');
};
