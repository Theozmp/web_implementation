import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PageResponse, UserDetail, UserListItem } from '../models/admin-user.model';

@Injectable({ providedIn: 'root' })
export class AdminUserService {

  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/admin/users`;

  getUsers(params: {
    q?: string;
    status?: string;
    page: number;
    size: number;
    sort: string;
  }): Observable<PageResponse<UserListItem>> {
    let p = new HttpParams()
      .set('page', params.page)
      .set('size', params.size)
      .set('sort', params.sort);
    if (params.q)      p = p.set('q',      params.q);
    if (params.status) p = p.set('status', params.status);
    return this.http.get<PageResponse<UserListItem>>(this.base, { params: p });
  }

  getUser(id: number): Observable<UserDetail> {
    return this.http.get<UserDetail>(`${this.base}/${id}`);
  }

  approveUser(id: number): Observable<UserDetail> {
    return this.http.post<UserDetail>(`${this.base}/${id}/approve`, {});
  }

  rejectUser(id: number, reason?: string): Observable<UserDetail> {
    return this.http.post<UserDetail>(`${this.base}/${id}/reject`,
      reason ? { reason } : {});
  }
}
