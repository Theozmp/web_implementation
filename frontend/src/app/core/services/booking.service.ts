import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BookingDto } from '../models/booking.model';
import { PageResponse } from '../models/event.model';

@Injectable({ providedIn: 'root' })
export class BookingService {

  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}`;

  createBooking(
    eventId: number,
    req: { ticketTypeId: number; numberOfTickets: number },
  ): Observable<BookingDto> {
    return this.http.post<BookingDto>(
      `${this.base}/events/${eventId}/bookings`, req,
    );
  }

  getMyBookings(page: number, size: number): Observable<PageResponse<BookingDto>> {
    const params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', 'bookedAt,desc');
    return this.http.get<PageResponse<BookingDto>>(
      `${this.base}/bookings/mine`, { params },
    );
  }
}
