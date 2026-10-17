import { Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../../core/services/auth.service';

/** Landing page for unauthenticated visitors. */
@Component({
  selector: 'app-welcome',
  standalone: true,
  imports: [RouterLink, MatButtonModule, MatCardModule, MatIconModule],
  template: `
    <div class="welcome-container">
      <mat-card class="welcome-card">
        <mat-card-header>
          <mat-icon mat-card-avatar color="primary">event</mat-icon>
          <mat-card-title>Event Reservation System</mat-card-title>
          <mat-card-subtitle>Discover, organise, and book events near you</mat-card-subtitle>
        </mat-card-header>

        <mat-card-content>
          <p class="welcome-text">
            Browse concerts, conferences, theatre shows, workshops and more.
            Register to organise your own events or book tickets in seconds.
          </p>
        </mat-card-content>

        <mat-card-actions align="end">
          <a mat-button routerLink="/events">
            <mat-icon>search</mat-icon> Browse Events
          </a>
          <a mat-stroked-button routerLink="/login">Log In</a>
          <a mat-flat-button color="primary" routerLink="/register">Register</a>
        </mat-card-actions>
      </mat-card>
    </div>
  `,
  styles: [`
    .welcome-container {
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 80vh;
      padding: 1.5rem;
    }
    .welcome-card { max-width: 500px; width: 100%; }
    .welcome-text { margin-top: 1rem; color: var(--mat-sys-on-surface-variant); }
    mat-card-actions { padding: 0 1rem 1rem; gap: 8px; }
  `],
})
export class WelcomeComponent {
  private auth   = inject(AuthService);
  private router = inject(Router);

  ngOnInit() {
    // If the user is already logged in, skip the welcome page
    if (this.auth.isLoggedIn()) {
      this.router.navigate(['/home']);
    }
  }
}
