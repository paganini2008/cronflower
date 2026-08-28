import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

export interface ConfirmData {
  title: string;
  message: string;
  /** Extra emphasis line, shown muted under the message (e.g. what cannot be undone). */
  note?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  icon?: string;
  /** Style the confirm button as a destructive action (red). */
  danger?: boolean;
}

/** A small reusable confirmation dialog. Closes with `true` when the user confirms. */
@Component({
  selector: 'cf-confirm-dialog',
  imports: [MatDialogModule, MatButtonModule, MatIconModule],
  template: `
    <div class="cd">
      <div class="cd-head" [class.danger]="data.danger">
        <mat-icon>{{ data.icon ?? (data.danger ? 'warning' : 'help') }}</mat-icon>
        <h2 mat-dialog-title>{{ data.title }}</h2>
      </div>
      <mat-dialog-content>
        <p class="cd-msg">{{ data.message }}</p>
        @if (data.note) { <p class="cd-note">{{ data.note }}</p> }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-stroked-button [mat-dialog-close]="false">
          {{ data.cancelLabel ?? 'Cancel' }}
        </button>
        <button mat-flat-button [class.danger-btn]="data.danger" [color]="data.danger ? undefined : 'primary'"
          [mat-dialog-close]="true" cdkFocusInitial>
          {{ data.confirmLabel ?? 'Confirm' }}
        </button>
      </mat-dialog-actions>
    </div>
  `,
  styles: [`
    .cd { min-width: 340px; max-width: 440px; }
    .cd-head { display: flex; align-items: center; gap: 0.6rem; padding: 0.25rem 0 0; }
    .cd-head h2 { margin: 0; padding: 0; font-size: 1.1rem; font-weight: 650; color: #0f2c4d; }
    .cd-head mat-icon { color: #1565c0; }
    .cd-head.danger mat-icon { color: #d93025; }
    .cd-msg { margin: 0.25rem 0 0; color: #3d5372; line-height: 1.5; }
    .cd-note { margin: 0.6rem 0 0; font-size: 0.85rem; color: #7a8aa0; line-height: 1.45; }
    .danger-btn { --mdc-filled-button-container-color: #d93025; --mdc-filled-button-label-text-color: #fff; }
  `],
})
export class ConfirmDialog {
  protected readonly data = inject<ConfirmData>(MAT_DIALOG_DATA);
  protected readonly ref = inject(MatDialogRef<ConfirmDialog>);
}
