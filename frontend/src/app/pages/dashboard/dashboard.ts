import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { CronsmithApi } from '../../core/api.service';
import { poll } from '../../core/util';
import { DonutChart, DonutSegment } from '../../shared/donut-chart';
import { Bar, BarChart } from '../../shared/bar-chart';

const STATUS_COLORS: Record<string, string> = {
  STANDBY: '#64748b', SCHEDULED: '#1565c0', RUNNING: '#0f9d58',
  PAUSED: '#b7791f', FINISHED: '#0891b2', CANCELED: '#d93025',
};

@Component({
  selector: 'cf-dashboard',
  imports: [RouterLink, MatIconModule, MatButtonModule, DonutChart, BarChart],
  template: `
    <div class="flex items-start justify-between flex-wrap gap-2">
      <div>
        <h1 class="page-title">Dashboard</h1>
        <p class="page-sub">Live overview of the cronsmith scheduler cluster.</p>
      </div>
      <a mat-flat-button color="primary" routerLink="/tasks/new">
        <mat-icon>add</mat-icon> New task
      </a>
    </div>

    <div class="grid gap-4 mb-6" style="grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));">
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
    </div>

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
        <h2 class="section-title" style="margin:0">Store</h2>
        <a mat-stroked-button routerLink="/cluster"><mat-icon>hub</mat-icon> Cluster details</a>
      </div>
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
  `,
  styles: [`
    .kpi-label { display: flex; align-items: center; gap: 0.4rem; color: #5b6b7f; font-size: 0.85rem; }
    .kpi-label mat-icon { font-size: 1.1rem; width: 1.1rem; height: 1.1rem; color: #1565c0; }
    .kpi-value { font-size: 2rem; font-weight: 700; color: #0f2c4d; margin-top: 0.25rem; }
    .kpi-of { font-size: 1rem; color: #94a3b8; font-weight: 500; margin-left: 0.25rem; }
    .section-title { font-size: 1rem; font-weight: 600; color: #0f2c4d; margin: 0 0 1rem; }
    .store-name { display: flex; align-items: center; gap: 0.5rem; font-size: 1.2rem; font-weight: 700; color: #0f2c4d; margin: 0.75rem 0 1rem; }
    .store-name mat-icon { color: #1565c0; }
    .meta-row { display: flex; justify-content: space-between; gap: 1rem; padding: 0.3rem 0; border-bottom: 1px dashed #eef2f7; }
    .meta-row dt { color: #7a8aa0; font-size: 0.8rem; }
    .meta-row dd { margin: 0; text-align: right; word-break: break-all; }
  `],
})
export class Dashboard {
  private readonly api = inject(CronsmithApi);
  protected readonly stats = poll(() => this.api.stats());
  protected readonly cluster = poll(() => this.api.cluster());
  private readonly taskPage = poll(() => this.api.tasks({ limit: 200 }), 8000);

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
    Object.entries(this.cluster()?.storeMetadata ?? {}).map(([k, v]) => ({ k, v })),
  );
}
