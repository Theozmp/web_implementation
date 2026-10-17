import {
  ApplicationConfig,
  provideBrowserGlobalErrorListeners,
  provideZoneChangeDetection,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';

/**
 * Root application config (Angular standalone / functional style).
 *
 * withComponentInputBinding() injects route params as @Input() on components, so we
 * skip the ActivatedRoute boilerplate. withInterceptors([authInterceptor])
 * registers the JWT interceptor for every HttpClient call. provideAnimations() isn't
 * needed here; Material animations are initialised automatically.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor])),
  ],
};
