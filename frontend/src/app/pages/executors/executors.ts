import { Component, inject } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { CronsmithApi } from '../../core/api.service';
import { fmt, poll } from '../../core/util';

@Component({
  selector: 'cf-executors',
  imports: [MatTableModule, MatIconModule],
  template: `
    <h1 class="page-title">Executors</h1>
    <p class="page-sub">Worker instances registered with the scheduler. Updated live.</p>

    <div class="card overflow-hidden">
      <table mat-table [dataSource]="executors() ?? []">
        <ng-container matColumnDef="health">
          <th mat-header-cell *matHeaderCellDef>Health</th>
          <td mat-cell *matCellDef="let e">
            <span class="chip" [class]="e.healthy ? 'st-running' : 'st-canceled'">
              {{ e.healthy ? 'live' : 'stale' }}
            </span>
          </td>
        </ng-container>
        <ng-container matColumnDef="application">
          <th mat-header-cell *matHeaderCellDef>Application</th>
          <td mat-cell *matCellDef="let e"><strong>{{ e.application }}</strong></td>
        </ng-container>
        <ng-container matColumnDef="instanceId">
          <th mat-header-cell *matHeaderCellDef>Instance</th>
          <td mat-cell *matCellDef="let e" class="mono">{{ e.instanceId }}</td>
        </ng-container>
        <ng-container matColumnDef="weight">
          <th mat-header-cell *matHeaderCellDef>Weight</th>
          <td mat-cell *matCellDef="let e"><span class="chip st-scheduled">{{ e.weight }}</span></td>
        </ng-container>
        <ng-container matColumnDef="runUrl">
          <th mat-header-cell *matHeaderCellDef>Run URL</th>
          <td mat-cell *matCellDef="let e" class="mono muted">{{ e.runUrl }}</td>
        </ng-container>
        <ng-container matColumnDef="lastSeen">
          <th mat-header-cell *matHeaderCellDef>Last heartbeat</th>
          <td mat-cell *matCellDef="let e">{{ fmt(e.lastSeen) }}</td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="columns"></tr>
        <tr mat-row *matRowDef="let row; columns: columns"></tr>
      </table>
      @if ((executors()?.length ?? 0) === 0) {
        <div class="empty"><mat-icon>memory</mat-icon><p>No executors registered.</p></div>
      }
    </div>
  `,
  styles: [`
    .empty { padding: 2.5rem; text-align: center; color: #94a3b8; }
    .empty mat-icon { font-size: 2.5rem; width: 2.5rem; height: 2.5rem; }
  `],
})
export class Executors {
  private readonly api = inject(CronsmithApi);
  protected readonly executors = poll(() => this.api.executors());
  protected readonly columns = ['health', 'application', 'instanceId', 'weight', 'runUrl', 'lastSeen'];
  protected readonly fmt = fmt;
}
