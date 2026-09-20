import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

export interface ParamData {
  title: string;
  /** Helper text under the title. */
  hint?: string;
  /** Prefilled value. */
  value?: string;
  /** Placeholder for the empty field. */
  placeholder?: string;
  /** Confirm button label (default "Run"). */
  confirmLabel?: string;
  /**
   * 'json' requires a JSON object (used for a DAG's initial channel state); 'text' accepts any
   * string (a task's initial parameter, plain or JSON). Default 'text'.
   */
  mode?: 'json' | 'text';
}

/**
 * A small dialog to enter a parameter before a manual run / trigger. Closes with the entered string
 * on confirm, or {@code undefined} on cancel. In 'json' mode it blocks confirm until the value parses
 * as a JSON object.
 */
@Component({
  selector: 'cf-param-dialog',
  imports: [FormsModule, MatDialogModule, MatButtonModule, MatIconModule],
  template: `
    <div class="pd">
      <div class="pd-head">
        <mat-icon>tune</mat-icon>
        <h2 mat-dialog-title>{{ data.title }}</h2>
      </div>
      <mat-dialog-content>
        @if (data.hint) { <p class="pd-hint">{{ data.hint }}</p> }
        <textarea class="pd-input mono" rows="7" [(ngModel)]="text"
          [placeholder]="data.placeholder ?? ''" spellcheck="false"></textarea>
        @if (error(); as e) { <p class="pd-error">{{ e }}</p> }
        <button type="button" class="pd-fmt" (click)="format()">
          <mat-icon>data_object</mat-icon> Format JSON
        </button>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-stroked-button (click)="ref.close(undefined)">Cancel</button>
        <button mat-flat-button color="primary" (click)="confirm()" cdkFocusInitial>
          {{ data.confirmLabel ?? 'Run' }}
        </button>
      </mat-dialog-actions>
    </div>
  `,
  styles: [`
    .pd { min-width: 420px; max-width: 560px; }
    .pd-head { display: flex; align-items: center; gap: 0.6rem; padding: 0.25rem 0 0; }
    .pd-head h2 { margin: 0; padding: 0; font-size: 1.1rem; font-weight: 650; color: #0f2c4d; }
    .pd-head mat-icon { color: #1565c0; }
    .pd-hint { margin: 0.25rem 0 0.6rem; color: #3d5372; line-height: 1.45; font-size: 0.88rem; }
    .pd-input { width: 100%; box-sizing: border-box; border: 1px solid #d3e2f5; border-radius: 8px;
      padding: 0.6rem 0.7rem; font-size: 0.85rem; line-height: 1.5; color: #33415a; resize: vertical;
      background: #f8fbff; }
    .pd-input:focus { outline: none; border-color: #1565c0; background: #fff; }
    .pd-error { margin: 0.5rem 0 0; color: #d93025; font-size: 0.82rem; }
    .pd-fmt { margin-top: 0.5rem; display: inline-flex; align-items: center; gap: 0.3rem; border: 0;
      background: 0; cursor: pointer; color: #1565c0; font: inherit; font-size: 0.8rem; padding: 0; }
    .pd-fmt mat-icon { font-size: 18px; width: 18px; height: 18px; }
  `],
})
export class ParamDialog {
  protected readonly data = inject<ParamData>(MAT_DIALOG_DATA);
  protected readonly ref = inject(MatDialogRef<ParamDialog, string | undefined>);
  protected text = this.data.value ?? '';
  protected readonly error = signal('');

  protected format(): void {
    const raw = (this.text ?? '').trim();
    if (!raw) {
      return;
    }
    try {
      this.text = JSON.stringify(JSON.parse(raw), null, 2);
      this.error.set('');
    } catch {
      this.error.set('Not valid JSON — left as is');
    }
  }

  protected confirm(): void {
    const raw = (this.text ?? '').trim();
    if (this.data.mode === 'json') {
      if (!raw) {
        this.ref.close('{}');
        return;
      }
      try {
        const parsed = JSON.parse(raw);
        if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
          this.error.set('Enter a JSON object, e.g. { "input": "..." }');
          return;
        }
      } catch {
        this.error.set('Not valid JSON');
        return;
      }
    }
    this.ref.close(raw);
  }
}
