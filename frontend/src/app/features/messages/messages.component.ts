import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';

import { MatTabsModule } from '@angular/material/tabs';
import { MatListModule } from '@angular/material/list';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';
import { MatCardModule } from '@angular/material/card';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';

import { MessageService } from '../../core/services/message.service';
import type { MessageDto, MessageSummaryDto } from '../../core/models/message.model';
import { ComposeDialogComponent } from './compose-dialog.component';

@Component({
  selector: 'app-messages',
  standalone: true,
  imports: [
    DatePipe, RouterLink,
    MatTabsModule, MatListModule, MatPaginatorModule, MatButtonModule,
    MatIconModule, MatProgressSpinnerModule, MatDividerModule,
    MatCardModule, MatTooltipModule,
  ],
  templateUrl: './messages.component.html',
  styleUrl: './messages.component.scss',
})
export class MessagesComponent implements OnInit {
  private readonly svc        = inject(MessageService);
  private readonly dialog     = inject(MatDialog);
  private readonly snackBar   = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  activeTab: 'inbox' | 'sent' = 'inbox';
  loading   = false;

  messages:      MessageSummaryDto[] = [];
  totalElements  = 0;
  page           = 0;
  readonly size  = 20;

  selected: MessageDto | null = null;
  loadingDetail = false;

  ngOnInit(): void {
    this.load();
  }

  onTabChange(index: number): void {
    this.activeTab = index === 0 ? 'inbox' : 'sent';
    this.page      = 0;
    this.selected  = null;
    this.load();
  }

  onPage(e: PageEvent): void {
    this.page = e.pageIndex;
    this.load();
  }

  load(): void {
    this.loading = true;
    const obs = this.activeTab === 'inbox'
      ? this.svc.getInbox(this.page, this.size)
      : this.svc.getSent(this.page, this.size);

    obs.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: p => {
        this.loading       = false;
        this.messages      = p.content;
        this.totalElements = p.totalElements;
      },
      error: () => { this.loading = false; },
    });
  }

  openMessage(summary: MessageSummaryDto): void {
    this.loadingDetail = true;
    this.svc.getMessage(summary.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: msg => {
          this.loadingDetail = false;
          this.selected = msg;
          // Mark as read in the list so the badge updates immediately
          const item = this.messages.find(m => m.id === summary.id);
          if (item) item.read = true;
        },
        error: () => { this.loadingDetail = false; },
      });
  }

  deleteSelected(): void {
    if (!this.selected) return;
    this.svc.deleteMessage(this.selected.id, this.activeTab)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.selected = null;
          this.load();
          this.snackBar.open('Message deleted.', 'Close', { duration: 2500 });
        },
      });
  }

  // Compose

  openCompose(prefill?: { recipientId?: number; recipientUsername?: string; eventId?: number | null; eventTitle?: string | null }): void {
    this.dialog.open(ComposeDialogComponent, {
      data: prefill ?? {},
      width: '540px',
    }).afterClosed().subscribe(result => {
      if (result) {
        this.snackBar.open('Message sent.', 'Close', { duration: 2500 });
        if (this.activeTab === 'sent') this.load();
      }
    });
  }

  // Reply: sender becomes the recipient
  replyTo(msg: MessageDto): void {
    this.openCompose({
      recipientId:       msg.senderId,
      recipientUsername: msg.senderUsername,
      eventId:           msg.eventId,
      eventTitle:        msg.eventTitle,
    });
  }
}
