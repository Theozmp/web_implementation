import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AsyncPipe, DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { EMPTY } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';

import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatSliderModule } from '@angular/material/slider';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatCheckboxModule } from '@angular/material/checkbox';

import { EventService } from '../../core/services/event.service';
import { CategoryService } from '../../core/services/category.service';
import { AuthService } from '../../core/services/auth.service';
import { EventSummary } from '../../core/models/event.model';
import { RecommendedEventsComponent } from '../recommendations/recommended-events.component';

@Component({
  selector: 'app-events',
  standalone: true,
  imports: [
    AsyncPipe, DatePipe, DecimalPipe, RouterLink, FormsModule,
    MatCardModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatIconModule, MatSelectModule, MatSliderModule,
    MatPaginatorModule, MatProgressSpinnerModule,
    MatChipsModule, MatDividerModule, MatTooltipModule, MatCheckboxModule,
    RecommendedEventsComponent,
  ],
  templateUrl: './events.component.html',
  styleUrl: './events.component.scss',
})
export class EventsComponent implements OnInit {
  private readonly route      = inject(ActivatedRoute);
  private readonly router     = inject(Router);
  private readonly eventSvc   = inject(EventService);
  private readonly catSvc     = inject(CategoryService);
  private readonly destroyRef = inject(DestroyRef);
  readonly auth               = inject(AuthService);

  readonly categories$ = this.catSvc.categories$;

  // Filter state (read from / written to URL query params)
  q               = '';
  selectedCats: string[] = [];
  from            = '';
  to              = '';
  minPrice: number | null = null;
  maxPrice: number | null = null;
  city            = '';
  useGeo          = false;
  lat:      number | null = null;
  lng:      number | null = null;
  radiusKm        = 50;
  geoError        = '';

  // Pagination
  page      = 0;
  pageSize  = 12;
  total     = 0;

  // Results
  events:  EventSummary[] = [];
  loading  = false;

  ngOnInit(): void {
    // Drive the entire search from URL query params so the page is bookmarkable.
    // When the user changes any filter, applyFilters() updates the URL, which
    // triggers this stream and re-runs the search.
    this.route.queryParamMap
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        switchMap(params => {
          this.q            = params.get('q')       ?? '';
          this.selectedCats = params.getAll('category');
          this.from         = params.get('from')    ?? '';
          this.to           = params.get('to')      ?? '';
          this.minPrice     = params.has('minPrice') ? +params.get('minPrice')! : null;
          this.maxPrice     = params.has('maxPrice') ? +params.get('maxPrice')! : null;
          this.city         = params.get('city')    ?? '';
          this.lat          = params.has('lat')  ? +params.get('lat')!  : null;
          this.lng          = params.has('lng')  ? +params.get('lng')!  : null;
          this.radiusKm     = params.has('radiusKm') ? +params.get('radiusKm')! : 50;
          this.useGeo       = this.lat != null && this.lng != null;
          this.page         = params.has('page') ? +params.get('page')! : 0;
          this.loading      = true;

          return this.eventSvc.searchEvents({
            q:          this.q || undefined,
            category:   this.selectedCats.length ? this.selectedCats : undefined,
            from:       this.from || undefined,
            to:         this.to   || undefined,
            minPrice:   this.minPrice,
            maxPrice:   this.maxPrice,
            city:       this.city || undefined,
            lat:        this.useGeo ? this.lat : null,
            lng:        this.useGeo ? this.lng : null,
            radiusKm:   this.useGeo ? this.radiusKm : undefined,
            page:       this.page,
            size:       this.pageSize,
            sort:       'startDatetime,asc',
          }).pipe(catchError(() => { this.loading = false; return EMPTY; }));
        }),
      )
      .subscribe(page => {
        this.loading = false;
        this.events  = page.content;
        this.total   = page.totalElements;
      });
  }

  applyFilters(): void {
    this.router.navigate(['/events'], {
      queryParams: {
        q:        this.q        || undefined,
        category: this.selectedCats.length ? this.selectedCats : undefined,
        from:     this.from     || undefined,
        to:       this.to       || undefined,
        minPrice: this.minPrice ?? undefined,
        maxPrice: this.maxPrice ?? undefined,
        city:     this.city     || undefined,
        lat:      (this.useGeo && this.lat != null) ? this.lat  : undefined,
        lng:      (this.useGeo && this.lng != null) ? this.lng  : undefined,
        radiusKm: this.useGeo ? this.radiusKm : undefined,
        page:     0,
      },
    });
  }

  clearFilters(): void {
    this.q = ''; this.selectedCats = []; this.from = ''; this.to = '';
    this.minPrice = null; this.maxPrice = null; this.city = '';
    this.useGeo = false; this.lat = null; this.lng = null; this.radiusKm = 50;
    this.geoError = '';
    this.router.navigate(['/events']);
  }

  onPageChange(e: PageEvent): void {
    this.pageSize = e.pageSize;
    this.router.navigate(['/events'], {
      queryParamsHandling: 'merge',
      queryParams: { page: e.pageIndex, size: e.pageSize },
    });
  }

  locateMe(): void {
    if (!navigator.geolocation) {
      this.geoError = 'Geolocation is not supported by your browser.';
      return;
    }
    this.geoError = '';
    navigator.geolocation.getCurrentPosition(
      pos => {
        this.lat    = +pos.coords.latitude.toFixed(6);
        this.lng    = +pos.coords.longitude.toFixed(6);
        this.useGeo = true;
      },
      () => { this.geoError = 'Location access denied.'; this.useGeo = false; },
    );
  }

  priceRange(_event: EventSummary): string {
    // placeholder until EventSummaryDto carries minPrice/maxPrice.
    return '';
  }
}
