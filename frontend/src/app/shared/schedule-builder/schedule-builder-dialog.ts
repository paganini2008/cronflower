import { Component, inject, signal } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatTabsModule } from '@angular/material/tabs';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { CronsmithApi } from '../../core/api.service';
import { fmt } from '../../core/util';
import { buildCron, CronSpec, describeIso, describeSpec, Freq } from './cron';

@Component({
  selector: 'cf-schedule-builder',
  imports: [
    NgTemplateOutlet, FormsModule, MatDialogModule, MatTabsModule, MatButtonModule,
    MatButtonToggleModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatIconModule,
    MatProgressSpinnerModule,
  ],
  template: `
    <h2 mat-dialog-title>Build schedule</h2>
    <mat-dialog-content class="builder">
      <mat-tab-group [(selectedIndex)]="tabIndex" (selectedIndexChange)="resetTest()" animationDuration="150ms">
        <!-- CRON -->
        <mat-tab label="Cron">
          <div class="tab-body">
            <mat-form-field appearance="outline" class="w-full" subscriptSizing="dynamic">
              <mat-label>Frequency</mat-label>
              <mat-select [(ngModel)]="freq" (ngModelChange)="resetTest()">
                <mat-option value="seconds">Every N seconds</mat-option>
                <mat-option value="minutes">Every N minutes</mat-option>
                <mat-option value="hourly">Hourly</mat-option>
                <mat-option value="daily">Daily</mat-option>
                <mat-option value="weekly">Weekly</mat-option>
                <mat-option value="monthly">Monthly</mat-option>
                <mat-option value="yearly">Yearly</mat-option>
                <mat-option value="advanced">Advanced (raw fields)</mat-option>
              </mat-select>
            </mat-form-field>

            @switch (freq) {
              @case ('seconds') {
                <div class="row"><span>Every</span>{{ '' }}
                  <mat-form-field appearance="outline" class="num" subscriptSizing="dynamic">
                    <input matInput type="number" min="1" max="59" [(ngModel)]="everyN" /></mat-form-field>
                  <span>second(s)</span>
                </div>
              }
              @case ('minutes') {
                <div class="row"><span>Every</span>
                  <mat-form-field appearance="outline" class="num" subscriptSizing="dynamic">
                    <input matInput type="number" min="1" max="59" [(ngModel)]="everyN" /></mat-form-field>
                  <span>minute(s)</span>
                </div>
              }
              @case ('hourly') {
                <div class="row"><span>Every</span>
                  <mat-form-field appearance="outline" class="num" subscriptSizing="dynamic">
                    <input matInput type="number" min="1" max="23" [(ngModel)]="everyN" /></mat-form-field>
                  <span>hour(s) at minute</span>
                  <mat-form-field appearance="outline" class="num" subscriptSizing="dynamic">
                    <input matInput type="number" min="0" max="59" [(ngModel)]="minute" /></mat-form-field>
                </div>
              }
              @case ('daily') {
                <div class="row"><span>Every</span>
                  <mat-form-field appearance="outline" class="num" subscriptSizing="dynamic">
                    <input matInput type="number" min="1" max="31" [(ngModel)]="everyN" /></mat-form-field>
                  <span>day(s)</span>
                </div>
                <ng-container *ngTemplateOutlet="timePicker" />
              }
              @case ('weekly') {
                <mat-button-toggle-group multiple [(ngModel)]="days" class="wd-group">
                  @for (d of weekdays; track d.v) { <mat-button-toggle [value]="d.v">{{ d.l }}</mat-button-toggle> }
                </mat-button-toggle-group>
                <ng-container *ngTemplateOutlet="timePicker" />
              }
              @case ('monthly') {
                <div class="row"><span>On day</span>
                  <mat-form-field appearance="outline" class="num" subscriptSizing="dynamic">
                    <input matInput type="number" min="1" max="31" [(ngModel)]="dayOfMonth" /></mat-form-field>
                  <span>of the month</span>
                </div>
                <ng-container *ngTemplateOutlet="timePicker" />
              }
              @case ('yearly') {
                <div class="row">
                  <mat-form-field appearance="outline" class="w-28" subscriptSizing="dynamic">
                    <mat-label>Month</mat-label>
                    <mat-select [(ngModel)]="month">
                      @for (m of months; track m) { <mat-option [value]="m">{{ m }}</mat-option> }
                    </mat-select>
                  </mat-form-field>
                  <span>day</span>
                  <mat-form-field appearance="outline" class="num" subscriptSizing="dynamic">
                    <input matInput type="number" min="1" max="31" [(ngModel)]="dayOfMonth" /></mat-form-field>
                </div>
                <ng-container *ngTemplateOutlet="timePicker" />
              }
              @case ('advanced') {
                <p class="hint">second · minute · hour · day-of-month · month · day-of-week</p>
                <div class="adv">
                  <mat-form-field appearance="outline" subscriptSizing="dynamic"><mat-label>Second</mat-label><input matInput [(ngModel)]="adv.second" /></mat-form-field>
                  <mat-form-field appearance="outline" subscriptSizing="dynamic"><mat-label>Minute</mat-label><input matInput [(ngModel)]="adv.minute" /></mat-form-field>
                  <mat-form-field appearance="outline" subscriptSizing="dynamic"><mat-label>Hour</mat-label><input matInput [(ngModel)]="adv.hour" /></mat-form-field>
                  <mat-form-field appearance="outline" subscriptSizing="dynamic"><mat-label>Day (month)</mat-label><input matInput [(ngModel)]="adv.dom" /></mat-form-field>
                  <mat-form-field appearance="outline" subscriptSizing="dynamic"><mat-label>Month</mat-label><input matInput [(ngModel)]="adv.mon" /></mat-form-field>
                  <mat-form-field appearance="outline" subscriptSizing="dynamic"><mat-label>Day (week)</mat-label><input matInput [(ngModel)]="adv.dow" /></mat-form-field>
                </div>
              }
            }
          </div>
        </mat-tab>

        <!-- INTERVAL -->
        <mat-tab label="Interval">
          <div class="tab-body">
            <p class="hint">A fixed period between runs, from when the task is saved.</p>
            <div class="row"><span>Every</span>
              <mat-form-field appearance="outline" class="num" subscriptSizing="dynamic">
                <input matInput type="number" min="1" [(ngModel)]="intervalN" /></mat-form-field>
              <mat-form-field appearance="outline" class="w-32" subscriptSizing="dynamic">
                <mat-select [(ngModel)]="intervalUnit">
                  <mat-option value="S">second(s)</mat-option>
                  <mat-option value="M">minute(s)</mat-option>
                  <mat-option value="H">hour(s)</mat-option>
                  <mat-option value="D">day(s)</mat-option>
                </mat-select>
              </mat-form-field>
            </div>
          </div>
        </mat-tab>

        <!-- ISO -->
        <mat-tab label="ISO Duration">
          <div class="tab-body">
            <p class="hint">ISO-8601 duration, e.g. <code>PT1H30M</code>, <code>PT45S</code>, <code>P1DT6H</code>.</p>
            <mat-form-field appearance="outline" class="w-full" subscriptSizing="dynamic">
              <mat-label>ISO duration</mat-label>
              <input matInput [(ngModel)]="iso" placeholder="PT1H30M" />
            </mat-form-field>
            @if (iso && !isoValid) { <p class="err">Not a valid ISO-8601 duration.</p> }
          </div>
        </mat-tab>
      </mat-tab-group>

      <div class="preview">
        <div class="pv-top">
          <div>
            <div class="pv-label">Expression</div>
            <div class="pv-expr mono">{{ expression || '—' }}</div>
            <div class="pv-desc">{{ description }}</div>
          </div>
          <button mat-stroked-button (click)="test()" [disabled]="!expression || testing()">
            @if (testing()) { <mat-spinner diameter="16" /> } @else { <mat-icon>science</mat-icon> } Test
          </button>
        </div>
        @if (testError()) { <p class="err">{{ testError() }}</p> }
        @if (testTimes().length) {
          <div class="pv-times">
            <div class="pv-label">Next fire times</div>
            <ol>@for (t of testTimes(); track t) { <li class="mono">{{ fmt(t) }}</li> }</ol>
          </div>
        }
      </div>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <button mat-stroked-button mat-dialog-close>Cancel</button>
      <button mat-flat-button color="primary" [disabled]="!expression" (click)="apply()">
        <mat-icon>check</mat-icon> Use this
      </button>
    </mat-dialog-actions>

    <ng-template #timePicker>
      <div class="row"><span>at</span>
        <mat-form-field appearance="outline" class="num" subscriptSizing="dynamic"><mat-label>H</mat-label><input matInput type="number" min="0" max="23" [(ngModel)]="hour" /></mat-form-field>
        <span>:</span>
        <mat-form-field appearance="outline" class="num" subscriptSizing="dynamic"><mat-label>M</mat-label><input matInput type="number" min="0" max="59" [(ngModel)]="minute" /></mat-form-field>
        <span>:</span>
        <mat-form-field appearance="outline" class="num" subscriptSizing="dynamic"><mat-label>S</mat-label><input matInput type="number" min="0" max="59" [(ngModel)]="second" /></mat-form-field>
      </div>
    </ng-template>
  `,
  styles: [`
    .builder { min-width: 480px; max-width: 580px; }
    .tab-body { padding: 1.25rem 0.25rem 0.5rem; display: flex; flex-direction: column; gap: 0.75rem; }
    .row { display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; }
    .row span { color: #475569; }
    .w-full { width: 100%; } .w-28 { width: 7rem; } .w-32 { width: 8rem; }
    .num { width: 5rem; }
    .wd-group { flex-wrap: wrap; }
    .adv { display: grid; grid-template-columns: repeat(3, 1fr); gap: 0.5rem; }
    .hint { color: #7a8aa0; font-size: 0.82rem; margin: 0; }
    code { background: #eef2f7; padding: 0 0.3rem; border-radius: 4px; }
    .err { color: #d93025; font-size: 0.82rem; margin: 0.25rem 0 0; }
    .preview { margin-top: 0.5rem; padding: 0.9rem 1rem; background: #f0f6ff; border: 1px solid #d6e6fb; border-radius: 10px; }
    .pv-top { display: flex; justify-content: space-between; align-items: flex-start; gap: 1rem; }
    .pv-label { font-size: 0.72rem; color: #5b6b7f; text-transform: uppercase; letter-spacing: .4px; }
    .pv-expr { font-size: 1.1rem; font-weight: 600; color: #0f2c4d; margin: 0.2rem 0; word-break: break-all; }
    .pv-desc { font-size: 0.85rem; color: #475569; }
    .pv-times ol { margin: 0.35rem 0 0; padding-left: 1.25rem; }
    .pv-times li { color: #0f2c4d; padding: 0.1rem 0; }
    button mat-spinner { display: inline-block; margin-right: 0.25rem; }
  `],
})
export class ScheduleBuilderDialog {
  private readonly ref = inject(MatDialogRef<ScheduleBuilderDialog>);
  private readonly api = inject(CronsmithApi);
  private readonly data = inject<{ value?: string }>(MAT_DIALOG_DATA, { optional: true });

