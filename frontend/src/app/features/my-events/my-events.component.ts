import { Component, DestroyRef, inject, ViewChild, AfterViewInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { merge, Subject, EMPTY } from 'rxjs';
import { startWith, switchMap, catchError } from 'rxjs/operators';

import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, MatPaginator } from '@angular/material/paginator';
import { MatSortModule, MatSort } from '@angular/material/sort';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialogModule, MatDialog, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';

import { EventService } from '../../core/services/event.service';
import { EventSummary } from '../../core/models/event.model';

// Reusable confirmation dialog

export interface ConfirmDialogData {
  title: string;
  message: string;
  confirmLabel: string;
  confirmColor: 'primary' | 'warn' | 'accent';
}

@Component({
  selector: 'app-event-confirm-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content><p>{{ data.message }}</p></mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-raised-button [color]="data.confirmColor" [mat-dialog-close]="true">
        {{ data.confirmLabel }}
      </button>
    </mat-dialog-actions>
  `,
})
export class EventConfirmDialogComponent {
  readonly data: ConfirmDialogData = inject(MAT_DIALOG_DATA);
}

// Organizer event list

@Component({
  selector: 'app-my-events',
  standalone: true,
  imports: [
    DatePipe, RouterLink, FormsModule,
    MatTableModule, MatPaginatorModule, MatSortModule,
    MatButtonModule, MatIconModule, MatSelectModule,
    MatFormFieldModule, MatCardModule, MatProgressSpinnerModule,
    MatDialogModule, MatMenuModule, MatTooltipModule,
  ],
  templateUrl: './my-events.component.html',
  styleUrl: './my-events.component.scss',
})
export class MyEventsComponent implements AfterViewInit {
  private readonly eventSvc   = inject(EventService);
  private readonly dialog     = inject(MatDialog);
  private readonly snack      = inject(MatSnackBar);
  private readonly router     = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild(MatSort)      sort!: MatSort;

  private readonly reload$ = new Subject<void>();

  displayedColumns = ['title', 'status', 'startDatetime', 'capacity', 'sold', 'actions'];
  events: EventSummary[] = [];
  totalElements = 0;
  loading = false;
  statusFilter = '';

  ngAfterViewInit(): void {
    merge(this.sort.sortChange, this.paginator.page, this.reload$)
      .pipe(
        startWith({}),
        switchMap(() => {
          this.loading = true;
          return this.eventSvc.getMyEvents({
            status: this.statusFilter || undefined,
            page:   this.paginator.pageIndex,
            size:   this.paginator.pageSize,
            sort:   this.sort.active
                      ? `${this.sort.active},${this.sort.direction}`
                      : 'startDatetime,desc',
          }).pipe(catchError(() => { this.loading = false; return EMPTY; }));
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(page => {
        this.loading = false;
        this.events = page.content;
        this.totalElements = page.totalElements;
      });
  }

  applyFilter(): void {
    this.paginator.pageIndex = 0;
    this.reload$.next();
  }

  editEvent(id: number): void {
    this.router.navigate(['/my-events', id, 'edit']);
  }

  publishEvent(event: EventSummary): void {
    this.confirm({
      title: 'Publish Event',
      message: `Publish "${event.title}"? Once published, some fields become read-only.`,
      confirmLabel: 'Publish',
      confirmColor: 'primary',
    }).subscribe(ok => {
      if (!ok) return;
      this.eventSvc.publishEvent(event.id).subscribe({
        next: updated => {
          this.replaceRow(event.id, { ...event, status: updated.status });
          this.snack.open('Event published.', 'OK', { duration: 3000 });
        },
        error: err => this.snack.open(err.error?.message ?? 'Failed to publish.', 'OK', { duration: 4000 }),
      });
    });
  }

  cancelEvent(event: EventSummary): void {
    this.confirm({
      title: 'Cancel Event',
      message: `Cancel "${event.title}"? All confirmed bookings will be flagged for refund.`,
      confirmLabel: 'Cancel Event',
      confirmColor: 'warn',
    }).subscribe(ok => {
      if (!ok) return;
      this.eventSvc.cancelEvent(event.id).subscribe({
        next: updated => {
          this.replaceRow(event.id, { ...event, status: updated.status });
          this.snack.open('Event cancelled.', 'OK', { duration: 3000 });
        },
        error: err => this.snack.open(err.error?.message ?? 'Failed to cancel.', 'OK', { duration: 4000 }),
      });
    });
  }

  deleteEvent(event: EventSummary): void {
    this.confirm({
      title: 'Delete Event',
      message: `Permanently delete "${event.title}"? This cannot be undone.`,
      confirmLabel: 'Delete',
      confirmColor: 'warn',
    }).subscribe(ok => {
      if (!ok) return;
      this.eventSvc.deleteEvent(event.id).subscribe({
        next: () => {
          this.events = this.events.filter(e => e.id !== event.id);
          this.totalElements--;
          this.snack.open('Event deleted.', 'OK', { duration: 3000 });
        },
        error: err => this.snack.open(err.error?.message ?? 'Failed to delete.', 'OK', { duration: 4000 }),
      });
    });
  }

  private confirm(data: ConfirmDialogData) {
    return this.dialog
      .open(EventConfirmDialogComponent, { width: '420px', data })
      .afterClosed();
  }

  private replaceRow(id: number, updated: EventSummary): void {
    const idx = this.events.findIndex(e => e.id === id);
    if (idx !== -1) {
      this.events[idx] = updated;
      this.events = [...this.events];
    }
  }
}
