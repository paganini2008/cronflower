import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { of } from 'rxjs';
import { CronsmithApi } from '../../core/api.service';
import { CronflowFeature } from '../../core/cronflow-feature';
import { DagGraphView, DagRunView } from '../../core/models';
import { orderedEntries, poll } from '../../core/util';
import { DonutChart, DonutSegment } from '../../shared/donut-chart';
import { Bar, BarChart } from '../../shared/bar-chart';

const STATUS_COLORS: Record<string, string> = {
  STANDBY: '#64748b', SCHEDULED: '#1565c0', RUNNING: '#0f9d58',
  PAUSED: '#b7791f', FINISHED: '#0891b2', CANCELED: '#d93025',
};

const DAG_STATUS_COLORS: Record<string, string> = {
  RUNNING: '#1565c0', SUCCESS: '#0f9d58', FAILED: '#d93025', SKIPPED: '#94a3b8',
};

@Component({
  selector: 'cf-dashboard',
  imports: [RouterLink, MatIconModule, MatButtonModule, DonutChart, BarChart],
  template: `
    <div class="flex items-start justify-between flex-wrap gap-2">
      <div>
        <h1 class="page-title">Dashboard</h1>
        <p class="page-sub">Live overview of the scheduler cluster@if (hasDag()) { and DAG orchestration}.</p>
      </div>
    </div>

    <div class="grid gap-4 mb-6" style="grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));">
      <div class="card p-4">
        <div class="kpi-label"><mat-icon>list_alt</mat-icon> Tasks</div>
        <div class="kpi-value">{{ stats()?.taskTotal ?? '—' }}</div>
      </div>
      <div class="card p-4">
        <div class="kpi-label"><mat-icon>memory</mat-icon> Executors live</div>
        <div class="kpi-value">
          {{ stats()?.executorsLive ?? '—' }}<span class="kpi-of">/ {{ stats()?.executorsTotal ?? '—' }}</span>
        </div>
      </div>
      <div class="card p-4">
        <div class="kpi-label"><mat-icon>hub</mat-icon> Cluster nodes</div>
        <div class="kpi-value">{{ cluster()?.nodeCount ?? '—' }}</div>
      </div>
      <div class="card p-4">
        <div class="kpi-label"><mat-icon>call_split</mat-icon> Sharding</div>
        <div class="kpi-value">
          @if (cluster()?.sharding) { <span class="ok">on</span> } @else { <span class="muted">off</span> }
        </div>
      </div>
      @if (hasDag()) {
        <div class="card p-4">
          <div class="kpi-label"><mat-icon>account_tree</mat-icon> Workflows</div>
          <div class="kpi-value">{{ workflows()?.total ?? '—' }}</div>
        </div>
        <div class="card p-4">
          <div class="kpi-label"><mat-icon>timeline</mat-icon> DAG runs</div>
          <div class="kpi-value">{{ runsPage()?.total ?? '—' }}</div>
        </div>
        <div class="card p-4">
          <div class="kpi-label"><mat-icon>bolt</mat-icon> Running now</div>
          <div class="kpi-value">
            @if (dagRunningCount() > 0) { <span class="ok">{{ dagRunningCount() }}</span> }
            @else { <span class="muted">0</span> }
          </div>
        </div>
        <div class="card p-4">
          <div class="kpi-label"><mat-icon>check_circle</mat-icon> Success rate</div>
          <div class="kpi-value">{{ dagSuccessRate() }}</div>
        </div>
      }
    </div>

    <h2 class="group-title"><mat-icon>schedule</mat-icon> Scheduler</h2>
    <div class="grid gap-4" style="grid-template-columns: 1fr 1fr;">
      <div class="card p-5">
        <h2 class="section-title">Tasks by status</h2>
        <cf-donut [segments]="statusSegments()" caption="tasks" />
      </div>
      <div class="card p-5">
        <h2 class="section-title">Busiest tasks <span class="muted">· by run count</span></h2>
        <cf-bars [items]="busiest()" />
      </div>
    </div>

    <div class="card p-5 mt-4">
      <div class="flex items-center justify-between">
        <h2 class="section-title" style="margin:0">Running tasks <span class="muted">· top 5</span></h2>
        <a mat-stroked-button routerLink="/tasks"><mat-icon>list_alt</mat-icon> All tasks</a>
      </div>
      @if (runningTasks()?.items?.length) {
        <div class="runs">
          @for (t of runningTasks()!.items; track t.taskGroup + '/' + t.taskName) {
            <a class="run-row link" [routerLink]="['/tasks', t.taskGroup, t.taskName]">
              <span class="chip st-running">RUNNING</span>
              <span class="run-graph">{{ t.taskGroup }} / {{ t.taskName }}</span>
              <span class="run-time muted">{{ t.taskType }}</span>
            </a>
          }
        </div>
      } @else { <p class="muted">No tasks are running right now.</p> }
    </div>

    @if (hasDag()) {
      <h2 class="group-title mt-6"><mat-icon>account_tree</mat-icon> DAG orchestration</h2>
      <div class="grid gap-4" style="grid-template-columns: 1fr 1fr;">
        <div class="card p-5">
          <h2 class="section-title">DAG runs by status</h2>
          @if (dagStatusSegments().length) {
            <cf-donut [segments]="dagStatusSegments()" caption="runs" />
          } @else { <p class="muted">No DAG runs yet.</p> }
        </div>
        <div class="card p-5">
          <h2 class="section-title">Busiest workflows <span class="muted">· by run count</span></h2>
          @if (topWorkflows().length) {
            <cf-bars [items]="topWorkflows()" />
          } @else { <p class="muted">No DAG runs yet.</p> }
        </div>
      </div>

      <div class="card p-5 mt-4">
        <div class="flex items-center justify-between">
          <h2 class="section-title" style="margin:0">Running now <span class="muted">· top 5</span></h2>
          <a mat-stroked-button routerLink="/dag/runs"><mat-icon>timeline</mat-icon> All runs</a>
        </div>
        @if (runningRuns().length) {
          <div class="runs">
            @for (r of runningRuns(); track r.runId) {
              <a class="run-row link" [routerLink]="['/dag/runs', r.runId]">
                <span class="chip st-scheduled">RUNNING</span>
                <span class="run-graph">{{ r.graph }}</span>
                <span class="run-node muted">{{ r.nodeCount ?? '—' }} nodes</span>
                <span class="run-time muted">click to watch</span>
              </a>
            }
          </div>
        } @else { <p class="muted">No DAG runs are in flight right now.</p> }
      </div>
    }

    <h2 class="group-title mt-6"><mat-icon>dns</mat-icon> System</h2>
    <div class="grid gap-4" style="grid-template-columns: 1fr 1fr;">
      <div class="card p-5">
        <h2 class="section-title">Health</h2>
        @if (health(); as h) {
          <div class="health-overall" [class.up]="healthUp(h.status)" [class.down]="!healthUp(h.status)">
            <mat-icon>{{ healthUp(h.status) ? 'check_circle' : 'error' }}</mat-icon>
            <span>{{ h.status }}</span>
          </div>
          <div class="health-list">
            @for (c of healthComponents(); track c.name) {
              <div class="health-row">
                <span class="hc-dot" [class.up]="healthUp(c.status)" [class.down]="!healthUp(c.status)"></span>
                <span class="hc-name">{{ c.name }}</span>
                <span class="hc-status muted">{{ c.status }}</span>
              </div>
            }
          </div>
        } @else { <p class="muted">Loading…</p> }
      </div>

      <div class="card p-5">
        <h2 class="section-title">Store</h2>
      @if (cluster(); as c) {
        <div class="store-name">
          <mat-icon>database</mat-icon> {{ c.store }}
          <span class="chip" [class]="c.storeShared ? 'st-scheduled' : 'st-standby'">
            {{ c.storeShared ? 'shared' : 'node-local' }}
          </span>
        </div>
        <div class="grid gap-x-8" style="grid-template-columns: 1fr 1fr;">
          @for (m of storeMeta(); track m.k) {
            <div class="meta-row"><dt>{{ m.k }}</dt><dd class="mono">{{ m.v }}</dd></div>
          }
        </div>
      } @else { <p class="muted">Loading…</p> }
      </div>
    </div>

    <div class="card p-5 mt-4">
      <h2 class="section-title">Nodes <span class="muted">· live JVM memory per scheduler node</span></h2>
      @if (nodes(); as ns) {
        @if (ns.length === 0) { <p class="muted">No nodes.</p> }
        <div class="nodes-grid">
          @for (n of ns; track n.id || n.host) {
            <div class="node-card" [class.down]="!healthUp(n.status)">
              <div class="node-head">
                <span class="hc-dot" [class.up]="healthUp(n.status)" [class.down]="!healthUp(n.status)"></span>
                <span class="node-host mono">{{ n.host }}<span class="muted">:{{ n.httpPort }}</span></span>
                @if (n.leader) { <span class="chip st-scheduled">leader</span> }
                <span class="flex-1"></span>
                <span class="node-status muted">{{ n.status }}</span>
              </div>
              @if (n.memory; as m) {
                <div class="mem-bar">
                  <span class="mem-fill" [class.hot]="(m.usagePct ?? 0) > 85"
                        [style.width.%]="m.usagePct ?? 0"></span>
                </div>
                <div class="mem-text muted">
                  heap {{ m.usedMb }} / {{ m.maxMb ?? '—' }} MB
                  @if (m.usagePct != null) { <span class="mem-pct">· {{ m.usagePct }}%</span> }
                  @if (n.processors) { <span>· {{ n.processors }} vCPU</span> }
                </div>
              } @else if (n.error) {
                <div class="mem-text down-text">unreachable ({{ n.error }})</div>
              }
            </div>
          }
        </div>
      } @else { <p class="muted">Loading…</p> }
    </div>
  `,
  styles: [`
    .kpi-label { display: flex; align-items: center; gap: 0.4rem; color: #3d5372; font-size: 0.85rem; }
    .kpi-label mat-icon { font-size: 1.1rem; width: 1.1rem; height: 1.1rem; color: #1565c0; }
    .kpi-value { font-size: 2rem; font-weight: 700; color: #0f2c4d; margin-top: 0.25rem; }
    .kpi-of { font-size: 1rem; color: #3d5372; font-weight: 500; margin-left: 0.25rem; }
    .group-title { display: flex; align-items: center; gap: 0.5rem; font-size: 1.05rem; font-weight: 600;
      color: #0f2c4d; margin: 0 0 1rem; padding-left: 0.6rem; border-left: 3px solid #1565c0; }
    .group-title mat-icon { font-size: 1.15rem; width: 1.15rem; height: 1.15rem; color: #1565c0; }
    .section-title { font-size: 1rem; font-weight: 600; color: #0f2c4d; margin: 0 0 1rem; }
    .store-name { display: flex; align-items: center; gap: 0.5rem; font-size: 1.2rem; font-weight: 700; color: #0f2c4d; margin: 0.25rem 0 1rem; }
    .store-name mat-icon { color: #1565c0; }
    .meta-row { display: flex; justify-content: space-between; gap: 1rem; padding: 0.3rem 0; border-bottom: 1px dashed #eef2f7; }
    .meta-row dt { color: #3d5372; font-size: 0.8rem; }
    .meta-row dd { margin: 0; text-align: right; word-break: break-all; }
    .runs { display: flex; flex-direction: column; gap: 0.1rem; margin-top: 0.5rem; }
    .run-row { display: flex; align-items: center; gap: 0.6rem; padding: 0.4rem 0; border-bottom: 1px dashed #eef2f7; }
    .run-row.link { text-decoration: none; cursor: pointer; padding: 0.5rem; margin: 0 -0.5rem; border-radius: 6px; border-bottom: 1px dashed #eef2f7; }
    .run-row.link:hover { background: #f4f7fb; }
    .run-graph { font-weight: 600; color: #0f2c4d; flex: 1 1 auto; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .run-node { font-size: 0.78rem; white-space: nowrap; }
    .run-time { font-size: 0.78rem; white-space: nowrap; }
    .mt-6 { margin-top: 1.5rem; }
    .health-overall { display: flex; align-items: center; gap: 0.5rem; font-size: 1.2rem; font-weight: 700; margin: 0.25rem 0 1rem; }
    .health-overall.up { color: #0f9d58; }
    .health-overall.down { color: #d93025; }
    .health-list { display: flex; flex-direction: column; gap: 0.1rem; }
    .health-row { display: flex; align-items: center; gap: 0.6rem; padding: 0.35rem 0; border-bottom: 1px dashed #eef2f7; }
    .hc-dot { width: 8px; height: 8px; border-radius: 50%; flex: 0 0 auto; }
    .hc-dot.up { background: #0f9d58; }
    .hc-dot.down { background: #d93025; }
    .mt-4 { margin-top: 1rem; }
    .flex-1 { flex: 1 1 auto; }
    .nodes-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 0.75rem; }
    .node-card { border: 1px solid #eef2f7; border-radius: 8px; padding: 0.7rem 0.8rem; background: #fbfdff; }
    .node-card.down { border-color: #f3c2bd; background: #fdf5f4; }
    .node-head { display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.5rem; }
    .node-host { font-size: 0.85rem; color: #0f2c4d; }
    .node-status { font-size: 0.75rem; }
    .mem-bar { height: 8px; border-radius: 999px; background: #e8eef6; overflow: hidden; }
    .mem-fill { display: block; height: 100%; background: #1565c0; border-radius: 999px; transition: width .4s; }
    .mem-fill.hot { background: #d93025; }
    .mem-text { font-size: 0.75rem; margin-top: 0.35rem; }
    .mem-pct { font-weight: 600; color: #3d5372; }
    .down-text { font-size: 0.75rem; margin-top: 0.35rem; color: #d93025; }
    .hc-name { flex: 1 1 auto; color: #0f2c4d; font-weight: 500; }
    .hc-status { font-size: 0.78rem; }
  `],
})
export class Dashboard {
  private readonly api = inject(CronsmithApi);
  private readonly feature = inject(CronflowFeature);

