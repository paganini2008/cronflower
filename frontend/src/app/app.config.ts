import {
  ApplicationConfig, provideAppInitializer, provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth.interceptor';
import { loadRuntimeConfig } from './core/runtime-config';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Load config.json (backend location + API prefixes) before the app renders.
    provideAppInitializer(loadRuntimeConfig),
    provideRouter(routes, withComponentInputBinding()),
    // authInterceptor attaches the bearer token and redirects to /login on a 401.
    provideHttpClient(withFetch(), withInterceptors([authInterceptor])),
    provideAnimationsAsync(),
  ]
};
