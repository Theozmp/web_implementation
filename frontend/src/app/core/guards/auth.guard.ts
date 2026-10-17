import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Prevents navigation to authenticated routes when no valid JWT is present.
 *
 * If the user is logged in but mustChangePassword == true, redirects to the
 * change-password page (the backend also enforces this at the API level, but
 * the frontend redirect provides a better UX than a 403 error).
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth   = inject(AuthService);
  const router = inject(Router);

  if (!auth.isLoggedIn()) {
    router.navigate(['/login'], { queryParams: { returnUrl: state.url } });
    return false;
  }

  // Redirect to change-password if the flag is set and we're not already there
  const user = auth.currentUser;
  if (user?.mustChangePassword && state.url !== '/change-password') {
    router.navigate(['/change-password']);
    return false;
  }

  return true;
};
