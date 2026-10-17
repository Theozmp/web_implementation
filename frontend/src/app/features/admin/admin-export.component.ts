import { Component, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-admin-export',
  standalone: true,
  imports: [MatButtonModule, MatCardModule, MatIconModule, MatSnackBarModule],
  template: `
    <div class="export-page">
      <mat-card>
        <mat-card-header>
          <mat-card-title>Export Events</mat-card-title>
          <mat-card-subtitle>Download all events in XML or JSON format</mat-card-subtitle>
        </mat-card-header>

        <mat-card-content>
          <p>
            The XML export validates against the <code>events.dtd</code> schema.
            The JSON export mirrors the same structure.
          </p>

          <div class="button-row">
            <button mat-raised-button color="primary"
                    [disabled]="downloading"
                    (click)="download('xml')">
              <mat-icon>download</mat-icon>
              Download XML
            </button>

            <button mat-raised-button color="accent"
                    [disabled]="downloading"
                    (click)="download('json')">
              <mat-icon>download</mat-icon>
              Download JSON
            </button>
          </div>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .export-page {
      max-width: 600px;
      margin: 2rem auto;
      padding: 0 1rem;
    }
    mat-card-content { padding-top: 1rem; }
    p { margin-bottom: 1.5rem; color: var(--mat-sys-on-surface-variant, #666); }
    code { background: #f5f5f5; padding: 2px 4px; border-radius: 3px; font-size: 0.9em; }
    .button-row { display: flex; gap: 1rem; flex-wrap: wrap; }
  `],
})
export class AdminExportComponent {
  private readonly http    = inject(HttpClient);
  private readonly snackBar = inject(MatSnackBar);

  downloading = false;

  download(format: 'xml' | 'json'): void {
    this.downloading = true;
    const url = `${environment.apiBaseUrl}/admin/export/events.${format}`;
    const mime = format === 'xml' ? 'application/xml' : 'application/json';

    this.http.get(url, { responseType: 'blob' }).subscribe({
      next: blob => {
        const objectUrl = URL.createObjectURL(new Blob([blob], { type: mime }));
        const anchor    = document.createElement('a');
        anchor.href     = objectUrl;
        anchor.download  = `events.${format}`;
        anchor.click();
        URL.revokeObjectURL(objectUrl);
        this.downloading = false;
      },
      error: () => {
        this.snackBar.open('Export failed. Please try again.', 'Close', { duration: 4000 });
        this.downloading = false;
      },
    });
  }
}
