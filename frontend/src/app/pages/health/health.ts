import { Component, computed, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { CronsmithApi } from '../../core/api.service';
import { poll } from '../../core/util';
import { HealthComponent } from '../../core/models';

interface Row {
  name: string;
  status: string;
  details: { k: string; v: string }[];
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

      <div class="grid gap-4 mt-4" style="grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));">
        @for (c of components(); track c.name) {
          <div class="card p-4">
            <div class="comp-head">
              <span class="comp-name">{{ c.name }}</span>
              <span class="chip" [class]="ok(c.status) ? 'st-running' : 'st-canceled'">{{ c.status }}</span>
            </div>
            @if (c.details.length) {
              <dl class="meta">
                @for (d of c.details; track d.k) {
                  <div><dt>{{ d.k }}</dt><dd class="mono">{{ d.v }}</dd></div>
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
    .overall-label { color: #7a8aa0; font-size: 0.85rem; }
    .overall-status { font-size: 1.6rem; font-weight: 700; color: #0f2c4d; }
    .comp-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 0.75rem; }
    .comp-name { font-weight: 600; color: #0f2c4d; }
    .meta div { display: flex; justify-content: space-between; gap: 1rem; padding: 0.25rem 0; border-bottom: 1px dashed #eef2f7; }
    .meta dt { color: #7a8aa0; font-size: 0.8rem; } .meta dd { margin: 0; text-align: right; word-break: break-all; max-width: 60%; }
  `],
})
export class Health {
  private readonly api = inject(CronsmithApi);
  protected readonly health = poll(() => this.api.health());

  protected readonly components = computed<Row[]>(() => {
    const comps = this.health()?.components ?? {};
    return Object.entries(comps).map(([name, c]) => ({
      name,
      status: c.status,
      details: this.flatten(c),
    }));
  });

  private flatten(c: HealthComponent): { k: string; v: string }[] {
    const out: { k: string; v: string }[] = [];
    const details = c.details ?? {};
    for (const [k, v] of Object.entries(details)) {
      out.push({ k, v: typeof v === 'object' ? JSON.stringify(v) : String(v) });
    }
    return out;
  }

  protected ok(status: string): boolean {
    return status === 'UP';
  }
}
