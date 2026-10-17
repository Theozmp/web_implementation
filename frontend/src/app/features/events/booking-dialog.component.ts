import { Component, inject, OnInit } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import {
  FormBuilder, ReactiveFormsModule, Validators,
} from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';

import { BookingService } from '../../core/services/booking.service';
import { EventDetail, TicketTypeItem } from '../../core/models/event.model';

export interface BookingDialogData {
  event: EventDetail;
}

/** Two-step booking dialog: selection, then confirmation, then the API call. */
@Component({
  selector: 'app-booking-dialog',
  standalone: true,
  imports: [
    CurrencyPipe, ReactiveFormsModule,
    MatDialogModule, MatButtonModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatProgressSpinnerModule,
    MatIconModule, MatDividerModule,
  ],
  template: `
    <h2 mat-dialog-title>Book Tickets — {{ data.event.title }}</h2>

    <mat-dialog-content>
      @if (step === 'form') {
        <form [formGroup]="form" class="booking-form">
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Ticket type</mat-label>
            <mat-select formControlName="ticketTypeId"
                        (selectionChange)="onTypeChange()">
              @for (tt of availableTypes; track tt.id) {
                <mat-option [value]="tt.id">
                  {{ tt.name }} — {{ tt.price | currency:'EUR':'symbol':'1.2-2' }}
                  ({{ tt.quantity }} left)
                </mat-option>
              }
            </mat-select>
            @if (form.get('ticketTypeId')?.hasError('required') && form.get('ticketTypeId')?.touched) {
              <mat-error>Please select a ticket type.</mat-error>
            }
          </mat-form-field>

          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Quantity</mat-label>
            <input matInput type="number" formControlName="numberOfTickets"
                   [min]="1" [max]="maxAvailable">
            @if (form.get('numberOfTickets')?.hasError('min')) {
              <mat-error>At least 1 ticket required.</mat-error>
            }
            @if (form.get('numberOfTickets')?.hasError('max')) {
              <mat-error>Only {{ maxAvailable }} seat(s) left.</mat-error>
            }
            @if (maxAvailable > 0) {
              <mat-hint>Max: {{ maxAvailable }}</mat-hint>
            }
          </mat-form-field>

          @if (totalCost !== null) {
            <div class="total-row">
              <span>Total</span>
              <strong>{{ totalCost | currency:'EUR':'symbol':'1.2-2' }}</strong>
            </div>
          }
        </form>
      } @else {
        <!-- Confirmation step -->
        <div class="confirm-body">
          <mat-icon class="confirm-icon">confirmation_number</mat-icon>
          <p><strong>{{ form.get('numberOfTickets')?.value }}×</strong>
             {{ selectedType?.name }}</p>
          <p class="confirm-total">
            {{ totalCost | currency:'EUR':'symbol':'1.2-2' }}
          </p>
          <p class="confirm-note">This booking cannot be undone. Confirm?</p>
          @if (serverError) {
            <p class="error-msg">{{ serverError }}</p>
          }
        </div>
      }
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      @if (step === 'form') {
        <button mat-button mat-dialog-close>Cancel</button>
        <button mat-raised-button color="primary" (click)="toConfirm()">
          Review booking
        </button>
      } @else {
        <button mat-button (click)="step = 'form'" [disabled]="submitting">
          Back
        </button>
        <button mat-raised-button color="primary"
                (click)="confirm()" [disabled]="submitting">
          @if (submitting) { <mat-spinner diameter="20"></mat-spinner> }
          @else { Confirm booking }
        </button>
      }
    </mat-dialog-actions>
  `,
  styles: [`
    .booking-form { display: flex; flex-direction: column; gap: 0.5rem; min-width: 380px; }
    .full-width { width: 100%; }
    .total-row { display: flex; justify-content: space-between; padding: 0.75rem 0; border-top: 1px solid rgba(0,0,0,0.12); }
    .confirm-body { display: flex; flex-direction: column; align-items: center; gap: 0.5rem; padding: 1rem 0; text-align: center; min-width: 320px; }
    .confirm-icon { font-size: 3rem; width: 3rem; height: 3rem; color: #1976d2; }
    .confirm-total { font-size: 1.4rem; font-weight: 700; }
    .confirm-note { color: rgba(0,0,0,0.54); font-size: 0.85rem; }
    .error-msg { color: #d32f2f; font-size: 0.85rem; }
  `],
})
export class BookingDialogComponent implements OnInit {
  readonly data: BookingDialogData   = inject(MAT_DIALOG_DATA);
  readonly dialogRef                 = inject(MatDialogRef<BookingDialogComponent>);
  private readonly fb                = inject(FormBuilder);
  private readonly bookingSvc        = inject(BookingService);

  step:        'form' | 'confirm' = 'form';
  submitting   = false;
  serverError  = '';
  maxAvailable = 0;
  totalCost: number | null = null;

  form = this.fb.group({
    ticketTypeId:    [null as number | null, Validators.required],
    numberOfTickets: [1, [Validators.required, Validators.min(1)]],
  });

  // Only ticket types that still have seats
  get availableTypes(): TicketTypeItem[] {
    return this.data.event.ticketTypes.filter(tt => tt.quantity > 0);
  }

  get selectedType(): TicketTypeItem | undefined {
    const id = this.form.get('ticketTypeId')?.value;
    return this.data.event.ticketTypes.find(tt => tt.id === id);
  }

  ngOnInit(): void {
    // Recompute total whenever quantity changes
    this.form.get('numberOfTickets')?.valueChanges.subscribe(() => this.updateTotal());
  }

  onTypeChange(): void {
    this.maxAvailable = this.selectedType?.quantity ?? 0;
    // Update max validator dynamically
    this.form.get('numberOfTickets')?.setValidators([
      Validators.required,
      Validators.min(1),
      Validators.max(this.maxAvailable),
    ]);
    this.form.get('numberOfTickets')?.setValue(1);
    this.form.get('numberOfTickets')?.updateValueAndValidity();
    this.updateTotal();
  }

  toConfirm(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.step = 'confirm';
    this.serverError = '';
  }

  confirm(): void {
    const { ticketTypeId, numberOfTickets } = this.form.getRawValue();
    this.submitting  = true;
    this.serverError = '';
    this.bookingSvc
      .createBooking(this.data.event.id, { ticketTypeId: ticketTypeId!, numberOfTickets: numberOfTickets! })
      .subscribe({
        next: booking  => { this.submitting = false; this.dialogRef.close(booking); },
        error: err => {
          this.submitting  = false;
          this.serverError = err.error?.message ?? 'Booking failed. Please try again.';
        },
      });
  }

  private updateTotal(): void {
    const price = this.selectedType?.price ?? 0;
    const qty   = this.form.get('numberOfTickets')?.value ?? 0;
    this.totalCost = price * qty;
  }
}
