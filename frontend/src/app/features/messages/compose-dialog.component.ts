import { Component, inject, OnInit } from '@angular/core';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';
import { of, Subject } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DestroyRef } from '@angular/core';
import {
  FormBuilder, ReactiveFormsModule, Validators,
} from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { MessageService } from '../../core/services/message.service';
import type { MessageDto, UserSearchResult } from '../../core/models/message.model';

export interface ComposeDialogData {
  recipientId?: number;
  recipientUsername?: string;
  eventId?: number | null;
  eventTitle?: string | null;
}

@Component({
  selector: 'app-compose-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule, MatButtonModule, MatFormFieldModule,
    MatInputModule, MatAutocompleteModule, MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>New Message</h2>

    <mat-dialog-content>
      <form [formGroup]="form" class="compose-form">

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>To (username)</mat-label>
          <input matInput formControlName="recipientSearch"
                 [matAutocomplete]="auto"
                 placeholder="Start typing a username…">
          <mat-autocomplete #auto="matAutocomplete"
                            [displayWith]="displayUser"
                            (optionSelected)="onUserSelected($event.option.value)">
            @for (u of suggestions; track u.id) {
              <mat-option [value]="u">{{ u.username }}</mat-option>
            }
          </mat-autocomplete>
          @if (form.get('recipientId')?.hasError('required') && form.touched) {
            <mat-error>Please select a recipient.</mat-error>
          }
        </mat-form-field>

        @if (data.eventTitle) {
          <mat-form-field appearance="outline" class="full-width">
            <mat-label>Regarding event</mat-label>
            <input matInput [value]="data.eventTitle" readonly>
          </mat-form-field>
        }

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Subject</mat-label>
          <input matInput formControlName="subject">
          @if (form.get('subject')?.hasError('required') && form.get('subject')?.touched) {
            <mat-error>Subject is required.</mat-error>
          }
        </mat-form-field>

        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Message</mat-label>
          <textarea matInput formControlName="body" rows="6"></textarea>
          @if (form.get('body')?.hasError('required') && form.get('body')?.touched) {
            <mat-error>Message body is required.</mat-error>
          }
        </mat-form-field>

        @if (serverError) {
          <p class="error-msg">{{ serverError }}</p>
        }
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-raised-button color="primary" (click)="send()" [disabled]="submitting">
        @if (submitting) { <mat-spinner diameter="20"></mat-spinner> }
        @else { Send }
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .compose-form { display: flex; flex-direction: column; gap: 0.25rem; min-width: 420px; }
    .full-width    { width: 100%; }
    .error-msg     { color: #d32f2f; font-size: 0.85rem; margin-top: 0.25rem; }
  `],
})
export class ComposeDialogComponent implements OnInit {
  readonly data        = inject<ComposeDialogData>(MAT_DIALOG_DATA);
  readonly dialogRef   = inject(MatDialogRef<ComposeDialogComponent>);
  private readonly fb  = inject(FormBuilder);
  private readonly svc = inject(MessageService);
  private readonly destroyRef = inject(DestroyRef);

  suggestions: UserSearchResult[] = [];
  submitting  = false;
  serverError = '';

  private readonly search$ = new Subject<string>();

  form = this.fb.group({
    recipientId:     [this.data.recipientId ?? null as number | null, Validators.required],
    recipientSearch: [this.data.recipientUsername ?? ''],
    subject:         ['', [Validators.required, Validators.maxLength(255)]],
    body:            ['', [Validators.required, Validators.maxLength(4000)]],
  });

  ngOnInit(): void {
    // Autocomplete: debounce keystrokes, skip if a user was already selected
    this.search$
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap(q => q.length >= 2 ? this.svc.searchRecipients(q) : of([])),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(users => { this.suggestions = users; });

    this.form.get('recipientSearch')!.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(v => {
        if (typeof v === 'string') {
          this.form.patchValue({ recipientId: null }, { emitEvent: false });
          this.search$.next(v);
        }
      });
  }

  displayUser(u: UserSearchResult | string | null): string {
    if (!u) return '';
    return typeof u === 'string' ? u : u.username;
  }

  onUserSelected(u: UserSearchResult): void {
    this.form.patchValue({ recipientId: u.id }, { emitEvent: false });
    this.suggestions = [];
  }

  send(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const { recipientId, subject, body } = this.form.getRawValue();
    this.submitting  = true;
    this.serverError = '';
    this.svc.send({
      recipientId: recipientId!,
      eventId:     this.data.eventId ?? null,
      subject:     subject!,
      body:        body!,
    }).subscribe({
      next: (msg: MessageDto) => { this.submitting = false; this.dialogRef.close(msg); },
      error: err => {
        this.submitting  = false;
        this.serverError = err.error?.message ?? 'Failed to send message. Please try again.';
      },
    });
  }
}
