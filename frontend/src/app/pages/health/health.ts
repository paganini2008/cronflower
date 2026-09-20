import { Component, computed, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { CronsmithApi } from '../../core/api.service';
import { poll } from '../../core/util';
import { HealthComponent } from '../../core/models';

interface Detail {
  k: string;
  v: string;
  /** True when v is pretty-printed JSON that should render in a scrollable block, not inline. */
  pre: boolean;
}

interface Row {
  name: string;
  status: string;
  details: Detail[];
  /** True when the card holds a JSON block, so it spans the full grid width for room. */
  wide: boolean;
}

@Component({
  selector: 'cf-health',
  imports: [MatIconModule],
  template: `
    <h1 class="page-title">System Health</h1>
    <p class="page-sub">Spring Boot Actuator health, including the scheduler cluster. Updated live.</p>

    @if (health(); as h) {
      <div class="card overall" [class]="ok(h.status) ? 'up' : 'down'">
        <mat-icon>{{ ok(h.status) ? 'check_circle' : 'error' }}</mat-icon>
        <div>
          <div class="overall-label">Overall status</div>
          <div class="overall-status">{{ h.status }}</div>
        </div>
      </div>

      <h2 class="sub-head">Nodes <span class="muted">· live JVM memory per scheduler node</span></h2>
      @if (nodes(); as ns) {
        @if (ns.length === 0) { <p class="muted">No nodes.</p> }
        <div class="grid gap-4 nodes-grid">
          @for (n of ns; track n.id || n.host) {
            <div class="card p-4 node-card" [class.down]="!ok(n.status)">
              <div class="node-head">
                <span class="ndot" [class.up]="ok(n.status)" [class.down]="!ok(n.status)"></span>
                <span class="node-host mono">{{ n.host }}<span class="muted">:{{ n.httpPort }}</span></span>
                @if (n.leader) { <span class="chip st-scheduled">leader</span> }
                <span class="flex-1"></span>
                <span class="muted node-status">{{ n.status }}</span>
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
                  @if (n.uptimeMs != null) { <span>· up {{ uptime(n.uptimeMs) }}</span> }
                </div>
              } @else if (n.error) {
                <div class="mem-text down-text">unreachable ({{ n.error }})</div>
              }
            </div>
          }
        </div>
      } @else { <p class="muted">Loading node memory…</p> }

      <h2 class="sub-head">Components</h2>

      <div class="grid gap-4 mt-4" style="grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));">
        @for (c of components(); track c.name) {
          <div class="card p-4" [class.wide]="c.wide">
            <div class="comp-head">
              <span class="comp-name">{{ c.name }}</span>
              <span class="chip" [class]="ok(c.status) ? 'st-running' : 'st-canceled'">{{ c.status }}</span>
            </div>
            @if (c.details.length) {
              <dl class="meta">
                @for (d of c.details; track d.k) {
                  @if (d.pre) {
                    <div class="pre-row"><dt>{{ d.k }}</dt><pre class="mono json">{{ d.v }}</pre></div>
                  } @else {
                    <div><dt>{{ d.k }}</dt><dd class="mono">{{ d.v }}</dd></div>
                  }
                }
              </dl>
            }
          </div>
        }
      </div>
    } @else { <p class="muted">Loading…</p> }
  `,
  styles: [`
    .overall { display: flex; align-items: center; gap: 1rem; padding: 1.25rem 1.5rem; }
    .overall mat-icon { font-size: 2.5rem; width: 2.5rem; height: 2.5rem; }
    .overall.up { border-left: 5px solid #0f9d58; } .overall.up mat-icon { color: #0f9d58; }
    .overall.down { border-left: 5px solid #d93025; } .overall.down mat-icon { color: #d93025; }
    .overall-label { color: #3d5372; font-size: 0.85rem; }
    .overall-status { font-size: 1.6rem; font-weight: 700; color: #0f2c4d; }
    .sub-head { font-size: 1rem; font-weight: 600; color: #0f2c4d; margin: 1.5rem 0 0.75rem; }
    .nodes-grid { grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); }
    .node-card.down { border-left: 4px solid #d93025; }
    .node-head { display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.6rem; }
    .ndot { width: 9px; height: 9px; border-radius: 50%; flex: 0 0 auto; }
    .ndot.up { background: #0f9d58; } .ndot.down { background: #d93025; }
    .node-host { font-size: 0.9rem; color: #0f2c4d; }
    .node-status { font-size: 0.75rem; }
    .flex-1 { flex: 1 1 auto; }
    .mem-bar { height: 9px; border-radius: 999px; background: #e8eef6; overflow: hidden; }
    .mem-fill { display: block; height: 100%; background: #1565c0; border-radius: 999px; transition: width .4s; }
    .mem-fill.hot { background: #d93025; }
    .mem-text { font-size: 0.78rem; margin-top: 0.4rem; }
    .mem-pct { font-weight: 600; color: #3d5372; }
    .down-text { font-size: 0.78rem; margin-top: 0.4rem; color: #d93025; }
    .comp-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 0.75rem; }
    .comp-name { font-weight: 600; color: #0f2c4d; }
    /* Scalar rows keep key and value close together (capped width) so a wide card never flings the
       value to a far-off right edge; only JSON blocks (.pre-row) span the full card width. */
    .meta > div { display: flex; justify-content: space-between; gap: 1rem; padding: 0.25rem 0; border-bottom: 1px dashed #eef2f7; max-width: 560px; }
    .meta dt { color: #3d5372; font-size: 0.8rem; flex: 0 0 auto; } .meta dd { margin: 0; text-align: right; word-break: break-word; min-width: 0; }
    /* A card holding a JSON block spans the full grid width and lets the block scroll within bounds. */
    .card.wide { grid-column: 1 / -1; }
    .meta .pre-row { display: block; max-width: none; padding: 0.35rem 0; border-bottom: 1px dashed #eef2f7; }
    .meta .pre-row dt { display: block; margin-bottom: 0.35rem; }
    .meta pre.json { margin: 0; max-width: 100%; overflow: auto; max-height: 260px; white-space: pre;
      background: #f6f8fb; border: 1px solid #eef2f7; border-radius: 6px; padding: 0.5rem 0.6rem;
      font-size: 0.75rem; line-height: 1.45; color: #33415a; }
  `],
})
export class Health {
  private readonly api = inject(CronsmithApi);
  protected readonly health = poll(() => this.api.health());
  protected readonly nodes = poll(() => this.api.nodesHealth(), 8000);

  /** Compact uptime, e.g. "3h 12m" or "45s". */
  protected uptime(ms: number): string {
    const s = Math.floor(ms / 1000);
    if (s < 60) { return `${s}s`; }
    const m = Math.floor(s / 60);
    if (m < 60) { return `${m}m`; }
    const h = Math.floor(m / 60);
    const d = Math.floor(h / 24);
    return d > 0 ? `${d}d ${h % 24}h` : `${h}h ${m % 60}m`;
  }

  protected readonly components = computed<Row[]>(() => {
    const comps = this.health()?.components ?? {};
    return Object.entries(comps).map(([name, c]) => {
      const details = this.flatten(c);
      return { name, status: c.status, details, wide: details.some((d) => d.pre) };
    });
  });

  private flatten(c: HealthComponent): Detail[] {
    const out: Detail[] = [];
    const details = c.details ?? {};
    for (const [k, v] of Object.entries(details)) {
      if (v !== null && typeof v === 'object') {
        out.push({ k, v: JSON.stringify(v, null, 2), pre: true }); // pretty-print objects/arrays
      } else {
        out.push({ k, v: String(v), pre: false });
      }
    }
    return out;
  }

  protected ok(status: string): boolean {
    return status === 'UP';
  }
}
