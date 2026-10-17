import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { EventCreatePayload, EventDetail, EventSummary, PageResponse } from '../models/event.model';

@Injectable({ providedIn: 'root' })
export class EventService {

  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/events`;

  searchEvents(params: {
    q?: string;
    category?: string[];
    from?: string;        // ISO date yyyy-MM-dd; service appends T00:00:00
    to?: string;          // ISO date yyyy-MM-dd; service appends T23:59:59
    minPrice?: number | null;
    maxPrice?: number | null;
    city?: string;
    lat?: number | null;
    lng?: number | null;
    radiusKm?: number;
    page: number;
    size: number;
    sort: string;
  }): Observable<PageResponse<EventSummary>> {
    let p = new HttpParams()
      .set('page', params.page)
      .set('size', params.size)
      .set('sort', params.sort);
    if (params.q)        p = p.set('q', params.q);
    if (params.category?.length)
      params.category.forEach(c => (p = p.append('category', c)));
    if (params.from)     p = p.set('from', params.from);
    if (params.to)       p = p.set('to', params.to);
    if (params.minPrice != null) p = p.set('minPrice', params.minPrice);
    if (params.maxPrice != null) p = p.set('maxPrice', params.maxPrice);
    if (params.city)     p = p.set('city', params.city);
    if (params.lat != null && params.lng != null) {
      p = p.set('lat', params.lat).set('lng', params.lng)
           .set('radiusKm', params.radiusKm ?? 50);
    }
    return this.http.get<PageResponse<EventSummary>>(`${this.base}/search`, { params: p });
  }

  getMyEvents(params: {
    status?: string; page: number; size: number; sort: string;
  }): Observable<PageResponse<EventSummary>> {
    let p = new HttpParams()
      .set('page', params.page)
      .set('size', params.size)
      .set('sort', params.sort);
    if (params.status) p = p.set('status', params.status);
    return this.http.get<PageResponse<EventSummary>>(`${this.base}/mine`, { params: p });
  }

  getEvent(id: number): Observable<EventDetail> {
    return this.http.get<EventDetail>(`${this.base}/${id}`);
  }

  createEvent(payload: EventCreatePayload): Observable<EventDetail> {
    return this.http.post<EventDetail>(this.base, payload);
  }

  updateEvent(id: number, payload: EventCreatePayload): Observable<EventDetail> {
    return this.http.put<EventDetail>(`${this.base}/${id}`, payload);
  }

  publishEvent(id: number): Observable<EventDetail> {
    return this.http.post<EventDetail>(`${this.base}/${id}/publish`, {});
  }

  cancelEvent(id: number): Observable<EventDetail> {
    return this.http.post<EventDetail>(`${this.base}/${id}/cancel`, {});
  }

  deleteEvent(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  uploadPhoto(id: number, file: File): Observable<{ id: number; url: string }> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<{ id: number; url: string }>(`${this.base}/${id}/photos`, form);
  }
}
