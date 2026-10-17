import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import type { MessageCreateRequest, MessageDto, MessageSummaryDto, UserSearchResult } from '../models/message.model';
import type { PageResponse } from '../models/event.model';

@Injectable({ providedIn: 'root' })
export class MessageService {

  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/messages`;

  send(req: MessageCreateRequest): Observable<MessageDto> {
    return this.http.post<MessageDto>(this.base, req);
  }

  getInbox(page: number, size: number): Observable<PageResponse<MessageSummaryDto>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<MessageSummaryDto>>(`${this.base}/inbox`, { params });
  }

  getSent(page: number, size: number): Observable<PageResponse<MessageSummaryDto>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<MessageSummaryDto>>(`${this.base}/sent`, { params });
  }

  getMessage(id: number): Observable<MessageDto> {
    return this.http.get<MessageDto>(`${this.base}/${id}`);
  }

  deleteMessage(id: number, box: 'inbox' | 'sent'): Observable<void> {
    const params = new HttpParams().set('box', box);
    return this.http.delete<void>(`${this.base}/${id}`, { params });
  }

  getUnreadCount(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.base}/unread-count`);
  }

  searchRecipients(q: string): Observable<UserSearchResult[]> {
    const params = new HttpParams().set('q', q);
    return this.http.get<UserSearchResult[]>(`${this.base}/recipients/search`, { params });
  }
}
