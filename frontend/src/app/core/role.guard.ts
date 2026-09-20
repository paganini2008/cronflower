import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Route guard: allow only users holding at least one of the given roles, otherwise send them to the
 * dashboard. This mirrors the backend's URL authorization so a deep link to a forbidden page lands
 * somewhere sensible instead of on a page full of 403s. Roles: ADMIN / SCHEDULER_ADMIN /
 * WORKFLOW_ADMIN / USER.
 */
export function roleGuard(...roles: string[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (roles.some((r) => auth.hasRole(r))) {
      return true;
    }
    return router.createUrlTree(['/dashboard']);
  };
}