  protected readonly weekdays = [
    { v: 'MON', l: 'Mon' }, { v: 'TUE', l: 'Tue' }, { v: 'WED', l: 'Wed' }, { v: 'THU', l: 'Thu' },
    { v: 'FRI', l: 'Fri' }, { v: 'SAT', l: 'Sat' }, { v: 'SUN', l: 'Sun' },
  ];
  protected readonly months =
    ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN', 'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC'];
  protected readonly fmt = fmt;

  protected tabIndex = 0;
  protected freq: Freq = 'minutes';
  protected everyN = 5;
  protected second = 0;
  protected minute = 0;
  protected hour = 12;
  protected days: string[] = ['MON'];
  protected dayOfMonth = 1;
  protected month = 'JAN';
  protected adv = { second: '0', minute: '0', hour: '12', dom: '*', mon: '*', dow: '?' };
  protected intervalN = 30;
  protected intervalUnit: 'S' | 'M' | 'H' | 'D' = 'S';
  protected iso = this.data?.value?.startsWith('P') ? this.data.value : '';

  protected readonly testing = signal(false);
  protected readonly testTimes = signal<string[]>([]);
  protected readonly testError = signal('');

  private get spec(): CronSpec {
    return {
      freq: this.freq, everyN: this.everyN, second: this.second, minute: this.minute,
      hour: this.hour, days: this.days, dayOfMonth: this.dayOfMonth, month: this.month, adv: this.adv,
    };
  }

