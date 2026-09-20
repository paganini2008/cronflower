import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatDialog } from '@angular/material/dialog';
import { CronsmithApi } from '../../core/api.service';
import { DagGraphPage, DagGraphView } from '../../core/models';
import { DagGraph } from '../../shared/dag-graph';
import { ParamDialog, ParamData } from '../../shared/param-dialog';

/**
 * Workflows: the registered DAG definitions ({@code cf_task_dag}). A master list on the left
 * (server-side paged + name filter, like the Tasks / Runs lists), the selected DAG's structure drawn
 * on the right via {@link DagGraph}. Read-only — definitions are woven on the executor side; from here
 * you inspect them, trigger them by hand, or draw a new one.
 */
@Component({
  selector: 'cf-dags',
  imports: [RouterLink, MatIconModule, MatButtonModule, MatPaginatorModule, DagGraph],
  template: `
    <div class="page-head">
      <div>
        <h1 class="page-title">Workflows</h1>
        <p class="page-sub">Registered DAG definitions. Select one to see its shape, trigger it, or draw a new one.</p>
      </div>
      <a mat-flat-button color="primary" routerLink="/dag/new"><mat-icon>add</mat-icon> New workflow</a>
    </div>

    <div class="grid">
      <div class="card list-card">
        <div class="list-filter">
          <mat-icon>search</mat-icon>
          <input type="text" placeholder="Filter by name" [value]="filter()"
                 (input)="onFilter($any($event.target).value)" />
        </div>
        @if (list().length === 0) {
          <div class="empty">
            <mat-icon>account_tree</mat-icon>
            <p>{{ filter() ? 'No workflows match.' : 'No DAGs yet.' }}</p>
          </div>
        } @else {
          <div class="list">
            @for (d of list(); track d.application + '/' + d.graph) {
              <button type="button" class="row" [class.sel]="isSel(d)" (click)="select(d)">
                <mat-icon>account_tree</mat-icon>
                <span class="row-main">
                  <span class="row-title">{{ d.graph }}</span>
                  <span class="row-sub">{{ d.application }}</span>
                </span>
                <span class="count">{{ d.nodeCount }} nodes</span>
              </button>
            }
          </div>
        }
        <mat-paginator [length]="total()" [pageSize]="pageSize" [pageIndex]="pageIndex"
          [pageSizeOptions]="[10, 20, 50]" (page)="onPage($event)" hidePageSize="false" />
      </div>

      <div class="card graph-card">
        @if (selected(); as sel) {
          <div class="graph-head">
            <div>
              <span class="g-title">{{ sel.graph }}</span>
              <span class="g-sub">{{ sel.application }} · {{ sel.nodeCount }} nodes</span>
            </div>
            <div class="g-actions">
              <button mat-flat-button color="primary" (click)="trigger(sel.graph)" [disabled]="triggering()">
                <mat-icon>play_arrow</mat-icon> {{ triggering() ? 'Triggering…' : 'Trigger' }}
              </button>
              <a mat-stroked-button [routerLink]="['/dag/runs']" [queryParams]="{ graph: sel.graph }">
                <mat-icon>history</mat-icon> View runs
              </a>
            </div>
          </div>
          <cf-dag-graph class="graph" [definition]="sel.definition" />
          <div class="legend">
            <span><i class="sw entry"></i> entry</span>
            <span><i class="sw sub"></i> subgraph</span>
            <span><i class="sw cond"></i> conditional edge</span>
          </div>
        } @else {
          <div class="empty">
            <mat-icon>account_tree</mat-icon>
            <p>No DAGs yet. Draw one on the canvas, or start an executor that hosts a &#64;Dag.</p>
            <a mat-flat-button color="primary" routerLink="/dag/new"><mat-icon>add</mat-icon> New workflow</a>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .page-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; }
    .page-head .page-title { margin: 0; } .page-head .page-sub { margin: 0.15rem 0 1.1rem; }
    .g-actions { display: flex; gap: 0.5rem; }
    .grid { display: grid; grid-template-columns: 320px 1fr; gap: 1.25rem; align-items: start; }
    @media (max-width: 820px) { .grid { grid-template-columns: 1fr; } }
    .list-card { padding: 0; display: flex; flex-direction: column; }
    .list-filter { display: flex; align-items: center; gap: 0.4rem; padding: 0.6rem 0.8rem;
      border-bottom: 1px solid #eef2f7; }
    .list-filter mat-icon { color: #3d5372; font-size: 20px; width: 20px; height: 20px; }
    .list-filter input { border: 0; outline: 0; flex: 1 1 auto; font: inherit; color: #0f2c4d; background: transparent; }
    .list { padding: 0.4rem; max-height: 60vh; overflow: auto; }
    .row { display: flex; align-items: center; gap: 0.6rem; width: 100%; text-align: left;
      background: transparent; border: 0; padding: 0.6rem 0.7rem; cursor: pointer; color: inherit; }
    .row:hover { background: #f1f5f9; }
    .row.sel { background: #e8f1fd; }
    .row.sel .row-title { color: #1565c0; }
    .row > mat-icon { color: #3d5372; }
    .row.sel > mat-icon { color: #1565c0; }
    .row-main { display: flex; flex-direction: column; flex: 1 1 auto; min-width: 0; }
    .row-title { font-weight: 650; font-size: 0.9rem; }
    .row-sub { font-size: 0.75rem; color: #3d5372; overflow: hidden; text-overflow: ellipsis; }
    .count { font-size: 0.7rem; color: #3d5372; white-space: nowrap; }
    .graph-card { padding: 0; overflow: hidden; display: flex; flex-direction: column; min-height: 420px; }
    .graph-head { display: flex; align-items: center; justify-content: space-between; gap: 1rem;
      padding: 0.9rem 1.1rem; border-bottom: 1px solid #eef2f7; }
    .g-title { font-weight: 700; font-size: 1.05rem; color: #0f2c4d; }
    .g-sub { font-size: 0.78rem; color: #3d5372; margin-left: 0.6rem; }
    .graph { flex: 1 1 auto; height: 62vh; }
    .legend { display: flex; gap: 1.2rem; padding: 0.6rem 1.1rem; border-top: 1px solid #eef2f7;
      font-size: 0.75rem; color: #3d5372; }
    .legend i.sw { display: inline-block; width: 0.85rem; height: 0.85rem; border-radius: 4px;
      vertical-align: -2px; margin-right: 0.3rem; border: 2px solid #c3d0e0; background: #eef2f7; }
    .legend i.entry { border-color: #16b8a6; background: #d9f5f0; }
    .legend i.sub { border-color: #d99514; border-style: dashed; background: #fbf1dc; }
    .legend i.cond { border: 0; height: 0; border-top: 2px dashed #7c6bb0; width: 1.1rem; border-radius: 0; }
    .empty { padding: 2.5rem; text-align: center; color: #3d5372; display: flex; flex-direction: column;
      align-items: center; gap: 0.4rem; }
    .empty mat-icon { font-size: 2.5rem; width: 2.5rem; height: 2.5rem; }
  `],
})
export class Dags implements OnDestroy {
  private readonly api = inject(CronsmithApi);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);

  protected pageIndex = 0;
  protected pageSize = 20;
  protected readonly filter = signal('');
  private readonly page = signal<DagGraphPage | undefined>(undefined);
  protected readonly list = computed<DagGraphView[]>(() => this.page()?.items ?? []);
  protected readonly total = computed(() => this.page()?.total ?? 0);
  protected readonly triggering = signal(false);
  private readonly selectedKey = signal<string | null>(null);
  private timer?: ReturnType<typeof setInterval>;
  private filterDebounce?: ReturnType<typeof setTimeout>;

  constructor() {
    this.load();
    // Refresh periodically so newly registered/removed DAGs appear (like the old poll cadence).
    this.timer = setInterval(() => this.load(), 8000);
  }

  ngOnDestroy(): void {
    clearInterval(this.timer);
    clearTimeout(this.filterDebounce);
  }

  private load(): void {
    this.api.dags({ q: this.filter(), limit: this.pageSize, offset: this.pageIndex * this.pageSize })
      .subscribe({ next: (p) => this.page.set(p), error: () => {} });
  }

  protected onPage(e: PageEvent): void {
    this.pageIndex = e.pageIndex;
    this.pageSize = e.pageSize;
    this.load();
  }

  protected onFilter(value: string): void {
    this.filter.set(value);
    this.pageIndex = 0;
    clearTimeout(this.filterDebounce);
    this.filterDebounce = setTimeout(() => this.load(), 250);
  }

  /** Kick the selected DAG off by hand — ask for an optional initial state, then jump to the run. */
  protected trigger(graph: string): void {
    const ref = this.dialog.open(ParamDialog, {
      autoFocus: false, restoreFocus: false,
      data: {
        title: `Trigger ${graph}`,
        hint: 'Optional initial channel state for this run, as a JSON object. Leave as {} for none.',
        value: '{}', mode: 'json', confirmLabel: 'Trigger',
        placeholder: '{ "input": "..." }',
      } as ParamData,
    });
    ref.afterClosed().subscribe((val: string | undefined) => {
      if (val == null) {
        return;
      }
      let state: Record<string, unknown> = {};
      try {
        state = JSON.parse(val || '{}');
      } catch {
        return;
      }
      this.triggering.set(true);
      this.api.triggerDag(graph, state).subscribe({
        next: (r) => { this.triggering.set(false); this.router.navigate(['/dag/runs', r.runId]); },
        error: () => this.triggering.set(false),
      });
    });
  }

  protected readonly selected = computed<DagGraphView | undefined>(() => {
    const list = this.list();
    if (list.length === 0) {
      return undefined;
    }
    const key = this.selectedKey();
    return list.find((d) => this.keyOf(d) === key) ?? list[0];
  });

  protected isSel(d: DagGraphView): boolean {
    return this.selected() ? this.keyOf(this.selected()!) === this.keyOf(d) : false;
  }

  protected select(d: DagGraphView): void {
    this.selectedKey.set(this.keyOf(d));
  }

  private keyOf(d: DagGraphView): string {
    return `${d.application}/${d.graph}`;
  }
}
