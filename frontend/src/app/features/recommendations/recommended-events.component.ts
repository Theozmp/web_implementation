import { Component, DestroyRef, inject, Input, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';

import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';

import { RecommendationService } from '../../core/services/recommendation.service';
import { EventSummary } from '../../core/models/event.model';

/**
 * Standalone "Recommended for you" card strip.
 *
 * Drop <app-recommended-events> into any page. It fetches the top-N
 * recommendations on init and renders them as clickable cards. On error or an
 * empty list it hides itself so it never breaks the host layout.
 *
 * Usage:
 *   <app-recommended-events [limit]="5" />
 */
@Component({
  selector: 'app-recommended-events',
  standalone: true,
  imports: [
    DatePipe, RouterLink,
    MatCardModule, MatButtonModule, MatIconModule,
    MatProgressSpinnerModule, MatChipsModule,
  ],
  template: `
    @if (loading) {
      <div class="rec-loading">
        <mat-spinner diameter="32" />
      </div>
    } @else if (events.length > 0) {
      <section class="rec-section">
        <h3 class="rec-title">
          <mat-icon class="rec-icon">recommend</mat-icon>
          Recommended for you
        </h3>
        <div class="rec-cards">
          @for (ev of events; track ev.id) {
            <mat-card class="rec-card" appearance="outlined">
              <mat-card-header>
                <mat-card-title>{{ ev.title }}</mat-card-title>
                <mat-card-subtitle>
                  {{ ev.startDatetime | date:'mediumDate' }}
                  @if (ev.city) { · {{ ev.city }} }
                </mat-card-subtitle>
              </mat-card-header>
              <mat-card-actions>
                <a mat-button color="primary" [routerLink]="['/events', ev.id]">
                  View Details
                </a>
              </mat-card-actions>
            </mat-card>
          }
        </div>
      </section>
    }
  `,
  styles: [`
    .rec-loading {
      display: flex;
      justify-content: center;
      padding: 1rem;
    }
    .rec-section {
      margin: 1.5rem 0;
    }
    .rec-title {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      font-size: 1.1rem;
      font-weight: 600;
      margin-bottom: 0.75rem;
      color: var(--mat-sys-primary, #1976d2);
    }
    .rec-icon {
      font-size: 1.2rem;
      height: 1.2rem;
      width: 1.2rem;
    }
    .rec-cards {
      display: flex;
      gap: 1rem;
      flex-wrap: wrap;
    }
    .rec-card {
      flex: 1 1 200px;
      max-width: 280px;
      min-width: 180px;
    }
  `],
})
export class RecommendedEventsComponent implements OnInit {
  /** Number of recommendations to request from the backend. */
  @Input() limit = 5;

  private readonly recSvc     = inject(RecommendationService);
  private readonly destroyRef = inject(DestroyRef);

  events: EventSummary[] = [];
  loading = true;

  ngOnInit(): void {
    this.recSvc.getRecommendations(this.limit).pipe(
      catchError(() => of([])),   // hide the section on any error (e.g. 401)
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(results => {
      this.events  = results;
      this.loading = false;
    });
  }
}
