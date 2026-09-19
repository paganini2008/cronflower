import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval } from 'rxjs';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';
import { CronsmithApi } from '../../core/api.service';
import { DagDefinition, DagNodeView, DagRunDetail } from '../../core/models';
import { dagStatusClass, fmt } from '../../core/util';
import { DagGraph } from '../../shared/dag-graph';
import { TextViewerDialog } from '../../shared/text-viewer-dialog';

/**
 * One DAG run drilled down: the woven graph coloured by each node's outcome, a node inspector, the
 * run's parameter and return value, and links to any subgraph runs. Everything reachable from a
 * single {@code runId} ({@code cf_dag_log} + {@code cf_dag_node_log}).
 */
@Component({
  selector: 'cf-dag-run-detail',
  imports: [RouterLink, MatTableModule, MatIconModule, MatButtonModule, MatTooltipModule, DagGraph],
  template: `
    @if (detail(); as d) {
      <div class="head">
        <a mat-icon-button routerLink="/dag/runs" aria-label="Back"><mat-icon>arrow_back</mat-icon></a>
        <h1 class="page-title">{{ d.run.graph }}</h1>
        <span class="chip" [class]="dagStatusClass(d.run.status)">{{ d.run.status }}</span>
        @if (d.run.parentRunId) {
          <a class="parent" [routerLink]="['/dag/runs', d.run.parentRunId]">
            <mat-icon>subdirectory_arrow_right</mat-icon> parent run {{ shortId(d.run.parentRunId) }}
          </a>
        }
        <span class="flex-1"></span>
        <button mat-stroked-button (click)="reload()"><mat-icon>refresh</mat-icon> Refresh</button>
      </div>
      <p class="run-id mono">{{ d.run.runId }}</p>

      <div class="grid">
        <div class="card graph-card">
          @if (definition(); as def) {
            <cf-dag-graph class="graph" [definition]="def" [nodeStatus]="nodeStatus()"
              [selected]="selectedNode()" (nodeClick)="pick($event)" />
          }
          @if (picked(); as n) {
            <div class="node-panel">
              <div class="np-head">
                <span class="chip" [class]="dagStatusClass(n.status)">{{ n.status }}</span>
                <strong>{{ n.node }}</strong>
                <span class="np-meta">seq {{ n.seq }} · {{ n.elapsedMs != null ? n.elapsedMs + ' ms' : '—' }}</span>
                <span class="flex-1"></span>
                <button mat-icon-button (click)="pick('')" aria-label="Close"><mat-icon>close</mat-icon></button>
              </div>
              @if (pickedDef(); as pd) {
                <div class="np-row"><span class="k">Invokes</span>
                  <span class="mono v call">{{ pd.beanName || '—' }}<span class="sep">.</span>{{ pd.methodName || '' }}<span class="paren">()</span></span></div>
                @if (pd.subgraph) {
                  <div class="np-row"><span class="k">Subgraph</span><span class="mono v">{{ pd.subgraph }}</span></div>
                }
              }
              <div class="np-row"><span class="k">Ran on</span><span class="mono v">{{ n.executor || '—' }}</span></div>
              <div class="np-actions">
                <button mat-stroked-button (click)="view('Input · ' + n.node, n.inputParam)">
                  <mat-icon>input</mat-icon> Input</button>
                <button mat-stroked-button (click)="view('Output · ' + n.node, n.output)">
                  <mat-icon>output</mat-icon> Output</button>
                @if (n.errorDetail) {
                  <button mat-stroked-button color="warn" (click)="view('Error · ' + n.node, n.errorDetail, 'error')">
                    <mat-icon>error</mat-icon> Error</button>
                }
              </div>
            </div>
          } @else {
            <div class="hint"><mat-icon>touch_app</mat-icon> Click a node to see the bean &amp; method it calls, where it ran, and its input/output.</div>
          }
        </div>

        <div class="side">
          <div class="card sum">
            <h3>Run</h3>
            <dl>
              <div><dt>Triggered by</dt><dd class="mono">{{ d.run.triggeredBy || '—' }}</dd></div>
              <div><dt>Application</dt><dd>{{ d.run.application || '—' }}</dd></div>
              <div><dt>Started</dt><dd>{{ fmt(d.run.startedAt) }}</dd></div>
              <div><dt>Finished</dt><dd>{{ fmt(d.run.finishedAt) }}</dd></div>
              <div><dt>Elapsed</dt><dd>{{ d.run.elapsedMs != null ? d.run.elapsedMs + ' ms' : '—' }}</dd></div>
              <div><dt>Nodes</dt><dd>{{ d.run.nodeCount ?? '—' }}</dd></div>
              @if (d.run.failedNode) { <div><dt>Failed node</dt><dd class="bad">{{ d.run.failedNode }}</dd></div> }
            </dl>
            <div class="sum-actions">
              <button mat-stroked-button (click)="view('Parameter (initial state)', d.run.inputParameter)">
                <mat-icon>login</mat-icon> Parameter</button>
              <button mat-stroked-button (click)="view('Return value (final state)', d.run.returnValue)">
                <mat-icon>logout</mat-icon> Return</button>
              @if (d.run.errorDetail) {
                <button mat-stroked-button color="warn" (click)="view('Error detail', d.run.errorDetail, 'error')">
                  <mat-icon>error</mat-icon> Error</button>
              }
            </div>
          </div>

          @if (d.children.length) {
            <div class="card sum">
              <h3>Subgraph runs</h3>
              @for (c of d.children; track c.runId) {
                <a class="child" [routerLink]="['/dag/runs', c.runId]">
                  <span class="chip" [class]="dagStatusClass(c.status)">{{ c.status }}</span>
                  <span class="c-graph">{{ c.graph }}</span>
                  <span class="mono c-id">{{ shortId(c.runId) }}</span>
                </a>
              }
            </div>
          }
        </div>
      </div>

      <div class="card overflow-hidden nodes">
        <div class="nodes-head">Nodes</div>
        <table mat-table [dataSource]="d.nodes">
          <ng-container matColumnDef="seq">
            <th mat-header-cell *matHeaderCellDef>#</th>
            <td mat-cell *matCellDef="let n">{{ n.seq }}</td>
          </ng-container>
          <ng-container matColumnDef="node">
            <th mat-header-cell *matHeaderCellDef>Node</th>
            <td mat-cell *matCellDef="let n">
              <button type="button" class="linkish" (click)="pick(n.node)">{{ n.node }}</button>
            </td>
          </ng-container>
          <ng-container matColumnDef="target">
            <th mat-header-cell *matHeaderCellDef>Invokes</th>
            <td mat-cell *matCellDef="let n" class="mono call-cell">{{ target(n.node) || '—' }}</td>
          </ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Status</th>
            <td mat-cell *matCellDef="let n"><span class="chip" [class]="dagStatusClass(n.status)">{{ n.status }}</span></td>
          </ng-container>
          <ng-container matColumnDef="executor">
            <th mat-header-cell *matHeaderCellDef>Ran on</th>
            <td mat-cell *matCellDef="let n" class="mono muted">{{ n.executor || '—' }}</td>
          </ng-container>
          <ng-container matColumnDef="elapsed">
            <th mat-header-cell *matHeaderCellDef>Elapsed</th>
            <td mat-cell *matCellDef="let n">{{ n.elapsedMs != null ? n.elapsedMs + ' ms' : '—' }}</td>
          </ng-container>
          <ng-container matColumnDef="io">
            <th mat-header-cell *matHeaderCellDef>I/O</th>
            <td mat-cell *matCellDef="let n">
              <button mat-icon-button matTooltip="Input" (click)="view('Input · ' + n.node, n.inputParam)"><mat-icon>input</mat-icon></button>
              <button mat-icon-button matTooltip="Output" (click)="view('Output · ' + n.node, n.output)"><mat-icon>output</mat-icon></button>
              @if (n.errorDetail) {
                <button mat-icon-button color="warn" matTooltip="Error" (click)="view('Error · ' + n.node, n.errorDetail, 'error')"><mat-icon>error</mat-icon></button>
              }
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="nodeCols"></tr>
          <tr mat-row *matRowDef="let row; columns: nodeCols" [class.hl]="row.node === selectedNode()"></tr>
        </table>
      </div>
    } @else {
      <div class="card empty"><mat-icon>hourglass_empty</mat-icon><p>Loading run…</p></div>
    }
  `,
  styles: [`
    .head { display: flex; align-items: center; gap: 0.7rem; margin-bottom: 0.1rem; }
    .head .page-title { margin: 0; }
    .flex-1 { flex: 1 1 auto; }
    .parent { display: inline-flex; align-items: center; gap: 0.2rem; font-size: 0.8rem; color: #0891a5;
      text-decoration: none; }
    .parent mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .run-id { color: #94a3b8; margin: 0 0 1.1rem 2.6rem; font-size: 0.8rem; }
    .grid { display: grid; grid-template-columns: 1fr 320px; gap: 1.25rem; align-items: start; }
    @media (max-width: 900px) { .grid { grid-template-columns: 1fr; } }
    .graph-card { padding: 0; overflow: hidden; display: flex; flex-direction: column; min-height: 460px; }
    .graph { height: 56vh; }
    .node-panel { border-top: 1px solid #eef2f7; padding: 0.8rem 1rem; }
    .np-head { display: flex; align-items: center; gap: 0.6rem; }
    .np-meta { font-size: 0.78rem; color: #7a8aa0; }
    .np-row { display: flex; gap: 0.6rem; margin-top: 0.5rem; font-size: 0.82rem; }
    .np-row .k { color: #7a8aa0; min-width: 68px; }
    .np-row .v { word-break: break-all; }
    .call { color: #0f2c4d; font-weight: 600; }
    .call .sep { color: #38bdf8; margin: 0 1px; } .call .paren { color: #94a3b8; }
    .call-cell { color: #3d5372; font-size: 0.8rem; }
    .np-actions { display: flex; gap: 0.5rem; margin-top: 0.7rem; flex-wrap: wrap; }
    .hint { border-top: 1px solid #eef2f7; padding: 0.8rem 1rem; color: #94a3b8; font-size: 0.83rem;
      display: flex; align-items: center; gap: 0.4rem; }
    .side { display: flex; flex-direction: column; gap: 1.25rem; }
    .sum { padding: 1rem 1.1rem; }
    .sum h3 { margin: 0 0 0.7rem; font-size: 0.95rem; color: #0f2c4d; }
    .sum dl { margin: 0; display: flex; flex-direction: column; gap: 0.45rem; }
    .sum dl > div { display: flex; justify-content: space-between; gap: 0.8rem; font-size: 0.83rem; }
    .sum dt { color: #7a8aa0; } .sum dd { margin: 0; text-align: right; word-break: break-all; }
    .sum-actions { display: flex; gap: 0.5rem; margin-top: 0.9rem; flex-wrap: wrap; }
    .child { display: flex; align-items: center; gap: 0.55rem; text-decoration: none; color: inherit;
      padding: 0.4rem 0.2rem; border-radius: 8px; }
    .child:hover { background: #f6f9fd; }
    .c-graph { font-weight: 600; font-size: 0.85rem; } .c-id { color: #94a3b8; margin-left: auto; }
    .nodes { margin-top: 1.25rem; }
    .nodes-head { padding: 0.8rem 1.1rem; font-weight: 650; color: #0f2c4d; border-bottom: 1px solid #eef2f7; }
    .linkish { background: 0; border: 0; padding: 0; color: #1565c0; font-weight: 600; cursor: pointer; font: inherit; }
    tr.hl { background: #fff7e6; }
    .empty { padding: 2.5rem; text-align: center; color: #94a3b8; }
    .empty mat-icon { font-size: 2.5rem; width: 2.5rem; height: 2.5rem; }
  `],
})
export class DagRunDetailPage {
  private readonly api = inject(CronsmithApi);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);

  protected readonly detail = signal<DagRunDetail | undefined>(undefined);
  protected readonly definition = signal<DagDefinition | undefined>(undefined);
  protected readonly selectedNode = signal<string>('');

  protected readonly fmt = fmt;
  protected readonly dagStatusClass = dagStatusClass;
  protected readonly nodeCols = ['seq', 'node', 'target', 'status', 'executor', 'elapsed', 'io'];

  /** node name -> status, for colouring the graph. Finished nodes carry their recorded status; nodes
   *  the backend reports in flight are marked RUNNING so the graph pulses them live. */
  protected readonly nodeStatus = computed<Record<string, string>>(() => {
    const map: Record<string, string> = {};
    for (const n of this.detail()?.nodes ?? []) {
      map[n.node] = n.status;
    }
    for (const node of this.detail()?.running ?? []) {
      if (!map[node] || map[node] === 'RUNNING') {
        map[node] = 'RUNNING';
      }
    }
    return map;
  });

  /** The currently-selected node's execution row, if any. */
  protected readonly picked = computed<DagNodeView | undefined>(() =>
    this.detail()?.nodes.find((n) => n.node === this.selectedNode()));

  /** The selected node's definition (bean + method it invokes), joined from the graph definition. */
  protected readonly pickedDef = computed(() =>
    this.definition()?.nodes.find((n) => n.name === this.selectedNode()));

  /** bean·method label for a node name, from the definition — what the node actually calls. */
  protected target(node: string): string {
    const d = this.definition()?.nodes.find((n) => n.name === node);
    if (!d || (!d.beanName && !d.methodName)) {
      return '';
    }
    return `${d.beanName || '?'}.${d.methodName || '?'}`;
  }

  private runId = '';

  constructor() {
    this.route.paramMap.subscribe((p) => {
      this.runId = p.get('runId') ?? '';
      this.selectedNode.set('');
      this.reload();
    });
    // While the run is still in flight, refresh so node statuses fill in live; a finished run is
    // immutable, so we stop polling it.
    interval(3000).pipe(takeUntilDestroyed()).subscribe(() => {
      if (this.detail()?.run.status === 'RUNNING') {
        this.reload();
      }
    });
  }

  protected reload(): void {
    if (!this.runId) {
      return;
    }
    this.api.dagRun(this.runId).subscribe((d) => {
      this.detail.set(d);
      this.loadDefinition(d);
    });
  }

  private loadDefinition(d: DagRunDetail): void {
    const app = d.run.application;
    if (app) {
      this.api.dag(app, d.run.graph).subscribe({
        next: (g) => this.definition.set(g.definition),
        error: () => this.definition.set(synth(d)),
      });
    } else {
      this.definition.set(synth(d));
    }
  }

  protected pick(node: string): void {
    this.selectedNode.set(node);
  }

  protected view(title: string, text: string | null | undefined, tone: 'error' | 'default' = 'default'): void {
    this.dialog.open(TextViewerDialog, { data: { title, text: text || '(empty)', tone }, autoFocus: false });
  }

  protected shortId(id: string | undefined): string {
    return id ? id.slice(0, 8) : '';
  }
}

/** Fall back to a nodes-only definition (no edges) when the live definition is no longer registered. */
function synth(d: DagRunDetail): DagDefinition {
  return {
    graph: d.run.graph,
    inputs: [],
    channels: [],
    nodes: d.nodes.map((n) => ({
      name: n.node, entry: false, trigger: 'ALL', retries: 0, beanName: '', methodName: '', subgraph: '',
    })),
    edges: [],
    conditionals: [],
  };
}
