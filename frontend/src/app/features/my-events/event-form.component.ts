import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AsyncPipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  AbstractControl, FormArray, FormBuilder, FormGroup,
  ReactiveFormsModule, ValidationErrors, Validators,
} from '@angular/forms';
import { switchMap } from 'rxjs/operators';
import { of } from 'rxjs';

import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDividerModule } from '@angular/material/divider';
import { MatTooltipModule } from '@angular/material/tooltip';

import { EventService } from '../../core/services/event.service';
import { CategoryService } from '../../core/services/category.service';
import { EventCreatePayload } from '../../core/models/event.model';
import { environment } from '../../../environments/environment';

// Cross-field validators

function endAfterStartValidator(group: AbstractControl): ValidationErrors | null {
  const start = group.get('startDatetime')?.value as string;
  const end   = group.get('endDatetime')?.value as string;
  if (!start || !end) return null;
  return new Date(end) > new Date(start) ? null : { dateRangeInvalid: true };
}

function ticketCapacityValidator(group: AbstractControl): ValidationErrors | null {
  const capacity    = +(group.get('capacity')?.value ?? 0);
  const ticketTypes = group.get('ticketTypes') as FormArray;
  if (!ticketTypes || !capacity) return null;
  const total = (ticketTypes.controls as FormGroup[]).reduce(
    (sum, g) => sum + (+(g.get('quantity')?.value ?? 0)), 0,
  );
  return total <= capacity ? null : { ticketCapacityExceeded: { capacity, total } };
}

// Event create / edit form

@Component({
  selector: 'app-event-form',
  standalone: true,
  imports: [
    AsyncPipe, ReactiveFormsModule, RouterLink,
    MatCardModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatIconModule, MatSelectModule,
    MatProgressSpinnerModule, MatDividerModule, MatTooltipModule,
  ],
  templateUrl: './event-form.component.html',
  styleUrl: './event-form.component.scss',
})
export class EventFormComponent implements OnInit {
  private readonly route      = inject(ActivatedRoute);
  private readonly router     = inject(Router);
  private readonly fb         = inject(FormBuilder);
  private readonly eventSvc   = inject(EventService);
  private readonly catSvc     = inject(CategoryService);
  private readonly snack      = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  readonly categories$ = this.catSvc.categories$;

  // Prefix for photo URLs returned as relative paths (e.g. /uploads/...) from the API
  readonly apiBase = environment.apiBaseUrl.replace('/api/v1', '');

  eventId: number | null = null;
  loading        = false;
  saving         = false;
  uploadingPhoto = false;
  photoUrls: string[] = [];

  form = this.fb.group({
    title:         ['', [Validators.required, Validators.maxLength(200)]],
    description:   [''],
    startDatetime: ['', [Validators.required]],
    endDatetime:   ['', [Validators.required]],
    capacity:      [100, [Validators.required, Validators.min(1)]],
    venueName:     [''],
    address:       [''],
    city:          [''],
    country:       [''],
    latitude:      [null as number | null],
    longitude:     [null as number | null],
    categoryIds:   [[] as number[], [Validators.required, Validators.minLength(1)]],
    ticketTypes:   this.fb.array([this.buildTicketRow()]),
  }, { validators: [endAfterStartValidator, ticketCapacityValidator] });

  get ticketTypes(): FormArray { return this.form.get('ticketTypes') as FormArray; }

  ngOnInit(): void {
    this.route.paramMap
      .pipe(
        switchMap(params => {
          const id = params.get('id');
          if (!id) return of(null);
          this.eventId = +id;
          this.loading = true;
          return this.eventSvc.getEvent(this.eventId);
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: event => {
          this.loading = false;
          if (!event) return;
          this.photoUrls = event.photoUrls ?? [];
          this.form.patchValue({
            title:         event.title,
            description:   event.description ?? '',
            startDatetime: this.toDatetimeLocal(event.startDatetime),
            endDatetime:   this.toDatetimeLocal(event.endDatetime),
            capacity:      event.capacity,
            venueName:     event.venueName ?? '',
            address:       event.address ?? '',
            city:          event.city ?? '',
            country:       event.country ?? '',
            latitude:      event.latitude,
            longitude:     event.longitude,
            categoryIds:   event.categories.map(c => c.id),
          });
          this.ticketTypes.clear();
          for (const tt of event.ticketTypes) {
            this.ticketTypes.push(this.buildTicketRow(tt.id, tt.name, tt.price, tt.quantity));
          }
        },
        error: () => {
          this.loading = false;
          this.router.navigate(['/my-events']);
        },
      });
  }

  addTicketType(): void {
    this.ticketTypes.push(this.buildTicketRow());
  }

  removeTicketType(i: number): void {
    if (this.ticketTypes.length > 1) this.ticketTypes.removeAt(i);
  }

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    const raw = this.form.getRawValue();
    const payload: EventCreatePayload = {
      title:         raw.title!,
      description:   raw.description || undefined,
      startDatetime: raw.startDatetime!,
      endDatetime:   raw.endDatetime!,
      capacity:      raw.capacity!,
      venueName:     raw.venueName || undefined,
      address:       raw.address   || undefined,
      city:          raw.city      || undefined,
      country:       raw.country   || undefined,
      latitude:      raw.latitude  ?? null,
      longitude:     raw.longitude ?? null,
      categoryIds:   raw.categoryIds ?? [],
      ticketTypes:   (raw.ticketTypes as { id?: number | null; name: string; price: number; quantity: number }[])
                       .map(t => ({ id: t.id ?? undefined, name: t.name, price: t.price, quantity: t.quantity })),
    };
    this.saving = true;
    const req$ = this.eventId
      ? this.eventSvc.updateEvent(this.eventId, payload)
      : this.eventSvc.createEvent(payload);

    req$.subscribe({
      next: () => {
        this.saving = false;
        this.snack.open(
          this.eventId ? 'Event updated.' : 'Event created.',
          'OK', { duration: 3000 },
        );
        this.router.navigate(['/my-events']);
      },
      error: err => {
        this.saving = false;
        this.snack.open(err.error?.message ?? 'Save failed.', 'OK', { duration: 4000 });
      },
    });
  }

  onPhotoSelected(ev: Event): void {
    const file = (ev.target as HTMLInputElement).files?.[0];
    if (!file || !this.eventId) return;
    this.uploadingPhoto = true;
    this.eventSvc.uploadPhoto(this.eventId, file)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: photo => {
          this.photoUrls = [...this.photoUrls, photo.url];
          this.uploadingPhoto = false;
          (ev.target as HTMLInputElement).value = '';
        },
        error: err => {
          this.snack.open(err.error?.message ?? 'Photo upload failed.', 'OK', { duration: 4000 });
          this.uploadingPhoto = false;
        },
      });
  }

  private buildTicketRow(id?: number | null, name = '', price = 0, quantity = 50): FormGroup {
    return this.fb.group({
      id:       [id ?? null],
      name:     [name,     [Validators.required, Validators.maxLength(100)]],
      price:    [price,    [Validators.required, Validators.min(0)]],
      quantity: [quantity, [Validators.required, Validators.min(1)]],
    });
  }

  // Truncate ISO-8601 to the "yyyy-MM-ddTHH:mm" format required by datetime-local inputs
  private toDatetimeLocal(iso: string): string { return iso.substring(0, 16); }
}