  protected readonly hasDag = computed(() => this.feature.available() === true);

  protected readonly stats = poll(() => this.api.stats());
  protected readonly cluster = poll(() => this.api.cluster());
  protected readonly health = poll(() => this.api.health(), 8000);
  protected readonly nodes = poll(() => this.api.nodesHealth(), 8000);
  private readonly taskPage = poll(() => this.api.tasks({ limit: 200 }), 8000);

  // cronflow (DAG) — only fetched once the add-on is detected, so a cronsmith-only backend stays quiet.
  protected readonly workflows = poll(
    () => (this.feature.available() === true
      ? this.api.dags({ limit: 1 })
      : of({ total: 0, items: [] as DagGraphView[] })), 8000);
  protected readonly runsPage = poll(
    () => (this.feature.available() === true
      ? this.api.dagRuns({ limit: 200 })
      : of({ total: 0, items: [] as DagRunView[] })), 6000);

  constructor() {
    this.feature.ensure();
  }

  protected readonly statusSegments = computed<DonutSegment[]>(() => {
    const by = this.stats()?.tasksByStatus ?? {};
    return Object.entries(by)
      .filter(([name, v]) => name !== 'NONE' && v > 0)
      .map(([name, value]) => ({ label: name, value, color: STATUS_COLORS[name] ?? '#94a3b8' }));
  });

