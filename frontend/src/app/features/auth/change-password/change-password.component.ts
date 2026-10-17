import { Component, inject } from '@angular/core';
import {
  AbstractControl, FormBuilder, ReactiveFormsModule,
  ValidationErrors, ValidatorFn, Validators,
} from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { AuthService } from '../../../core/services/auth.service';

function passwordStrengthValidator(): ValidatorFn {
  return (c: AbstractControl): ValidationErrors | null => {
    const v: string = c.value ?? '';
    if (!v) return null;
    if (v.length < 8)         return { passwordTooShort: true };
    if (!/[a-zA-Z]/.test(v)) return { passwordNeedsLetter: true };
    if (!/\d/.test(v))        return { passwordNeedsDigit: true };
    return null;
  };
}

function newPasswordMatchValidator(group: AbstractControl): ValidationErrors | null {
  const newPass  = group.get('newPassword');
  const confirm  = group.get('confirmNewPassword');
  if (!newPass || !confirm) return null;
  const match = newPass.value === confirm.value;
  if (!match && !confirm.hasError('passwordMatch')) {
    confirm.setErrors({ ...(confirm.errors ?? {}), passwordMatch: true });
  } else if (match && confirm.hasError('passwordMatch')) {
    const { passwordMatch, ...rest } = confirm.errors!;
    confirm.setErrors(Object.keys(rest).length ? rest : null);
  }
  return match ? null : { passwordMatch: true };
}

@Component({
  selector: 'app-change-password',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './change-password.component.html',
  styleUrl:    './change-password.component.scss',
})
export class ChangePasswordComponent {
  private readonly fb     = inject(FormBuilder);
  private readonly auth   = inject(AuthService);
  private readonly router = inject(Router);

  form = this.fb.group({
    currentPassword:    ['', [Validators.required]],
    newPassword:        ['', [Validators.required, passwordStrengthValidator()]],
    confirmNewPassword: ['', [Validators.required]],
  }, { validators: newPasswordMatchValidator });

  loading      = false;
  errorMessage = '';

  get f() { return this.form.controls; }

  onSubmit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }

    this.loading      = true;
    this.errorMessage = '';

    const { currentPassword, newPassword, confirmNewPassword } = this.form.getRawValue();

    this.auth.changePassword(currentPassword!, newPassword!, confirmNewPassword!).subscribe({
      next: () => {
        this.loading = false;
        // Clear the mustChangePassword flag in the in-memory user state so that
        // authGuard no longer redirects subsequent navigation back to this page.
        this.auth.markPasswordChanged();
        this.router.navigate(['/home']);
      },
      error: (err: HttpErrorResponse) => {
        this.loading      = false;
        this.errorMessage = err.error?.message ?? 'Failed to change password. Please try again.';
      },
    });
  }
}
