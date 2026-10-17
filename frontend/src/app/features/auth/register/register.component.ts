import { Component, inject } from '@angular/core';
import {
  AbstractControl, FormBuilder, ReactiveFormsModule,
  ValidationErrors, ValidatorFn, Validators,
} from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { MatRadioModule } from '@angular/material/radio';
import { AuthService } from '../../../core/services/auth.service';

// Synchronous validators

function passwordStrengthValidator(): ValidatorFn {
  return (c: AbstractControl): ValidationErrors | null => {
    const v: string = c.value ?? '';
    if (!v) return null;
    if (v.length < 8)        return { passwordTooShort: true };
    if (!/[a-zA-Z]/.test(v)) return { passwordNeedsLetter: true };
    if (!/\d/.test(v))        return { passwordNeedsDigit: true };
    return null;
  };
}

/**
 * Validates a 9-digit Greek AFM with the modulo-11 check digit: weight the first 8
 * digits by 2^(8-i), sum them, and (sum % 11) % 10 must equal the 9th digit.
 */
function greekAfmValidator(): ValidatorFn {
  return (c: AbstractControl): ValidationErrors | null => {
    const v: string = c.value ?? '';
    if (!v) return null;
    if (!/^\d{9}$/.test(v)) return { afmFormat: true };
    let sum = 0;
    for (let i = 0; i < 8; i++) sum += parseInt(v[i], 10) << (8 - i);
    return (sum % 11) % 10 === parseInt(v[8], 10) ? null : { afmChecksum: true };
  };
}

/**
 * Cross-field validator placed on the form group.
 * Propagates the error to the confirmPassword control so that
 * mat-form-field picks up the invalid state and shows the error styling.
 */
function passwordMatchValidator(group: AbstractControl): ValidationErrors | null {
  const pass    = group.get('password');
  const confirm = group.get('confirmPassword');
  if (!pass || !confirm) return null;
  const match = pass.value === confirm.value;
  if (!match && !confirm.hasError('passwordMatch')) {
    confirm.setErrors({ ...(confirm.errors ?? {}), passwordMatch: true });
  } else if (match && confirm.hasError('passwordMatch')) {
    const { passwordMatch, ...rest } = confirm.errors!;
    confirm.setErrors(Object.keys(rest).length ? rest : null);
  }
  return match ? null : { passwordMatch: true };
}

// Component

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatIconModule,
    MatRadioModule,
  ],
  templateUrl: './register.component.html',
  styleUrl:    './register.component.scss',
})
export class RegisterComponent {
  private readonly fb   = inject(FormBuilder);
  private readonly auth = inject(AuthService);

  form = this.fb.group({
    // Role the applicant registers under (Organizer or Participant)
    role:            ['' as 'ORGANIZER' | 'PARTICIPANT' | '', [Validators.required]],
    // Account
    username:        ['', [Validators.required, Validators.minLength(4),
                           Validators.maxLength(30), Validators.pattern(/^[a-zA-Z0-9_]+$/)]],
    password:        ['', [Validators.required, passwordStrengthValidator()]],
    confirmPassword: ['', [Validators.required]],
    // Personal
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName:  ['', [Validators.required, Validators.maxLength(100)]],
    email:     ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    taxId:     ['', [Validators.required, greekAfmValidator()]],
    // Optional contact / location
    phone:       ['', [Validators.maxLength(30)]],
    addressLine: ['', [Validators.maxLength(255)]],
    city:        ['', [Validators.maxLength(100)]],
    country:     ['', [Validators.maxLength(100)]],
    latitude:    [null as number | null, [Validators.min(-90),  Validators.max(90)]],
    longitude:   [null as number | null, [Validators.min(-180), Validators.max(180)]],
  }, { validators: passwordMatchValidator });

  loading      = false;
  submitted    = false;
  errorMessage = '';
  /** Field-level errors returned by the backend (e.g. username already taken). */
  serverErrors: Record<string, string> = {};

  get f() { return this.form.controls; }

  onSubmit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }

    this.loading      = true;
    this.errorMessage = '';
    this.serverErrors = {};

    // Drop empty-string / null optional fields so the backend doesn't see ""
    const payload = Object.fromEntries(
      Object.entries(this.form.getRawValue()).filter(([, v]) => v !== '' && v !== null),
    );

    this.auth.register(payload).subscribe({
      next: () => { this.loading = false; this.submitted = true; },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        if (err.error?.fieldErrors) {
          this.serverErrors = err.error.fieldErrors as Record<string, string>;
        } else {
          this.errorMessage = err.error?.message ?? 'Registration failed. Please try again.';
        }
      },
    });
  }
}
