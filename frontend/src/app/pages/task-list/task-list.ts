import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';
import { FormsModule } from '@angular/forms';
import { CronsmithApi } from '../../core/api.service';
import { TaskView } from '../../core/models';
import { TASK_STATUSES } from '../../core/models';
import { fmt, statusClass } from '../../core/util';
import { ConfirmDialog } from '../../shared/confirm-dialog';

@Component({
  selector: 'cf-task-list',
  imports: [
    RouterLink, FormsModule, MatTableModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatButtonModule, MatIconModule, MatMenuModule, MatTooltipModule, MatProgressBarModule,
    MatPaginatorModule,
  ],
  template: `
    <div class="flex items-start justify-between flex-wrap gap-2">
      <div>
        <h1 class="page-title">Tasks</h1>
        <p class="page-sub">{{ data()?.total ?? 0 }} task(s).</p>
      </div>
      <div class="flex gap-2">
        <button mat-stroked-button (click)="load()"><mat-icon>refresh</mat-icon> Refresh</button>
        <a mat-flat-button color="primary" routerLink="/tasks/new"><mat-icon>add</mat-icon> New task</a>
      </div>
    </div>

    <div class="card p-3 mb-4 flex flex-wrap gap-3 items-center">
      <mat-form-field appearance="outline" subscriptSizing="dynamic" class="w-40">
        <mat-label>Group</mat-label>
        <input matInput [(ngModel)]="group" (keyup.enter)="load()" />
      </mat-form-field>
      <mat-form-field appearance="outline" subscriptSizing="dynamic" class="w-40">
        <mat-label>Name</mat-label>
        <input matInput [(ngModel)]="name" (keyup.enter)="load()" />
      </mat-form-field>
      <mat-form-field appearance="outline" subscriptSizing="dynamic" class="w-44">
        <mat-label>Status</mat-label>
        <mat-select [(ngModel)]="status" (selectionChange)="load()">
          <mat-option [value]="''">Any</mat-option>
          @for (s of statuses; track s) { <mat-option [value]="s">{{ s }}</mat-option> }
        </mat-select>
      </mat-form-field>
      <button mat-flat-button color="primary" (click)="load()"><mat-icon>search</mat-icon> Filter</button>
    </div>

    <div class="card overflow-hidden">
      @if (loading()) { <mat-progress-bar mode="indeterminate" /> }
      <table mat-table [dataSource]="data()?.items ?? []">
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let t"><span class="chip" [class]="cls(t.status)">{{ t.status }}</span></td>
        </ng-container>
        <ng-container matColumnDef="task">
          <th mat-header-cell *matHeaderCellDef>Task</th>
          <td mat-cell *matCellDef="let t">
            <a class="task-link" [routerLink]="['/tasks', t.taskGroup, t.taskName]">
              <span class="muted">{{ t.taskGroup }} /</span> <strong>{{ t.taskName }}</strong>
            </a>
            @if (t.description) { <div class="muted text-xs">{{ t.description }}</div> }
          </td>
        </ng-container>
        <ng-container matColumnDef="cron">
          <th mat-header-cell *matHeaderCellDef>Schedule</th>
          <td mat-cell *matCellDef="let t">
            <span class="cron-cell">
              <mat-icon class="cron-type" [class.ycron]="t.parser === 'ycron'"
                        [matTooltip]="t.parser === 'ycron' ? 'YCRON — year-based' : 'Cron — traditional'">
                {{ t.parser === 'ycron' ? 'event_repeat' : 'schedule' }}
              </mat-icon>
              <span class="mono">{{ t.cron || '—' }}</span>
            </span>
          </td>
        </ng-container>
        <ng-container matColumnDef="runs">
          <th mat-header-cell *matHeaderCellDef>Runs</th>
          <td mat-cell *matCellDef="let t">
            <span>{{ t.runCount }} <span class="muted">run(s)</span></span>
            @if (t.failureCount) {
              <span class="bad" style="margin-left:.45rem">· {{ t.failureCount }} failed</span>
            } @else if (t.runCount) {
              <span class="ok" style="margin-left:.45rem">· all ok</span>
            }
          </td>
        </ng-container>
        <ng-container matColumnDef="next">
          <th mat-header-cell *matHeaderCellDef>Next fire</th>
          <td mat-cell *matCellDef="let t" class="muted">{{ fmt(t.nextFiredDateTime) }}</td>
        </ng-container>
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let t" class="text-right">
            <button matIconButton [matMenuTriggerFor]="menu" (click)="$event.stopPropagation()">
              <mat-icon>more_vert</mat-icon>
            </button>
            <mat-menu #menu="matMenu">
              <a mat-menu-item [routerLink]="['/tasks', t.taskGroup, t.taskName]"><mat-icon>visibility</mat-icon> View</a>
              <a mat-menu-item [routerLink]="['/tasks', t.taskGroup, t.taskName, 'edit']"><mat-icon>edit</mat-icon> Edit</a>
              <button mat-menu-item (click)="runNow(t)"><mat-icon>play_circle</mat-icon> Run now</button>
              <button mat-menu-item (click)="act(t, 'pause')"><mat-icon>pause</mat-icon> Pause</button>
              <button mat-menu-item (click)="act(t, 'resume')"><mat-icon>play_arrow</mat-icon> Resume</button>
              <button mat-menu-item (click)="withdraw(t)"><mat-icon>block</mat-icon> Withdraw</button>
              <button mat-menu-item class="danger-item" (click)="remove(t)"><mat-icon>delete</mat-icon> Delete</button>
            </mat-menu>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
      @if (!loading() && (data()?.items?.length ?? 0) === 0) {
        <div class="empty">No tasks match.</div>
      }
      <mat-paginator
        [length]="data()?.total ?? 0"
        [pageSize]="pageSize"
        [pageIndex]="pageIndex"
        [pageSizeOptions]="[10, 20, 50, 100]"
        (page)="onPage($event)"
      />
    </div>
  `,
  styles: [`
    .task-link { text-decoration: none; color: #0f2c4d; }
    .task-link:hover strong { color: #1565c0; }
    .w-40 { width: 10rem; } .w-44 { width: 11rem; }
    .empty { padding: 2rem; text-align: center; color: #94a3b8; }
    .danger-item mat-icon, .danger-item { color: #d93025; }
    .cron-cell { display: inline-flex; align-items: center; gap: 0.4rem; }
    .cron-type { font-size: 18px; width: 18px; height: 18px; color: #94a3b8; }
    .cron-type.ycron { color: #1565c0; }
  `],
})
export class TaskList implements OnInit {
  private readonly api = inject(CronsmithApi);
  private readonly route = inject(ActivatedRoute);
  private readonly snack = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  protected group = '';
  protected name = '';
  protected status = '';
  protected readonly statuses = TASK_STATUSES;
  protected readonly columns = ['status', 'task', 'cron', 'runs', 'next', 'actions'];

