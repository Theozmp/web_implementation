import {
  Component, DestroyRef, inject, OnDestroy, OnInit,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AsyncPipe, CurrencyPipe, DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { switchMap } from 'rxjs/operators';
import * as L from 'leaflet';

import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';
import { MatChipsModule } from '@angular/material/chips';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';

import { EventService } from '../../core/services/event.service';
import { AuthService } from '../../core/services/auth.service';
import { EventDetail } from '../../core/models/event.model';
import { environment } from '../../../environments/environment';
import { BookingDialogComponent } from './booking-dialog.component';
import { ComposeDialogComponent } from '../messages/compose-dialog.component';
import type { BookingDto } from '../../core/models/booking.model';

// Fix marker icon paths: esbuild doesn't resolve Leaflet's internal URL() calls,
// so the default icons 404. Point them at the image files copied to assets/leaflet/
// via the angular.json assets config.
const DefaultIcon = L.icon({
  iconUrl:       'assets/leaflet/marker-icon.png',
  iconRetinaUrl: 'assets/leaflet/marker-icon-2x.png',
  shadowUrl:     'assets/leaflet/marker-shadow.png',
  iconSize:    [25, 41],
  iconAnchor:  [12, 41],
  popupAnchor: [1, -34],
  shadowSize:  [41, 41],
});
L.Marker.prototype.options.icon = DefaultIcon;

@Component({
  selector: 'app-event-detail',
  standalone: true,
  imports: [
    AsyncPipe, CurrencyPipe, DatePipe, RouterLink,
    MatCardModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule,
    MatDividerModule, MatChipsModule, MatTableModule, MatTooltipModule,
  ],
  templateUrl: './event-detail.component.html',
  styleUrl: './event-detail.component.scss',
})
export class EventDetailComponent implements OnInit, OnDestroy {
  private readonly route      = inject(ActivatedRoute);
  private readonly router     = inject(Router);
  private readonly eventSvc   = inject(EventService);
  private readonly dialog     = inject(MatDialog);
  private readonly snackBar   = inject(MatSnackBar);
  readonly auth               = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  readonly apiBase = environment.apiBaseUrl.replace('/api/v1', '');

  event:   EventDetail | null = null;
  loading  = false;
  error    = '';

  // Photo carousel state
  photoIndex = 0;

  // Ticket table columns
  ticketColumns = ['name', 'price', 'available'];

  private eventId: number | null = null;
  private mapInstance: L.Map | null = null;

  ngOnInit(): void {
    this.route.paramMap
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        switchMap(params => {
          this.eventId = +params.get('id')!;
          this.loading = true;
          this.error   = '';
          this.event   = null;
          this.photoIndex = 0;
          return this.eventSvc.getEvent(this.eventId);
        }),
      )
      .subscribe({
        next: ev => {
          this.loading = false;
          this.event   = ev;
          if (ev.latitude != null && ev.longitude != null) {
            // Defer until Angular has painted the @if block that holds #event-map,
            // and the div has a real size, before attaching Leaflet (see initMap).
            this.scheduleMap(+ev.latitude!, +ev.longitude!, ev.venueName ?? ev.title);
          }
        },
        error: () => {
          this.loading = false;
          this.error   = 'Event not found or no longer available.';
        },
      });
  }

  ngOnDestroy(): void {
    this.mapInstance?.remove();
    this.mapInstance = null;
  }

  // Photo carousel

  get currentPhoto(): string {
    return this.event?.photoUrls[this.photoIndex] ?? '';
  }

  nextPhoto(): void {
    const len = this.event?.photoUrls.length ?? 1;
    this.photoIndex = (this.photoIndex + 1) % len;
  }

  prevPhoto(): void {
    const len = this.event?.photoUrls.length ?? 1;
    this.photoIndex = (this.photoIndex - 1 + len) % len;
  }

  // Ticket availability

  // quantity on TicketTypeItem is already the *remaining* count (decremented on booking)
  available(qty: number): number {
    return Math.max(0, qty);
  }

  // Booking

  openBookingDialog(): void {
    if (!this.event) return;
    if (!this.auth.currentUser) {
      this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
      return;
    }
    this.dialog.open(BookingDialogComponent, {
      data: { event: this.event },
      width: '480px',
    }).afterClosed().subscribe((result: BookingDto | undefined) => {
      if (result) {
        this.snackBar.open(
          `Booking confirmed! ${result.numberOfTickets}× ${result.ticketTypeName}`,
          'Close',
          { duration: 4000 },
        );
        this.reloadEvent();
      }
    });
  }

  private reloadEvent(): void {
    if (this.eventId === null) return;
    this.eventSvc.getEvent(this.eventId).subscribe({
      next: ev => { this.event = ev; },
    });
  }

  // Messaging

  messageOrganizer(): void {
    if (!this.event) return;
    if (!this.auth.currentUser) {
      this.router.navigate(['/login'], { queryParams: { returnUrl: this.router.url } });
      return;
    }
    this.dialog.open(ComposeDialogComponent, {
      data: {
        recipientId:       this.event.organizerId,
        recipientUsername: this.event.organizerUsername,
        eventId:           this.event.id,
        eventTitle:        this.event.title,
      },
      width: '540px',
    }).afterClosed().subscribe(result => {
      if (result) this.snackBar.open('Message sent to organizer.', 'Close', { duration: 3000 });
    });
  }

  // Map

  /**
   * Polls for a few animation frames until the #event-map div exists and has a
   * non-zero size, then initialises Leaflet.
   *
   * Needed because the map lives inside an @if (event) block, so the div isn't in
   * the DOM when the HTTP response arrives. A plain setTimeout(0) often fires before
   * the block is painted (container is null, map skipped) or before the div has its
   * CSS height (Leaflet captures 0px, giving a blank/grey map).
   */
  private scheduleMap(lat: number, lng: number, label: string, attempt = 0): void {
    const container = document.getElementById('event-map');
    if (container && container.clientHeight > 0 && container.clientWidth > 0) {
      this.initMap(container, lat, lng, label);
      return;
    }
    if (attempt < 30) {
      requestAnimationFrame(() => this.scheduleMap(lat, lng, label, attempt + 1));
    }
  }

  private initMap(container: HTMLElement, lat: number, lng: number, label: string): void {
    if (this.mapInstance) {
      this.mapInstance.remove();
      this.mapInstance = null;
    }

    this.mapInstance = L.map(container).setView([lat, lng], 14);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      // OSM attribution is mandatory under the ODbL license.
      attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
      maxZoom: 19,
    }).addTo(this.mapInstance);

    L.marker([lat, lng])
      .addTo(this.mapInstance)
      .bindPopup(`<strong>${label}</strong>`)
      .openPopup();

    // Leaflet measures the container on creation; if it was mid-layout the tile
    // grid can render blank until a resize. Recompute once on the next frame.
    requestAnimationFrame(() => this.mapInstance?.invalidateSize());
  }

}
