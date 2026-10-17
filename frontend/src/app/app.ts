import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { AsyncPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { timer } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { of } from 'rxjs';

import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatBadgeModule } from '@angular/material/badge';

import { AuthService } from './core/services/auth.service';
import { MessageService } from './core/services/message.service';

@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive,
    AsyncPipe,
    MatToolbarModule, MatButtonModule, MatIconModule, MatBadgeModule,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App implements OnInit {
  readonly auth               = inject(AuthService);
  private readonly messageSvc = inject(MessageService);
  private readonly destroyRef = inject(DestroyRef);

  unreadCount = 0;

  ngOnInit(): void {
    // While the user is logged in, poll for unread messages every 30 seconds.
    // Resets to 0 immediately on logout (switchMap cancels the inner observable).
    this.auth.currentUser$
      .pipe(
        switchMap(user => {
          if (!user) return of(0);
          // timer(0, 30_000): fires immediately then every 30 seconds
          return timer(0, 30_000).pipe(
            switchMap(() =>
              this.messageSvc.getUnreadCount().pipe(
                map(r => r.count),
                catchError(() => of(0)),  // on network error keep the badge at 0
              )
            ),
          );
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(count => { this.unreadCount = count; });
  }
}
