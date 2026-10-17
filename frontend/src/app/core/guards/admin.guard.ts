import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Restricts access to ADMIN-only routes.
 * authGuard must run first (already listed as a guard on the same route).
 */
export const adminGuard: CanActivateFn = () => {
  const auth   = inject(AuthService);
  const router = inject(Router);

  if (!auth.hasRole('ADMIN')) {
    router.navigate(['/home']);
    return false;
  }
  return true;
};
