import {
  Component, DestroyRef, inject, OnInit,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CurrencyPipe, DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';

import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';

import { BookingService } from '../../core/services/booking.service';
import type { BookingDto } from '../../core/models/booking.model';
import { ComposeDialogComponent } from '../messages/compose-dialog.component';

@Component({
  selector: 'app-my-bookings',
  standalone: true,
  imports: [
    CurrencyPipe, DatePipe, RouterLink,
    MatTableModule, MatPaginatorModule, MatProgressSpinnerModule,
    MatIconModule, MatButtonModule, MatChipsModule, MatTooltipModule,
  ],
  templateUrl: './my-bookings.component.html',
  styleUrl: './my-bookings.component.scss',
})
export class MyBookingsComponent implements OnInit {
  private readonly bookingSvc  = inject(BookingService);
  private readonly dialog      = inject(MatDialog);
  private readonly snackBar    = inject(MatSnackBar);
  private readonly destroyRef  = inject(DestroyRef);

  bookings: BookingDto[] = [];
  totalElements = 0;
  loading = false;

  // Paging state lives on the component, not on the <mat-paginator> ViewChild:
  // the paginator is rendered inside an @if and does not exist on first render,
  // so we must not depend on it to trigger the initial load.
  pageIndex = 0;
  pageSize = 10;

  readonly columns = ['event', 'ticketType', 'qty', 'total', 'status', 'bookedAt', 'actions'];

  ngOnInit(): void {
    this.load();
  }

  onPage(e: PageEvent): void {
    this.pageIndex = e.pageIndex;
    this.pageSize  = e.pageSize;
    this.load();
  }

  private load(): void {
    this.loading = true;
    this.bookingSvc
      .getMyBookings(this.pageIndex, this.pageSize)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: page => {
          this.loading = false;
          this.bookings = page.content;
          this.totalElements = page.totalElements;
        },
        error: () => { this.loading = false; },
      });
  }

  messageOrganizer(booking: BookingDto): void {
    this.dialog.open(ComposeDialogComponent, {
      data: {
        recipientId:       booking.organizerId,
        recipientUsername: booking.organizerUsername,
        eventId:           booking.eventId,
        eventTitle:        booking.eventTitle,
      },
      width: '540px',
    }).afterClosed().subscribe(result => {
      if (result) this.snackBar.open('Message sent to organizer.', 'Close', { duration: 3000 });
    });
  }
}
