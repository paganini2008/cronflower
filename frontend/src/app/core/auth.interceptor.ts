import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Attaches the bearer token to API requests and, on a 401, drops the session and bounces to the login
 * page. The login call itself is left unauthenticated. config.json is fetched with the browser's
 * fetch() (not HttpClient), so it never passes through here.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const isLogin = req.url.includes('/auth/login');
  const token = auth.token();

  const authed = token && !isLogin
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authed).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401 && !isLogin) {
        auth.clear();
        router.navigate(['/login'], { queryParams: { returnUrl: router.url } });
      }
      return throwError(() => err);
    }),
  );
};
