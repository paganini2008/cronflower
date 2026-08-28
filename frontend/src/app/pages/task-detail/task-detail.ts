import { Component, effect, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';
import { CronsmithApi } from '../../core/api.service';
import { TextViewerDialog } from '../../shared/text-viewer-dialog';
import { ConfirmDialog } from '../../shared/confirm-dialog';
import { LogView, TaskView } from '../../core/models';
import { fmt, statusClass } from '../../core/util';

@Component({
  selector: 'cf-task-detail',
  imports: [
    RouterLink, MatTableModule, MatButtonModule, MatIconModule, MatTooltipModule, MatPaginatorModule,
  ],
  template: `
    <a routerLink="/tasks" class="back"><mat-icon>arrow_back</mat-icon> Tasks</a>

    @if (task(); as t) {
      <div class="flex items-start justify-between flex-wrap gap-2 mt-2">
        <div>
          <h1 class="page-title">
            <span class="muted">{{ t.taskGroup }} /</span> {{ t.taskName }}
            <span class="chip" [class]="cls(t.status)">{{ t.status }}</span>
          </h1>
          <p class="page-sub">{{ t.description || 'No description.' }}</p>
        </div>
        <div class="flex gap-2">
          <button mat-flat-button color="primary" (click)="runNow()" [disabled]="running()">
            <mat-icon>play_circle</mat-icon> {{ running() ? 'Running…' : 'Run now' }}
          </button>
          <button mat-stroked-button (click)="act('pause')"><mat-icon>pause</mat-icon> Pause</button>
          <button mat-stroked-button (click)="act('resume')"><mat-icon>play_arrow</mat-icon> Resume</button>
          <button mat-stroked-button (click)="withdraw()"><mat-icon>block</mat-icon> Withdraw</button>
          <a mat-stroked-button [routerLink]="['/tasks', t.taskGroup, t.taskName, 'edit']">
            <mat-icon>edit</mat-icon> Edit
          </a>
        </div>
      </div>

      <div class="grid gap-4 mb-4" style="grid-template-columns: 1fr 1fr 1fr;">
        <div class="card p-4"><div class="l">Run count</div><div class="v">{{ t.runCount }}</div></div>
        <div class="card p-4"><div class="l">Failures</div><div class="v" [class.bad]="t.failureCount">{{ t.failureCount }}</div></div>
        <div class="card p-4"><div class="l">Misfires</div><div class="v">{{ t.misfireCount }}</div></div>
      </div>

      <div class="flex flex-col gap-4">
        <div class="card p-5">
          <h2 class="section-title">Definition</h2>
          <dl class="meta">
            <div><dt>Type</dt><dd>
              <span class="chip" [class]="t.taskType === 'HTTP' ? 'st-scheduled' : 'st-standby'">
                {{ t.taskType === 'HTTP' ? 'HTTP API' : 'Spring Bean' }}</span></dd></div>
            <div><dt>Schedule</dt><dd class="mono">{{ t.cron || '—' }}</dd></div>
            <div><dt>Next fire</dt><dd>{{ fmt(t.nextFiredDateTime) }}</dd></div>
            <div><dt>Previous fire</dt><dd>{{ fmt(t.previousFiredDateTime) }}</dd></div>
            @if (t.taskType === 'HTTP') {
              <div><dt>Method</dt><dd class="mono">{{ t.httpMethod || 'GET' }}</dd></div>
              <div><dt>Endpoint</dt><dd class="mono">{{ t.url || '—' }}</dd></div>
              <div><dt>Payload</dt><dd class="mono">{{ t.initialParameter || '—' }}</dd></div>
            } @else {
              <div><dt>Application</dt><dd>{{ t.application || '—' }}</dd></div>
              <div><dt>Bean</dt><dd class="mono">{{ t.beanName || '—' }}</dd></div>
              <div><dt>Method</dt><dd class="mono">{{ t.className }}#{{ t.methodName }}</dd></div>
              <div><dt>Parameter</dt><dd class="mono">{{ t.initialParameter || '—' }}</dd></div>
            }
            <div><dt>Timeout</dt><dd>{{ t.timeout }} ms</dd></div>
            <div><dt>Retries</dt><dd>{{ t.maxRetryCount }} × {{ t.retryInterval }}ms</dd></div>
            <div><dt>Misfire policy</dt><dd>{{ t.misfirePolicy || '—' }}</dd></div>
          </dl>
        </div>

        <div class="card p-5">
          <h2 class="section-title">Execution history</h2>
          <div class="table-wrap">
          <table mat-table [dataSource]="logs()">
            <ng-container matColumnDef="result">
              <th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let l">
                <mat-icon [class]="l.success ? 'ok' : 'bad'">{{ l.success ? 'check_circle' : 'error' }}</mat-icon>
              </td>
            </ng-container>
            <ng-container matColumnDef="fired">
              <th mat-header-cell *matHeaderCellDef>Fired</th>
              <td mat-cell *matCellDef="let l">{{ fmt(l.firedDateTime) }}</td>
            </ng-container>
            <ng-container matColumnDef="elapsed">
              <th mat-header-cell *matHeaderCellDef>Elapsed</th>
              <td mat-cell *matCellDef="let l">{{ l.elapsed }}ms</td>
            </ng-container>
            <ng-container matColumnDef="attempt">
              <th mat-header-cell *matHeaderCellDef>Attempt</th>
              <td mat-cell *matCellDef="let l">
                <span class="chip attempt-chip" [class.retry]="l.attempt > 0">#{{ l.attempt }}</span>
              </td>
            </ng-container>
            <ng-container matColumnDef="scheduler">
              <th mat-header-cell *matHeaderCellDef>Scheduler</th>
              <td mat-cell *matCellDef="let l">
                @if (l.schedulerRepr) {
                  <span class="mono repr" [matTooltip]="l.schedulerRepr">{{ l.schedulerRepr }}</span>
                } @else { <span class="muted">—</span> }
              </td>
            </ng-container>
            <ng-container matColumnDef="executor">
              <th mat-header-cell *matHeaderCellDef>Executor</th>
              <td mat-cell *matCellDef="let l">
                @if (l.executorRepr) {
                  <span class="mono repr" [matTooltip]="l.executorRepr">{{ l.executorRepr }}</span>
                } @else { <span class="muted">—</span> }
              </td>
            </ng-container>
            <ng-container matColumnDef="input">
              <th mat-header-cell *matHeaderCellDef>Input</th>
              <td mat-cell *matCellDef="let l" class="output">
                @if (l.parameter) {
                  <span class="mono ret">{{ short(l.parameter) }}</span>
                  @if (isLong(l.parameter)) {
                    <button matIconButton class="expand" matTooltip="View full parameter"
                            (click)="openText('Parameter', l.parameter, 'default')">
                      <mat-icon>open_in_full</mat-icon></button>
                  }
                } @else { <span class="muted">—</span> }
              </td>
            </ng-container>
            <ng-container matColumnDef="output">
              <th mat-header-cell *matHeaderCellDef>Result</th>
              <td mat-cell *matCellDef="let l" class="output">
                @if (l.success) {
                  @if (l.returnValue) {
                    <button matIconButton class="view ok-i" matTooltip="View return value"
                            (click)="openText('Return value', l.returnValue, 'default')">
                      <mat-icon>data_object</mat-icon></button>
                  } @else { <span class="muted">—</span> }
                } @else {
                  <button matIconButton class="view bad-i" matTooltip="View error"
                          (click)="openText('Error detail', l.errorDetail || 'failed', 'error')">
                    <mat-icon>error_outline</mat-icon></button>
                }
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="logColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: logColumns"></tr>
          </table>
          </div>
          @if (logs().length === 0) { <div class="empty">No executions recorded yet.</div> }
          <mat-paginator
            [length]="task()?.runCount ?? 0"
            [pageSize]="logPageSize"
            [pageIndex]="logPageIndex"
            [pageSizeOptions]="[10, 20, 50]"
            (page)="onLogPage($event)"
          />
        </div>
      </div>
    } @else {
      <p class="muted mt-4">Loading…</p>
    }
  `,
  styles: [`
    .back { display: inline-flex; align-items: center; gap: 0.25rem; color: #1565c0; text-decoration: none; font-size: 0.9rem; }
    .l { color: #7a8aa0; font-size: 0.8rem; } .v { font-size: 1.6rem; font-weight: 700; color: #0f2c4d; }
    .section-title { font-size: 1rem; font-weight: 600; color: #0f2c4d; margin: 0 0 1rem; }
    .table-wrap { overflow-x: auto; }
    table { min-width: 760px; }
    th.mat-mdc-header-cell, td.mat-mdc-cell { padding-right: 1.75rem; white-space: nowrap; }
    .meta { display: grid; grid-template-columns: 1fr 1fr; gap: 0 2rem; }
    .meta div { display: flex; justify-content: space-between; gap: 1rem; padding: 0.45rem 0; border-bottom: 1px dashed #eef2f7; }
    .meta dt { color: #7a8aa0; font-size: 0.82rem; white-space: nowrap; } .meta dd { margin: 0; text-align: right; word-break: break-all; }
    .meta dd.pre { white-space: pre-line; }
    .output { vertical-align: middle; }
    .ret { display: inline-block; vertical-align: middle; max-width: 260px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .expand { width: 28px; height: 28px; vertical-align: middle; }
    .expand mat-icon { font-size: 1rem; width: 1rem; height: 1rem; color: #64748b; }
    .view { width: 32px; height: 32px; vertical-align: middle; }
    .view.ok-i mat-icon { color: #1565c0; }
    .view.bad-i mat-icon { color: #d93025; }
    .attempt-chip { background: #eef2f7; color: #64748b; }
    .attempt-chip.retry { background: #fef3c7; color: #b45309; }
    .repr { max-width: 240px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
            display: inline-block; vertical-align: middle; font-size: 12px; color: #475569; }
    .empty { padding: 1.5rem; text-align: center; color: #94a3b8; }
    mat-icon.ok { color: #0f9d58; } mat-icon.bad { color: #d93025; }
  `],
})
export class TaskDetail {
  private readonly api = inject(CronsmithApi);
  private readonly router = inject(Router);

  readonly group = input.required<string>();
  readonly name = input.required<string>();

  private readonly snack = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  protected readonly task = signal<TaskView | undefined>(undefined);
  protected readonly logs = signal<LogView[]>([]);
  protected readonly running = signal(false);
  protected readonly logColumns =
      ['result', 'fired', 'elapsed', 'attempt', 'input', 'output', 'scheduler', 'executor'];
  protected readonly fmt = fmt;
  protected logPageIndex = 0;
  protected logPageSize = 20;

  constructor() {
    // Reload when the route (group/name) changes; reset to the first page of logs.
    effect(() => {
      this.group();
      this.name();
      this.logPageIndex = 0;
      this.reload();
    });
  }

  private reload(): void {
    const g = this.group();
    const n = this.name();
    this.api.task(g, n).subscribe((t) => this.task.set(t));
    this.loadLogs();
  }

  private loadLogs(): void {
    this.api.logs(this.group(), this.name(), this.logPageSize, this.logPageIndex * this.logPageSize)
      .subscribe((l) => this.logs.set(l));
  }

  onLogPage(e: PageEvent): void {
    this.logPageIndex = e.pageIndex;
    this.logPageSize = e.pageSize;
    this.loadLogs();
  }

  runNow(): void {
    this.running.set(true);
    this.api.runNow(this.group(), this.name()).subscribe({
      next: (r) => {
        this.running.set(false);
        const ok = r['success'] === true;
        this.snack.open(ok ? 'Ran once — ' + (r['returnValue'] ?? 'done')
          : 'Run failed: ' + (r['error'] ?? 'error'), 'OK', { duration: 4000 });
        this.logPageIndex = 0;
        this.reload();
      },
      error: (e) => {
        this.running.set(false);
        this.snack.open('Run failed: ' + (e?.error?.message ?? e?.message ?? 'error'), 'Dismiss',
          { duration: 5000 });
      },
    });
  }

  act(action: 'pause' | 'resume' | 'cancel'): void {
    this.api.action(this.group(), this.name(), action).subscribe((t) => {
      this.task.set(t);
    });
  }

  /** Withdraw = the backend 'cancel' transition. Terminal, so confirm first. */
  withdraw(): void {
    this.dialog.open(ConfirmDialog, {
      autoFocus: false,
      data: {
        title: `Withdraw ${this.name()}?`,
        message: `“${this.group()} / ${this.name()}” will stop firing. Its definition and run history are kept.`,
        note: 'This cannot be undone from here — a withdrawn task can only be brought back by editing and saving it.',
        confirmLabel: 'Withdraw',
        icon: 'block',
      },
    }).afterClosed().subscribe((ok) => {
      if (ok) {
        this.act('cancel');
      }
    });
  }

  cls(status: string): string {
    return statusClass(status);
  }

  /** Whether a value is long enough to warrant the expand dialog. */
  isLong(s: string | null | undefined): boolean {
    return !!s && (s.length > 48 || s.includes('\n'));
  }

  short(s: string | null | undefined): string {
    if (!s) {
      return '—';
    }
    return s.length > 48 ? s.slice(0, 48) + '…' : s;
  }

  /** The first (message) line of an error, trimmed for the table. */
  firstLine(s: string | null | undefined): string {
    if (!s) {
      return 'failed';
    }
    const line = s.split('\n')[0].trim();
    return line.length > 60 ? line.slice(0, 60) + '…' : line;
  }

  openText(title: string, text: string | null | undefined, tone: 'error' | 'default'): void {
    this.dialog.open(TextViewerDialog, { data: { title, text: text || '', tone }, autoFocus: false });
  }
}
