import { Component, OnInit, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { CronsmithApi, ConfigProps } from '../../core/api.service';

interface KV { k: string; v: string; }
interface Section { prefix: string; rows: KV[]; }

/**
 * System > Settings — the effective server configuration, read from Spring Boot Actuator's
 * {@code /actuator/configprops}. We keep the app's own @ConfigurationProperties beans (the
 * {@code cronsmith.*} and {@code cronflow.*} prefixes), flatten each into dotted key/value rows,
 * and show them grouped by prefix. Read-only; Spring masks any secret before it reaches us.
 */
@Component({
  selector: 'cf-settings',
  imports: [MatIconModule, MatProgressBarModule],
  template: `
    <h2 class="page-title">Settings</h2>
    <p class="page-sub">Effective server configuration (properties). Read-only; secrets are masked.</p>

    @if (loading()) { <mat-progress-bar mode="indeterminate" /> }
    @if (error()) {
      <div class="card p-5 muted">Could not load configuration. This view is available to admins only.</div>
    }
    @if (sections(); as secs) {
      @if (secs.length === 0) {
        <div class="card p-5 muted">No configuration properties to show.</div>
      }
      @for (s of secs; track s.prefix) {
        <div class="card p-5 mb-4">
          <div class="card-head"><mat-icon class="db">tune</mat-icon> {{ s.prefix }}</div>
          <dl class="meta">
            @for (r of s.rows; track r.k) {
              <div><dt>{{ r.k }}</dt><dd class="mono">{{ r.v }}</dd></div>
            }
          </dl>
        </div>
      }
    }
  `,
  styles: [`
    .card-head { padding: 0 0 0.75rem; font-weight: 600; color: #0f2c4d; display: flex;
      align-items: center; gap: 0.4rem; }
    .card-head .db { color: var(--cf-blue); }
    .meta { margin: 0; }
    .meta div { display: flex; justify-content: space-between; gap: 1.5rem; padding: 0.4rem 0;
      border-bottom: 1px dashed #eef2f7; }
    .meta div:last-child { border-bottom: 0; }
    .meta dt { color: var(--cf-muted); }
    .meta dd { margin: 0; text-align: right; word-break: break-all; }
  `],
})
export class Settings implements OnInit {
  private readonly api = inject(CronsmithApi);
  protected readonly sections = signal<Section[] | undefined>(undefined);
  protected readonly loading = signal(false);
  protected readonly error = signal(false);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.api.configProps().subscribe({
      next: (cp) => { this.sections.set(this.build(cp)); this.loading.set(false); },
      error: () => { this.error.set(true); this.loading.set(false); },
    });
  }

  /** Keep the app's own property beans and flatten each to sorted key/value rows, grouped by prefix. */
  private build(cp: ConfigProps): Section[] {
    // Operational config only: the cronsmith/cronflow server properties. Auth config (cronflow.security)
    // is excluded — it is secrets and account setup, not the deployment tuning this view is for.
    const wanted = (p?: string) => !!p
      && (p.startsWith('cronsmith') || p.startsWith('cronflow'))
      && !p.startsWith('cronflow.security');
    const byPrefix = new Map<string, KV[]>();
    for (const ctx of Object.values(cp.contexts ?? {})) {
      for (const bean of Object.values(ctx.beans ?? {})) {
        if (!wanted(bean.prefix)) { continue; }
        const rows = this.flatten(bean.properties ?? {});
        if (!rows.length) { continue; }
        byPrefix.set(bean.prefix!, (byPrefix.get(bean.prefix!) ?? []).concat(rows));
      }
    }
    return [...byPrefix.entries()]
      .map(([prefix, rows]) => ({ prefix, rows: rows.sort((a, b) => a.k.localeCompare(b.k)) }))
      .sort((a, b) => a.prefix.localeCompare(b.prefix));
  }

  private flatten(obj: Record<string, unknown>, base = ''): KV[] {
    const rows: KV[] = [];
    for (const [k, val] of Object.entries(obj)) {
      rows.push(...this.flattenValue(base ? `${base}.${k}` : k, val));
    }
    return rows;
  }

  private flattenValue(key: string, val: unknown): KV[] {
    if (val === null || val === undefined) { return [{ k: key, v: '—' }]; }
    if (Array.isArray(val)) {
      if (val.length === 0) { return [{ k: key, v: '[]' }]; }
      if (val.every((x) => x === null || typeof x !== 'object')) {
        return [{ k: key, v: val.join(', ') }];
      }
      return val.flatMap((x, i) => this.flattenValue(`${key}[${i}]`, x));
    }
    if (typeof val === 'object') { return this.flatten(val as Record<string, unknown>, key); }
    return [{ k: key, v: String(val) }];
  }
}
