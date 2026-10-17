import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, shareReplay } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Category } from '../models/event.model';

@Injectable({ providedIn: 'root' })
export class CategoryService {

  private readonly http = inject(HttpClient);

  /** Cache the category list for the app's lifetime; it's static reference data. */
  readonly categories$: Observable<Category[]> = this.http
    .get<Category[]>(`${environment.apiBaseUrl}/categories`)
    .pipe(shareReplay(1));
}