  protected readonly busiest = computed<Bar[]>(() => {
    const items = this.taskPage()?.items ?? [];
    return [...items]
      .sort((a, b) => b.runCount - a.runCount)
      .slice(0, 6)
      .map((t) => ({
        label: `${t.taskGroup} / ${t.taskName}`,
        value: t.runCount,
        sub: t.failureCount ? `${t.failureCount} failed` : undefined,
      }));
  });

  protected readonly storeMeta = computed(() =>
    orderedEntries(this.cluster()?.storeMetadata),
  );

  protected readonly healthComponents = computed(() =>
    Object.entries(this.health()?.components ?? {}).map(([k, v]) => ({ name: k, status: v.status })),
  );

  protected healthUp(status: string | undefined): boolean {
    return (status ?? '').toUpperCase() === 'UP';
  }

  // Top running items (clickable through to a detail view). Polled a bit faster so live runs surface.
  protected readonly runningTasks = poll(() => this.api.tasks({ status: 'RUNNING', limit: 5 }), 4000);

  private readonly runs = computed<DagRunView[]>(() => this.runsPage()?.items ?? []);

  protected readonly runningRuns = computed<DagRunView[]>(
    () => this.runs().filter((r) => r.status === 'RUNNING').slice(0, 5));

  protected readonly dagRunningCount = computed(
    () => this.runs().filter((r) => r.status === 'RUNNING').length);

  protected readonly dagSuccessRate = computed(() => {
    const done = this.runs().filter((r) => r.status === 'SUCCESS' || r.status === 'FAILED');
    if (done.length === 0) {
      return '—';
    }
    const ok = done.filter((r) => r.status === 'SUCCESS').length;
    return Math.round((ok / done.length) * 100) + '%';
  });

  protected readonly dagStatusSegments = computed<DonutSegment[]>(() => {
    const counts: Record<string, number> = {};
    for (const r of this.runs()) {
      counts[r.status] = (counts[r.status] ?? 0) + 1;
    }
    return Object.entries(counts)
      .filter(([, v]) => v > 0)
      .map(([label, value]) => ({ label, value, color: DAG_STATUS_COLORS[label] ?? '#94a3b8' }));
  });

  protected readonly topWorkflows = computed<Bar[]>(() => {
    const counts: Record<string, number> = {};
    for (const r of this.runs()) {
      counts[r.graph] = (counts[r.graph] ?? 0) + 1;
    }
    return Object.entries(counts)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 6)
      .map(([label, value]) => ({ label, value }));
  });

}