  protected readonly data = signal<{ total: number; items: TaskView[] } | undefined>(undefined);
  protected readonly loading = signal(false);
  protected pageIndex = 0;
  protected pageSize = 10;

  protected readonly fmt = fmt;

  ngOnInit(): void {
    const s = this.route.snapshot.queryParamMap.get('status');
    if (s) {
      this.status = s;
    }
    this.load();
  }

  /** Called by the filter buttons: reset to the first page. */
  load(): void {
    this.pageIndex = 0;
    this.fetch();
  }

  onPage(e: PageEvent): void {
    this.pageIndex = e.pageIndex;
    this.pageSize = e.pageSize;
    this.fetch();
  }

  private fetch(): void {
    this.loading.set(true);
    this.api.tasks({
      group: this.group, name: this.name, status: this.status,
      limit: this.pageSize, offset: this.pageIndex * this.pageSize,
    }).subscribe({
      next: (r) => { this.data.set(r); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  act(t: TaskView, action: 'pause' | 'resume' | 'cancel'): void {
    this.api.action(t.taskGroup, t.taskName, action).subscribe({ next: () => this.fetch(), error: () => this.fetch() });
  }

  runNow(t: TaskView): void {
    this.api.runNow(t.taskGroup, t.taskName).subscribe({
      next: (r) => {
        this.snack.open(r['success'] === true ? `Ran ${t.taskName}` : 'Run failed', 'OK',
          { duration: 3000 });
        this.fetch();
      },
      error: () => this.snack.open('Run failed', 'Dismiss', { duration: 3000 }),
    });
  }

  /** Withdraw = the backend 'cancel' transition. It is terminal, so confirm first. */
  withdraw(t: TaskView): void {
    this.dialog.open(ConfirmDialog, {
      autoFocus: false,
      data: {
        title: `Withdraw ${t.taskName}?`,
        message: `“${t.taskGroup} / ${t.taskName}” will stop firing. Its definition and run history are kept.`,
        note: 'This cannot be undone from here — a withdrawn task can only be brought back by editing and saving it.',
        confirmLabel: 'Withdraw',
        icon: 'block',
      },
    }).afterClosed().subscribe((ok) => {
      if (ok) {
        this.act(t, 'cancel');
      }
    });
  }

  remove(t: TaskView): void {
    this.dialog.open(ConfirmDialog, {
      autoFocus: false,
      data: {
        title: `Delete ${t.taskName}?`,
        message: `“${t.taskGroup} / ${t.taskName}” will be permanently deleted, along with its execution history.`,
        note: 'This cannot be undone.',
        confirmLabel: 'Delete',
        danger: true,
      },
    }).afterClosed().subscribe((ok) => {
      if (ok) {
        this.api.remove(t.taskGroup, t.taskName).subscribe(() => this.load());
      }
    });
  }

  cls(status: string): string {
    return statusClass(status);
  }
}
