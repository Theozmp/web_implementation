import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthResponse, UserSummary } from '../models/user.model';

/**
 * Central authentication state manager.
 *
 * Token storage: memory plus sessionStorage, not localStorage. localStorage would
 * persist across browser restarts and is readable by any injected script (XSS).
 * sessionStorage clears when the tab closes and survives refreshes within the tab,
 * so F5 doesn't log you out. Memory alone is safest but loses the token on refresh.
 * We keep it in both memory and sessionStorage to survive refresh/deep-links
 * without the cross-session risk of localStorage.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {

  private readonly http   = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly base   = environment.apiBaseUrl;

  // in-memory token (the primary source)
  private _token: string | null = null;

  // reactive user state
  private readonly _currentUser$ = new BehaviorSubject<UserSummary | null>(null);

  /** Observable of the currently authenticated user (null when logged out). */
  readonly currentUser$ = this._currentUser$.asObservable();

  /** Synchronous getter, safe to call in guards. */
  get currentUser(): UserSummary | null { return this._currentUser$.getValue(); }

  constructor() {
    // restore the session from sessionStorage on load/F5, but only if the token
    // hasn't expired.
    const savedToken = sessionStorage.getItem('access_token');
    const savedUser  = sessionStorage.getItem('current_user');
    if (savedToken && savedUser && !this.isTokenExpired(savedToken)) {
      this._token = savedToken;
      this._currentUser$.next(JSON.parse(savedUser) as UserSummary);
    } else {
      sessionStorage.removeItem('access_token');
      sessionStorage.removeItem('current_user');
    }
  }

  // auth operations

  register(payload: object): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.base}/auth/register`, payload);
  }

  login(username: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.base}/auth/login`, { username, password }).pipe(
      tap(res => this.storeAuth(res))
    );
  }

  changePassword(currentPassword: string, newPassword: string, confirmNewPassword: string) {
    return this.http.post<void>(`${this.base}/auth/change-password`,
      { currentPassword, newPassword, confirmNewPassword });
  }

  logout(): void {
    this._token = null;
    this._currentUser$.next(null);
    sessionStorage.removeItem('access_token');
    sessionStorage.removeItem('current_user');
    this.router.navigate(['/welcome']);
  }

  /**
   * Called after a successful change-password request. Clears mustChangePassword in
   * the in-memory user state and sessionStorage so authGuard stops redirecting to
   * /change-password. The endpoint doesn't reissue a JWT, so the token's flag is now
   * stale, but authGuard reads the live BehaviorSubject rather than the token claims.
   */
  markPasswordChanged(): void {
    const user = this.currentUser;
    if (!user) return;
    const updated = { ...user, mustChangePassword: false };
    this._currentUser$.next(updated);
    sessionStorage.setItem('current_user', JSON.stringify(updated));
  }

  // token access (used by the auth interceptor)

  getToken(): string | null { return this._token; }

  isLoggedIn(): boolean {
    return !!this._token && !this.isTokenExpired(this._token);
  }

  hasRole(role: string): boolean {
    return this.currentUser?.role === role;
  }

  // internal helpers

  private storeAuth(res: AuthResponse): void {
    this._token = res.accessToken;
    this._currentUser$.next(res.user);
    sessionStorage.setItem('access_token', res.accessToken);
    sessionStorage.setItem('current_user', JSON.stringify(res.user));
  }

  private isTokenExpired(token: string): boolean {
    try {
      // the JWT payload is the middle segment, base64url-encoded
      const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
      return payload.exp * 1000 < Date.now();
    } catch {
      return true;
    }
  }
}
