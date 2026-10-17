import { Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { switchMap } from 'rxjs';

import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import {
  MAT_DIALOG_DATA, MatDialog, MatDialogModule,
} from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { AdminUserService } from '../../core/services/admin-user.service';
import { UserDetail } from '../../core/models/admin-user.model';

// Confirm dialog (approve)

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [MatButtonModule, MatDialogModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content><p>{{ data.message }}</p></mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" [mat-dialog-close]="true">Confirm</button>
    </mat-dialog-actions>
  `,
})
export class ConfirmDialogComponent {
  readonly data = inject<{ title: string; message: string }>(MAT_DIALOG_DATA);
}

// Reject dialog (reason textarea)

@Component({
  selector: 'app-reject-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule],
  template: `
    <h2 mat-dialog-title>Reject User</h2>
    <mat-dialog-content>
      <p>Are you sure you want to reject this account?</p>
      <mat-form-field appearance="outline" style="width:100%;min-width:320px;margin-top:.5rem">
        <mat-label>Reason (optional)</mat-label>
        <textarea matInput [formControl]="reason" rows="3"
                  placeholder="Enter a reason visible to the admin audit log…"></textarea>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="warn" [mat-dialog-close]="reason.value || ''">Reject</button>
    </mat-dialog-actions>
  `,
})
export class RejectDialogComponent {
  readonly reason = new FormControl('');
}

// Detail component

@Component({
  selector: 'app-admin-user-detail',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    TitleCasePipe,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatDialogModule,
  ],
  templateUrl: './admin-user-detail.component.html',
  styleUrl:    './admin-user-detail.component.scss',
})
export class AdminUserDetailComponent {
  private readonly svc    = inject(AdminUserService);
  private readonly route  = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);

  user:        UserDetail | null = null;
  loading      = true;
  actionError  = '';

  constructor() {
    // Load user whenever the :id route param changes
    this.route.paramMap.pipe(
      switchMap(params => {
        this.loading = true;
        this.user    = null;
        return this.svc.getUser(Number(params.get('id')));
      }),
    ).subscribe({
      next: u => { this.user = u; this.loading = false; },
      error: () => { this.loading = false; },
    });
  }

  approve(): void {
    if (!this.user) return;
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: {
        title:   'Approve Account',
        message: `Grant "${this.user.username}" access to the platform?`,
      },
      width: '380px',
    });
    ref.afterClosed().subscribe(confirmed => {
      if (confirmed) this.doApprove();
    });
  }

  reject(): void {
    if (!this.user) return;
    const ref = this.dialog.open(RejectDialogComponent, { width: '420px' });
    ref.afterClosed().subscribe(result => {
      // undefined means cancelled; a string (even empty) means confirmed
      if (result !== undefined) this.doReject(result as string);
    });
  }

  private doApprove(): void {
    this.actionError = '';
    this.svc.approveUser(this.user!.id).subscribe({
      next: updated => { this.user = updated; },
      error: (err: HttpErrorResponse) => {
        this.actionError = err.error?.message ?? 'Approve failed. Please try again.';
      },
    });
  }

  private doReject(reason: string): void {
    this.actionError = '';
    this.svc.rejectUser(this.user!.id, reason || undefined).subscribe({
      next: updated => { this.user = updated; },
      error: (err: HttpErrorResponse) => {
        this.actionError = err.error?.message ?? 'Reject failed. Please try again.';
      },
    });
  }
}
