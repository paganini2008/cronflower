import { Component, inject, signal } from '@angular/core';
import { MatDialogModule, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

export interface TextViewerData {
  title: string;
  text: string;
  tone?: 'error' | 'default';
}

@Component({
  selector: 'cf-text-viewer',
  imports: [MatDialogModule, MatButtonModule, MatIconModule],
  template: `
    <h2 mat-dialog-title>
      @if (data.tone === 'error') { <mat-icon class="err">error</mat-icon> }
      {{ data.title }}
    </h2>
    <mat-dialog-content>
      <pre class="viewer" [class.err]="data.tone === 'error'">{{ data.text }}</pre>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-stroked-button (click)="copy()">
        <mat-icon>{{ copied() ? 'check' : 'content_copy' }}</mat-icon> {{ copied() ? 'Copied' : 'Copy' }}
      </button>
      <button mat-flat-button color="primary" mat-dialog-close>Close</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .viewer { margin: 0; padding: 1rem; background: #0f2c4d; color: #e6edf6; border-radius: 8px;
      font-family: 'JetBrains Mono', ui-monospace, monospace; font-size: 0.82rem; line-height: 1.5;
      white-space: pre-wrap; word-break: break-word; max-height: 60vh; overflow: auto;
      min-width: 460px; max-width: 720px; }
    .viewer.err { background: #3b1210; color: #ffd9d4; }
    h2 mat-icon.err { color: #d93025; vertical-align: middle; }
  `],
})
export class TextViewerDialog {
  protected readonly data = inject<TextViewerData>(MAT_DIALOG_DATA);
  protected readonly copied = signal(false);

  copy(): void {
    navigator.clipboard?.writeText(this.data.text).then(() => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 1500);
    }).catch(() => { /* clipboard unavailable */ });
  }
}
