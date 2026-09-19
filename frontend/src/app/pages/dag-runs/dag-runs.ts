import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { CronsmithApi } from '../../core/api.service';
import { DagGraphView, DagRunPage, DAG_RUN_STATUSES } from '../../core/models';
import { dagStatusClass, fmt } from '../../core/util';

/**
 * DAG Runs: the run history ({@code cf_dag_log}), newest first, filterable by workflow and status.
 * Each row drills into the run's node-by-node detail. Subgraph runs appear here too, tagged with the
 * parent run they belong to.
 */
@Component({
  selector: 'cf-dag-runs',
  imports: [
    MatTableModule, MatIconModule, MatButtonModule, MatFormFieldModule, MatSelectModule,
    MatPaginatorModule,
  ],
  template: `
    <h1 class="page-title">DAG Runs</h1>
    <p class="page-sub">Run history for every workflow. Parameters, outcome and per-node results.</p>

    <div class="toolbar">
      <mat-form-field appearance="outline" class="f">
        <mat-label>Workflow</mat-label>
        <mat-select [value]="graph()" (selectionChange)="onGraph($event.value)">
          <mat-option [value]="''">All</mat-option>
          @for (d of dags(); track d.graph) {
            <mat-option [value]="d.graph">{{ d.graph }}</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline" class="f">
        <mat-label>Status</mat-label>
        <mat-select [value]="status()" (selectionChange)="onStatus($event.value)">
          <mat-option [value]="''">All</mat-option>
          @for (s of statuses; track s) { <mat-option [value]="s">{{ s }}</mat-option> }
        </mat-select>
      </mat-form-field>
      <span class="flex-1"></span>
      <button mat-stroked-button (click)="reload()"><mat-icon>refresh</mat-icon> Refresh</button>
    </div>

    <div class="card overflow-hidden">
      <table mat-table [dataSource]="page()?.items ?? []">
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Status</th>
          <td mat-cell *matCellDef="let r">
            <span class="chip" [class]="dagStatusClass(r.status)">{{ r.status }}</span>
          </td>
        </ng-container>
        <ng-container matColumnDef="graph">
          <th mat-header-cell *matHeaderCellDef>Workflow</th>
          <td mat-cell *matCellDef="let r">
            <strong>{{ r.graph }}</strong>
            @if (r.parentRunId) { <span class="sub-tag" title="subgraph run">subgraph</span> }
          </td>
        </ng-container>
        <ng-container matColumnDef="triggeredBy">
          <th mat-header-cell *matHeaderCellDef>Triggered by</th>
          <td mat-cell *matCellDef="let r" class="mono muted">{{ r.triggeredBy || '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="startedAt">
          <th mat-header-cell *matHeaderCellDef>Started</th>
          <td mat-cell *matCellDef="let r">{{ fmt(r.startedAt) }}</td>
        </ng-container>
        <ng-container matColumnDef="elapsed">
          <th mat-header-cell *matHeaderCellDef>Elapsed</th>
          <td mat-cell *matCellDef="let r">{{ r.elapsedMs != null ? r.elapsedMs + ' ms' : '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="nodes">
          <th mat-header-cell *matHeaderCellDef>Nodes</th>
          <td mat-cell *matCellDef="let r">{{ r.nodeCount ?? '—' }}</td>
        </ng-container>
        <ng-container matColumnDef="runId">
          <th mat-header-cell *matHeaderCellDef>Run</th>
          <td mat-cell *matCellDef="let r" class="mono">{{ shortId(r.runId) }}</td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns" class="clickable"
            (click)="open(row.runId)"></tr>
      </table>
      @if ((page()?.items?.length ?? 0) === 0) {
        <div class="empty"><mat-icon>history</mat-icon><p>No runs yet.</p></div>
      }
      <mat-paginator [length]="page()?.total ?? 0" [pageSize]="pageSize"
        [pageIndex]="pageIndex" [pageSizeOptions]="[10, 25, 50, 100]"
        (page)="onPage($event)" showFirstLastButtons />
    </div>
  `,
  styles: [`
    .toolbar { display: flex; align-items: center; gap: 0.75rem; margin-bottom: 1rem; flex-wrap: wrap; }
    .f { width: 220px; }
    .f ::ng-deep .mat-mdc-form-field-subscript-wrapper { display: none; }
    .flex-1 { flex: 1 1 auto; }
    .clickable { cursor: pointer; }
    .clickable:hover { background: #f6f9fd; }
    .sub-tag { margin-left: 0.5rem; font-size: 0.68rem; font-weight: 600; color: #7e57c2;
      background: #f3ecfb; border-radius: 999px; padding: 0.05rem 0.45rem; }
    .empty { padding: 2.5rem; text-align: center; color: #94a3b8; }
    .empty mat-icon { font-size: 2.5rem; width: 2.5rem; height: 2.5rem; }
  `],
})
export class DagRuns {
  private readonly api = inject(CronsmithApi);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly dags = signal<DagGraphView[]>([]);
  protected readonly page = signal<DagRunPage | undefined>(undefined);
  protected readonly graph = signal<string>('');
  protected readonly status = signal<string>('');
  protected readonly statuses = DAG_RUN_STATUSES;
  protected pageIndex = 0;
  protected pageSize = 25;

  protected readonly columns =
    ['status', 'graph', 'triggeredBy', 'startedAt', 'elapsed', 'nodes', 'runId'];
  protected readonly fmt = fmt;
  protected readonly dagStatusClass = dagStatusClass;

  constructor() {
    this.api.dags().subscribe((d) => this.dags.set(d));
    const g = this.route.snapshot.queryParamMap.get('graph');
    if (g) {
      this.graph.set(g);
    }
    this.reload();
    // Live refresh, so in-flight runs and new runs appear without a manual reload.
    interval(5000).pipe(takeUntilDestroyed()).subscribe(() => this.reload());
  }

  protected onGraph(v: string): void {
    this.graph.set(v);
    this.pageIndex = 0;
    this.reload();
  }

  protected onStatus(v: string): void {
    this.status.set(v);
    this.pageIndex = 0;
    this.reload();
  }

  protected onPage(e: PageEvent): void {
    this.pageIndex = e.pageIndex;
    this.pageSize = e.pageSize;
    this.reload();
  }

  protected reload(): void {
    this.api.dagRuns({
      graph: this.graph() || undefined,
      status: this.status() || undefined,
      limit: this.pageSize,
      offset: this.pageIndex * this.pageSize,
    }).subscribe((p) => this.page.set(p));
  }

  protected open(runId: string): void {
    this.router.navigate(['/dag/runs', runId]);
  }

  protected shortId(id: string): string {
    return id ? id.slice(0, 8) : '';
  }
}
