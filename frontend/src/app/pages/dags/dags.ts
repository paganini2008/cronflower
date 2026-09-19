import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { CronsmithApi } from '../../core/api.service';
import { DagGraphView } from '../../core/models';
import { poll } from '../../core/util';
import { DagGraph } from '../../shared/dag-graph';

/**
 * Workflows: the registered DAG definitions ({@code cf_task_dag}). A master list on the left, the
 * selected DAG's structure drawn on the right via {@link DagGraph}. Read-only — definitions are woven
 * on the executor side; from here you inspect them and jump to their run history.
 */
@Component({
  selector: 'cf-dags',
  imports: [RouterLink, MatIconModule, MatButtonModule, DagGraph],
  template: `
    <div class="page-head">
      <div>
        <h1 class="page-title">Workflows</h1>
        <p class="page-sub">Registered DAG definitions. Select one to see its shape, trigger it, or draw a new one.</p>
      </div>
      <a mat-flat-button color="primary" routerLink="/dag/new"><mat-icon>add</mat-icon> New workflow</a>
    </div>

    @if ((dags() ?? []).length === 0) {
      <div class="card empty">
        <mat-icon>account_tree</mat-icon>
        <p>No DAGs yet. Draw one on the canvas, or start an executor that hosts a &#64;Dag.</p>
        <a mat-flat-button color="primary" routerLink="/dag/new"><mat-icon>add</mat-icon> New workflow</a>
      </div>
    } @else {
      <div class="grid">
        <div class="card list">
          @for (d of dags(); track d.application + '/' + d.graph) {
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
          }
        </div>
      </div>
    }
  `,
  styles: [`
    .page-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; }
    .page-head .page-title { margin: 0; } .page-head .page-sub { margin: 0.15rem 0 1.1rem; }
    .g-actions { display: flex; gap: 0.5rem; }
    .grid { display: grid; grid-template-columns: 320px 1fr; gap: 1.25rem; align-items: start; }
    @media (max-width: 820px) { .grid { grid-template-columns: 1fr; } }
    .list { padding: 0.4rem; max-height: 72vh; overflow: auto; }
    .row { display: flex; align-items: center; gap: 0.6rem; width: 100%; text-align: left;
      background: transparent; border: 0; border-radius: 10px; padding: 0.6rem 0.7rem; cursor: pointer;
      color: inherit; }
    .row:hover { background: #f1f5f9; }
    .row.sel { background: #e8f1fd; }
    .row.sel .row-title { color: #1565c0; }
    .row > mat-icon { color: #8aa0bd; }
    .row.sel > mat-icon { color: #1565c0; }
    .row-main { display: flex; flex-direction: column; flex: 1 1 auto; min-width: 0; }
    .row-title { font-weight: 650; font-size: 0.9rem; }
    .row-sub { font-size: 0.75rem; color: #7a8aa0; overflow: hidden; text-overflow: ellipsis; }
    .count { font-size: 0.7rem; color: #94a3b8; white-space: nowrap; }
    .graph-card { padding: 0; overflow: hidden; display: flex; flex-direction: column; min-height: 420px; }
    .graph-head { display: flex; align-items: center; justify-content: space-between; gap: 1rem;
      padding: 0.9rem 1.1rem; border-bottom: 1px solid #eef2f7; }
    .g-title { font-weight: 700; font-size: 1.05rem; color: #0f2c4d; }
    .g-sub { font-size: 0.78rem; color: #7a8aa0; margin-left: 0.6rem; }
    .graph { flex: 1 1 auto; height: 62vh; }
    .legend { display: flex; gap: 1.2rem; padding: 0.6rem 1.1rem; border-top: 1px solid #eef2f7;
      font-size: 0.75rem; color: #64748b; }
    .legend i.sw { display: inline-block; width: 0.85rem; height: 0.85rem; border-radius: 4px;
      vertical-align: -2px; margin-right: 0.3rem; border: 2px solid #c3d0e0; background: #eef2f7; }
    .legend i.entry { border-color: #16b8a6; background: #d9f5f0; }
    .legend i.sub { border-color: #d99514; border-style: dashed; background: #fbf1dc; }
    .legend i.cond { border: 0; height: 0; border-top: 2px dashed #7c6bb0; width: 1.1rem; border-radius: 0; }
    .empty { padding: 2.5rem; text-align: center; color: #94a3b8; display: flex; flex-direction: column;
      align-items: center; gap: 0.4rem; }
    .empty mat-icon { font-size: 2.5rem; width: 2.5rem; height: 2.5rem; }
  `],
})
export class Dags {
  private readonly api = inject(CronsmithApi);
  private readonly router = inject(Router);
  protected readonly dags = poll(() => this.api.dags(), 8000);
  protected readonly triggering = signal(false);
  private readonly selectedKey = signal<string | null>(null);

  /** Kick the selected DAG off by hand and jump to the new run so it can be watched live. */
  protected trigger(graph: string): void {
    this.triggering.set(true);
    this.api.triggerDag(graph).subscribe({
      next: (r) => { this.triggering.set(false); this.router.navigate(['/dag/runs', r.runId]); },
      error: () => this.triggering.set(false),
    });
  }

  protected readonly selected = computed<DagGraphView | undefined>(() => {
    const list = this.dags() ?? [];
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
