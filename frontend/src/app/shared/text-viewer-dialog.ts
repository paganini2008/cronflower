import { Component, inject, signal } from '@angular/core';
import { MatDialogModule, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { prettyJson, highlightJson } from '../core/util';

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
      @if (isJson) { <span class="json-tag">JSON</span> }
    </h2>
    <mat-dialog-content>
      @if (isJson) {
        <pre class="viewer json" [innerHTML]="html"></pre>
      } @else {
        <pre class="viewer" [class.err]="data.tone === 'error'">{{ shown }}</pre>
      }
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
    .json-tag { margin-left: 0.5rem; padding: 0.05rem 0.4rem; border-radius: 6px; font-size: 0.62rem;
      font-weight: 700; letter-spacing: 0.03em; vertical-align: 2px; color: #8fb8ff;
      background: rgba(143, 184, 255, 0.15); border: 1px solid rgba(143, 184, 255, 0.35); }
    /* JSON token colours — tuned for the dark viewer, legible in both app themes. */
    .viewer.json ::ng-deep .jt-key { color: #8fb8ff; }
    .viewer.json ::ng-deep .jt-str { color: #9fe0a0; }
    .viewer.json ::ng-deep .jt-num { color: #f3b06a; }
    .viewer.json ::ng-deep .jt-bool { color: #d4a7f5; font-weight: 600; }
    .viewer.json ::ng-deep .jt-null { color: #ff9e9e; font-style: italic; }
  `],
})
export class TextViewerDialog {
  protected readonly data = inject<TextViewerData>(MAT_DIALOG_DATA);
  protected readonly copied = signal(false);
  protected readonly isJson: boolean;
  protected readonly shown: string;
  protected readonly html: string;

  constructor() {
    const p = prettyJson(this.data.text ?? '');
    // Never syntax-highlight an error payload — keep it raw and red.
    this.isJson = p.isJson && this.data.tone !== 'error';
    this.shown = this.isJson ? p.text : (this.data.text ?? '');
    this.html = this.isJson ? highlightJson(p.text) : '';
  }

  copy(): void {
    navigator.clipboard?.writeText(this.shown).then(() => {
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 1500);
    }).catch(() => { /* clipboard unavailable */ });
  }
}
