import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { EventSummary } from '../models/event.model';

@Injectable({ providedIn: 'root' })
export class RecommendationService {
  private readonly http = inject(HttpClient);

  /**
   * Returns the top-N recommended PUBLISHED upcoming events for the current
   * authenticated user, ranked by BMF predicted score.
   * Falls back to popularity ranking for cold-start users.
   */
  getRecommendations(limit = 5): Observable<EventSummary[]> {
    return this.http.get<EventSummary[]>(`${environment.apiBaseUrl}/recommendations`, {
      params: { limit: limit.toString() },
    });
  }
}
