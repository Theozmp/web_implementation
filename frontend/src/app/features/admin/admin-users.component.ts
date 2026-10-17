import {
  AfterViewInit, Component, DestroyRef, OnInit, ViewChild, inject,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { DatePipe, TitleCasePipe } from '@angular/common';
import { debounceTime, distinctUntilChanged, merge, startWith } from 'rxjs';

import { MatTableModule } from '@angular/material/table';
import { MatPaginator, MatPaginatorModule } from '@angular/material/paginator';
import { MatSort, MatSortModule } from '@angular/material/sort';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';

import { AdminUserService } from '../../core/services/admin-user.service';
import { UserListItem } from '../../core/models/admin-user.model';

@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    DatePipe,
    TitleCasePipe,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
  ],
  templateUrl: './admin-users.component.html',
  styleUrl:    './admin-users.component.scss',
})
export class AdminUsersComponent implements OnInit, AfterViewInit {

  private readonly svc     = inject(AdminUserService);
  private readonly destroy = inject(DestroyRef);

  displayedColumns = ['username', 'name', 'email', 'status', 'createdAt', 'actions'];

  users:         UserListItem[] = [];
  totalElements  = 0;
  loading        = false;

  searchControl = new FormControl('');
  statusControl = new FormControl('');

  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild(MatSort)      sort!: MatSort;

  ngOnInit(): void {
    // initial load before AfterViewInit, when paginator/sort aren't ready yet;
    // loadUsers() handles that with the ?? fallbacks.
    this.loadUsers();
  }

  ngAfterViewInit(): void {
    // When sort changes, reset to first page (setting pageIndex directly
    // does NOT emit a page event, so no double-load occurs)
    this.sort.sortChange
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe(() => { this.paginator.pageIndex = 0; });

    // Reload when sort or paginator changes
    merge(this.sort.sortChange, this.paginator.page)
      .pipe(startWith({}), takeUntilDestroyed(this.destroy))
      .subscribe(() => this.loadUsers());

    // Reload when filters change (search debounced; status immediate)
    merge(
      this.searchControl.valueChanges.pipe(debounceTime(300), distinctUntilChanged()),
      this.statusControl.valueChanges,
    )
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe(() => {
        this.paginator.pageIndex = 0;
        this.loadUsers();
      });
  }

  loadUsers(): void {
    this.loading = true;

    const sortField = this.sort?.active    || 'createdAt';
    const sortDir   = this.sort?.direction || 'desc';

    this.svc.getUsers({
      q:      this.searchControl.value  || undefined,
      status: this.statusControl.value  || undefined,
      page:   this.paginator?.pageIndex ?? 0,
      size:   this.paginator?.pageSize  ?? 20,
      sort:   `${sortField},${sortDir}`,
    }).subscribe({
      next: page => {
        this.users         = page.content;
        this.totalElements = page.totalElements;
        this.loading       = false;
      },
      error: () => { this.loading = false; },
    });
  }
}
