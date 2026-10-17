import { HttpInterceptorFn, HttpRequest, HttpHandlerFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * JWT auth interceptor.
 *
 * Attaches the Bearer token to outgoing API requests; requests without a stored
 * token (e.g. /auth/login, /auth/register) pass through unchanged. On a 401 it logs
 * out and redirects to /login (token missing or expired); 403s are left to the
 * calling component to handle.
 *
 * Registered as a functional interceptor via
 * provideHttpClient(withInterceptors([...])) in app.config.ts.
 */
export const authInterceptor: HttpInterceptorFn = (
  req: HttpRequest<unknown>,
  next: HttpHandlerFn
) => {
  const authService = inject(AuthService);
  const router      = inject(Router);

  const token = authService.getToken();
  const authedReq = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authedReq).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401) {
        // token expired or missing: force re-authentication
        authService.logout();
        router.navigate(['/login']);
      }
      // 403 is left to the calling component (show a message, stay on the page)
      return throwError(() => err);
    })
  );
};
