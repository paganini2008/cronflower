import { Component, computed, inject } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { CronsmithApi } from '../../core/api.service';
import { poll } from '../../core/util';

@Component({
  selector: 'cf-cluster',
  imports: [MatTableModule, MatIconModule],
  template: `
    <h1 class="page-title">Cluster</h1>
    <p class="page-sub">Scheduler nodes, leadership, and the backing store. Updated live.</p>

    @if (cluster(); as c) {
      <div class="grid gap-4 mb-4" style="grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));">
        <div class="card p-4"><div class="l">Application</div><div class="v-sm">{{ c.application }}</div></div>
        <div class="card p-4"><div class="l">Nodes</div><div class="v">{{ c.nodeCount }}</div></div>
        <div class="card p-4">
          <div class="l">Sharding</div>
          <div class="v-sm">
            @if (c.sharding) { <span class="chip st-running">enabled</span> }
            @else { <span class="chip st-standby">leader-only</span> }
          </div>
        </div>
        <div class="card p-4">
          <div class="l">Store</div>
          <div class="v-sm">
            {{ c.store }}
            <span class="chip" [class]="c.storeShared ? 'st-scheduled' : 'st-standby'">
              {{ c.storeShared ? 'shared' : 'node-local' }}
            </span>
          </div>
        </div>
      </div>

      <div class="grid gap-4" style="grid-template-columns: 1.5fr 1fr;">
        <div class="card overflow-hidden">
          <div class="card-head">Nodes</div>
          <table mat-table [dataSource]="c.nodes">
            <ng-container matColumnDef="role">
              <th mat-header-cell *matHeaderCellDef>Role</th>
              <td mat-cell *matCellDef="let n">
                @if (n.leader) { <span class="chip st-scheduled"><mat-icon class="ci">star</mat-icon> leader</span> }
                @else { <span class="chip st-standby">{{ n.role }}</span> }
              </td>
            </ng-container>
            <ng-container matColumnDef="node">
              <th mat-header-cell *matHeaderCellDef>Node</th>
              <td mat-cell *matCellDef="let n">
                <div><strong>{{ n.host }}:{{ n.httpPort ?? n.port }}</strong> @if (n.self) { <span class="chip st-finished">this</span> }</div>
                <div class="mono muted text-xs">{{ n.id }}</div>
              </td>
            </ng-container>
            <ng-container matColumnDef="name">
              <th mat-header-cell *matHeaderCellDef>App</th>
              <td mat-cell *matCellDef="let n">{{ n.name }}</td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns"></tr>
          </table>
        </div>

        <div class="card p-5">
          <div class="card-head" style="padding:0 0 0.75rem"><mat-icon class="db">database</mat-icon> Store · {{ c.store }}</div>
          <dl class="meta">
            <div><dt>Replicated</dt><dd>{{ c.storeReplicated }}</dd></div>
            <div><dt>Shared</dt><dd>{{ c.storeShared }}</dd></div>
            @for (m of storeMeta(); track m.k) {
              <div><dt>{{ m.k }}</dt><dd class="mono">{{ m.v }}</dd></div>
            }
          </dl>
        </div>
      </div>
    } @else { <p class="muted">Loading…</p> }
  `,
  styles: [`
    .l { color: #7a8aa0; font-size: 0.8rem; } .v { font-size: 1.8rem; font-weight: 700; color: #0f2c4d; }
    .v-sm { font-size: 1.05rem; font-weight: 700; color: #0f2c4d; margin-top: 0.25rem; }
    .card-head { padding: 1rem 1.25rem; font-weight: 600; color: #0f2c4d; border-bottom: 1px solid #eef2f7; display: flex; align-items: center; gap: 0.4rem; }
    .db { color: #1565c0; }
    .ci { font-size: 0.9rem; width: 0.9rem; height: 0.9rem; }
    .meta div { display: flex; justify-content: space-between; gap: 1rem; padding: 0.4rem 0; border-bottom: 1px dashed #eef2f7; }
    .meta dt { color: #7a8aa0; font-size: 0.82rem; } .meta dd { margin: 0; text-align: right; word-break: break-all; }
  `],
})
export class Cluster {
  private readonly api = inject(CronsmithApi);
  protected readonly cluster = poll(() => this.api.cluster());
  protected readonly columns = ['role', 'node', 'name'];

  protected readonly storeMeta = computed(() =>
    Object.entries(this.cluster()?.storeMetadata ?? {}).map(([k, v]) => ({ k, v })),
  );
}