  get isoValid(): boolean {
    return /^P(?!$)(\d+D)?(T(?!$)(\d+H)?(\d+M)?(\d+S)?)?$/i.test((this.iso || '').trim());
  }

  get expression(): string {
    if (this.tabIndex === 1) {
      const n = Math.max(1, Number(this.intervalN) || 1);
      return this.intervalUnit === 'D' ? `P${n}D` : `PT${n}${this.intervalUnit}`;
    }
    if (this.tabIndex === 2) {
      return this.isoValid ? this.iso.trim() : '';
    }
    return buildCron(this.spec);
  }

  get description(): string {
    const e = this.expression;
    return e.startsWith('P') ? describeIso(e) : describeSpec(this.spec);
  }

  resetTest(): void {
    this.testTimes.set([]);
    this.testError.set('');
  }

  test(): void {
    this.testing.set(true);
    this.testError.set('');
    this.testTimes.set([]);
    this.api.cronPreview(this.expression, 5).subscribe({
      next: (r) => {
        this.testing.set(false);
        if (r.valid) {
          this.testTimes.set(r.next ?? []);
        } else {
          this.testError.set(r.error || 'Invalid expression.');
        }
      },
      error: (e) => {
        this.testing.set(false);
        this.testError.set(
          e?.name === 'TimeoutError' ? 'Timed out — is the server running?'
            : e?.error?.message ?? e?.message ?? 'Test failed.');
      },
    });
  }

  apply(): void {
    this.ref.close(this.expression);
  }
}
