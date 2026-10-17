import { Component, inject } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../core/services/auth.service';
import { RecommendedEventsComponent } from '../recommendations/recommended-events.component';

/**
 * Authenticated user home page.
 *
 * Shows a personalised "Recommended for you" strip (top 5) from the Biased MF
 * recommender. For cold-start users it falls back to the most-booked upcoming
 * events, handled transparently by the backend.
 *
 * Below it, mode cards surface what the account can do (spec req. 6): everyone
 * gets the "Attend events" card (browse/book), and ORGANIZER accounts also get an
 * "Organize events" card (create/manage), rather than hiding those behind plain links.
 */
@Component({
  selector: 'app-home',
  standalone: true,
  imports: [
    AsyncPipe, RouterLink,
    MatCardModule, MatButtonModule, MatIconModule,
    RecommendedEventsComponent,
  ],
  template: `
    <div class="home-page">
      @if (auth.currentUser$ | async; as user) {
        <h2>Welcome back, {{ user.firstName || user.username }}!</h2>
        <p class="subtitle">Here are some events we think you'll enjoy.</p>

        <!-- Personalised recommendations (top 5) -->
        <app-recommended-events [limit]="5" />

        <!-- Two modes every registered user can switch between (spec req. 6) -->
        <div class="mode-cards">
          <!-- Participant mode -->
          <mat-card class="mode-card">
            <mat-card-content>
              <div class="mode-head">
                <mat-icon class="mode-icon">local_activity</mat-icon>
                <div>
                  <h3>Attend events</h3>
                  <p>Browse, search and book events created by others, and keep track of your tickets.</p>
                </div>
              </div>
              <div class="mode-actions">
                <a mat-raised-button color="primary" routerLink="/events">
                  <mat-icon>search</mat-icon> Browse events
                </a>
                <a mat-stroked-button routerLink="/my-bookings">
                  <mat-icon>confirmation_number</mat-icon> My bookings
                </a>
              </div>
            </mat-card-content>
          </mat-card>

          <!-- Organizer mode (only for accounts registered as ORGANIZER) -->
          @if (user.role === 'ORGANIZER') {
            <mat-card class="mode-card">
              <mat-card-content>
                <div class="mode-head">
                  <mat-icon class="mode-icon">event</mat-icon>
                  <div>
                    <h3>Organize events</h3>
                    <p>Create and manage your own events, set ticket types and review their bookings.</p>
                  </div>
                </div>
                <div class="mode-actions">
                  <a mat-raised-button color="primary" routerLink="/my-events/new">
                    <mat-icon>add</mat-icon> Create event
                  </a>
                  <a mat-stroked-button routerLink="/my-events">
                    <mat-icon>event</mat-icon> My events
                  </a>
                </div>
              </mat-card-content>
            </mat-card>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .home-page {
      max-width: 960px;
      margin: 2rem auto;
      padding: 0 1rem;
    }
    h2 { margin-bottom: 0.25rem; }
    .subtitle {
      color: var(--mat-sys-on-surface-variant, #666);
      margin-bottom: 0.5rem;
    }
    .mode-cards {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 1rem;
      margin-top: 1.5rem;
    }
    .mode-card { height: 100%; }
    .mode-head {
      display: flex;
      align-items: flex-start;
      gap: 0.75rem;
      margin-bottom: 1rem;
    }
    .mode-icon {
      font-size: 2rem;
      width: 2rem;
      height: 2rem;
      flex: none;
      color: var(--mat-sys-primary, #1976d2);
    }
    .mode-head h3 { margin: 0 0 0.25rem; }
    .mode-head p {
      margin: 0;
      color: var(--mat-sys-on-surface-variant, #666);
    }
    .mode-actions {
      display: flex;
      gap: 0.75rem;
      flex-wrap: wrap;
    }
  `],
})
export class HomeComponent {
  readonly auth = inject(AuthService);
}
