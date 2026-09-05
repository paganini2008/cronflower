import { Component, effect, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';
import { CronsmithApi } from '../../core/api.service';
import { HTTP_METHODS, MISFIRE_POLICIES, TaskMetadata } from '../../core/models';
import { fromInputDateTime, toInputDateTime, tzLabel } from '../../core/util';
import { ScheduleBuilderDialog } from '../../shared/schedule-builder/schedule-builder-dialog';

@Component({
  selector: 'cf-task-form',
  imports: [
    RouterLink, ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatButtonModule, MatIconModule, MatTooltipModule,
  ],
  template: `
    <a routerLink="/tasks" class="back"><mat-icon>arrow_back</mat-icon> Tasks</a>
    <h1 class="page-title mt-2">{{ editing() ? 'Edit task' : 'New task' }}</h1>
    <p class="page-sub">Define what runs, when, and how it is invoked.</p>

    <form [formGroup]="form" (ngSubmit)="submit()" class="card form-card">

      <section class="form-section">
        <div class="section-head">
          <mat-icon>badge</mat-icon>
          <div>
            <div class="section-title">Identity</div>
            <div class="section-desc">Group and name together identify the task.</div>
          </div>
        </div>
        <div class="field-grid cols-2">
          <mat-form-field appearance="outline">
            <mat-label>Group</mat-label>
            <input matInput formControlName="taskGroup" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Name</mat-label>
            <input matInput formControlName="taskName" />
          </mat-form-field>
        </div>
      </section>

      <section class="form-section">
        <div class="section-head">
          <mat-icon>bolt</mat-icon>
          <div>
            <div class="section-title">Invocation</div>
            <div class="section-desc">How the scheduler reaches the work.</div>
          </div>
        </div>

        <div class="type-cards">
          <button type="button" class="type-card" [class.selected]="type() === 'BEAN'"
            (click)="setType('BEAN')">
            <mat-icon>memory</mat-icon>
            <span class="tc-title">Spring Bean</span>
            <span class="tc-desc">Invoke a bean method on an executor.</span>
          </button>
          <button type="button" class="type-card" [class.selected]="type() === 'HTTP'"
            (click)="setType('HTTP')">
            <mat-icon>public</mat-icon>
            <span class="tc-title">HTTP API</span>
            <span class="tc-desc">Call an external endpoint directly.</span>
          </button>
        </div>

        @if (type() === 'BEAN') {
          <div class="field-grid cols-3">
            <mat-form-field appearance="outline">
              <mat-label>Class name</mat-label>
              <input matInput formControlName="className" />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Bean name</mat-label>
              <input matInput formControlName="beanName" />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Method</mat-label>
              <input matInput formControlName="methodName" />
            </mat-form-field>
          </div>
          <div class="param-block">
            <div class="param-head">
              <span class="param-title">Initial parameter</span>
              <button mat-button type="button" class="fmt-btn" (click)="formatJson()"
                matTooltip="Pretty-print the value as JSON">
                <mat-icon>data_object</mat-icon> Format JSON
              </button>
            </div>
            <mat-form-field appearance="outline" class="w-full">
              <textarea matInput formControlName="initialParameter" rows="4"
                placeholder='plain text, or JSON like {{ "{" }} "hello": "world" {{ "}" }}'></textarea>
            </mat-form-field>
          </div>
        } @else {
          <div class="schedule-row">
            <mat-form-field appearance="outline" style="flex: 0 0 150px;">
              <mat-label>Method</mat-label>
              <mat-select formControlName="httpMethod">
                @for (m of methods; track m) { <mat-option [value]="m">{{ m }}</mat-option> }
              </mat-select>
            </mat-form-field>
            <mat-form-field appearance="outline" class="flex-1">
              <mat-label>URL</mat-label>
              <input matInput formControlName="url" placeholder="https://httpbin.org/get" />
              <mat-hint>The external endpoint the scheduler calls directly.</mat-hint>
            </mat-form-field>
          </div>
          <div class="param-block">
            <div class="param-head">
              <span class="param-title">Payload (request body)</span>
              <button mat-button type="button" class="fmt-btn" (click)="formatJson()"
                matTooltip="Pretty-print the payload as JSON">
                <mat-icon>data_object</mat-icon> Format JSON
              </button>
            </div>
            <mat-form-field appearance="outline" class="w-full">
              <textarea matInput formControlName="initialParameter" rows="5"
                placeholder='{{ "{" }} "hello": "world" {{ "}" }}'></textarea>
              <mat-hint>Sent as JSON for POST / PUT / PATCH / DELETE.</mat-hint>
            </mat-form-field>
          </div>
        }
      </section>

      <section class="form-section">
        <div class="section-head">
          <mat-icon>schedule</mat-icon>
          <div>
            <div class="section-title">Schedule</div>
            <div class="section-desc">When the task fires.</div>
          </div>
        </div>
        <div class="schedule-row">
          <mat-form-field appearance="outline" class="syntax-field">
            <mat-label>Syntax</mat-label>
            <mat-select formControlName="parser">
              <mat-option value="cron">Cron</mat-option>
              <mat-option value="ycron">YCRON</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline" class="flex-1">
            <mat-label>Cron or ISO duration</mat-label>
            <input matInput formControlName="cron" placeholder="0 0 12 * * ?" />
            <mat-hint>
              @if (form.controls.parser.value === 'ycron') {
                Year-based YCRON, e.g. 0 0 12 ? ? 100 (the 100th day of the year).
              } @else {
                A Quartz cron, or an ISO period like PT1H30M for a fixed interval.
              }
            </mat-hint>
          </mat-form-field>
          <button mat-stroked-button type="button" class="build-btn" (click)="openBuilder()"
                  [disabled]="form.controls.parser.value === 'ycron'"
                  matTooltip="The visual builder produces traditional cron only">
            <mat-icon>tune</mat-icon> Build
          </button>
        </div>
      </section>

      <section class="form-section">
        <div class="section-head">
          <mat-icon>settings</mat-icon>
          <div>
            <div class="section-title">Options</div>
            <div class="section-desc">Timeout, retries and how a missed fire is handled.</div>
          </div>
        </div>
        <div class="field-grid cols-4">
          <mat-form-field appearance="outline">
            <mat-label>Timeout (ms)</mat-label>
            <input matInput type="number" formControlName="timeout" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Max retries</mat-label>
            <input matInput type="number" formControlName="maxRetryCount" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Retry interval (ms)</mat-label>
            <input matInput type="number" formControlName="retryInterval" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Misfire policy</mat-label>
            <mat-select formControlName="misfirePolicy">
              @for (p of policies; track p) { <mat-option [value]="p">{{ p }}</mat-option> }
            </mat-select>
          </mat-form-field>
        </div>
        <div class="field-grid cols-2">
          <mat-form-field appearance="outline">
            <mat-label>Repeat count</mat-label>
            <input matInput type="number" formControlName="repeatCount" />
            <mat-hint>Total fires for a periodic task; -1 or 0 = unlimited</mat-hint>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Stop at</mat-label>
            <input matInput type="datetime-local" formControlName="stopAt" step="1" />
            <mat-hint>Deadline after which the task stops; blank = none · times in {{ tzLabel() }}</mat-hint>
          </mat-form-field>
        </div>
        <mat-form-field appearance="outline" class="w-full">
          <mat-label>Description</mat-label>
          <input matInput formControlName="description" />
        </mat-form-field>
      </section>

      <div class="form-actions">
        <a mat-stroked-button routerLink="/tasks">Cancel</a>
        <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || saving()">
          <mat-icon>save</mat-icon> {{ editing() ? 'Save changes' : 'Create task' }}
        </button>
      </div>
    </form>
  `,
  styles: [`
    .back { display: inline-flex; align-items: center; gap: 0.25rem; color: var(--cf-blue); text-decoration: none; font-size: 0.9rem; }
    .back:hover { text-decoration: underline; }
    .form-card { max-width: 860px; padding: 1.5rem 1.75rem 0; overflow: hidden; }
    .w-full { width: 100%; }
    .field-grid { display: grid; gap: 1rem 1.1rem; }
    .field-grid.cols-2 { grid-template-columns: 1fr 1fr; }
    .field-grid.cols-3 { grid-template-columns: 1fr 1fr 1fr; }
    .field-grid.cols-4 { grid-template-columns: 1fr 1fr 1fr 1fr; }
    .schedule-row { display: flex; gap: 0.75rem; align-items: flex-start; }
    .flex-1 { flex: 1; }
    .syntax-field { width: 130px; }
    .build-btn { margin-top: 0.5rem; height: 56px; }

    /* Task-type selector cards */
    .type-cards { display: grid; grid-template-columns: 1fr 1fr; gap: 0.85rem; margin-bottom: 1.25rem; }
    .type-card {
      display: flex; flex-direction: column; align-items: flex-start; gap: 0.25rem;
      text-align: left; padding: 0.9rem 1rem; cursor: pointer;
      background: var(--cf-surface); border: 1.5px solid var(--cf-line); border-radius: var(--cf-radius-sm);
      transition: border-color .15s, box-shadow .15s, background .15s;
    }
    .type-card:hover { border-color: #c6d6ec; box-shadow: var(--cf-shadow); }
    .type-card > mat-icon { color: var(--cf-muted); transition: color .15s; }
    .type-card .tc-title { font-weight: 650; color: var(--cf-ink-2); }
    .type-card .tc-desc { font-size: 0.78rem; color: var(--cf-muted); }
    .type-card.selected {
      border-color: var(--cf-blue); background: var(--cf-blue-light);
      box-shadow: 0 0 0 1px var(--cf-blue) inset;
    }
    .type-card.selected > mat-icon, .type-card.selected .tc-title { color: var(--cf-blue); }

    .param-block { margin-bottom: 0.25rem; }
    .param-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 0.25rem; }
    .param-title { color: var(--cf-ink-2); font-size: 0.82rem; font-weight: 600; }
    .fmt-btn { --mdc-text-button-label-text-color: var(--cf-blue); font-size: 0.8rem; line-height: 1.6; min-width: 0; }
    .param-block textarea { font-family: 'JetBrains Mono', ui-monospace, monospace; font-size: 0.85rem; line-height: 1.5; }

    /* Sticky footer inside the card */
    .form-actions {
      position: sticky; bottom: 0;
      display: flex; gap: 0.6rem; justify-content: flex-end;
      margin: 0 -1.75rem; padding: 1rem 1.75rem;
      background: var(--cf-surface); border-top: 1px solid var(--cf-line);
    }

    @media (max-width: 720px) {
      .field-grid.cols-3, .field-grid.cols-4 { grid-template-columns: 1fr 1fr; }
      .type-cards { grid-template-columns: 1fr; }
    }
  `],
})
export class TaskForm {
  private readonly api = inject(CronsmithApi);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);
  private readonly snack = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);

  readonly group = input<string>();
  readonly name = input<string>();

  protected readonly policies = MISFIRE_POLICIES;
  protected readonly methods = HTTP_METHODS;
  protected readonly saving = signal(false);
  protected readonly editing = signal(false);
  protected readonly type = signal<'BEAN' | 'HTTP'>('BEAN');
  protected readonly tzLabel = tzLabel;

  protected readonly form = this.fb.nonNullable.group({
    taskGroup: ['', Validators.required],
    taskName: ['', Validators.required],
    taskType: ['BEAN' as 'BEAN' | 'HTTP'],
    className: ['com.example.Tasks', Validators.required],
    beanName: [''],
    methodName: ['run', Validators.required],
    url: [''],
    httpMethod: ['GET'],
    initialParameter: [''],
    cron: ['0 0 12 * * ?', Validators.required],
    parser: ['cron'],
    description: [''],
    timeout: [30000],
    maxRetryCount: [0],
    retryInterval: [1000],
    misfirePolicy: ['FIRE_ONCE_NOW'],
    repeatCount: [-1],
    stopAt: [''],
  });

  constructor() {
    // Keep the type signal and the per-type required validators in sync with the toggle.
    this.form.controls.taskType.valueChanges.subscribe((t) => this.applyType(t as 'BEAN' | 'HTTP'));

    effect(() => {
      const g = this.group();
      const n = this.name();
      if (g && n) {
        this.editing.set(true);
        this.api.task(g, n).subscribe((t) => {
          const kind = (t.taskType as 'BEAN' | 'HTTP') ?? 'BEAN';
          this.form.patchValue({
            taskGroup: t.taskGroup, taskName: t.taskName, taskType: kind,
            className: t.className ?? '', beanName: t.beanName ?? '', methodName: t.methodName ?? 'run',
            url: t.url ?? '', httpMethod: t.httpMethod ?? 'GET',
            initialParameter: t.initialParameter ?? '', cron: t.cron ?? '', parser: t.parser ?? 'cron',
            description: t.description ?? '', timeout: t.timeout, maxRetryCount: t.maxRetryCount,
            retryInterval: t.retryInterval, misfirePolicy: t.misfirePolicy ?? 'FIRE_ONCE_NOW',
            repeatCount: t.repeatCount ?? -1,
            // Server stopAt is UTC; render it into the datetime-local per the active time-zone mode.
            stopAt: toInputDateTime(t.stopAt),
          });
          this.applyType(kind);
          this.form.controls.taskGroup.disable();
          this.form.controls.taskName.disable();
        });
      }
    });
  }

  /** Select the task kind from the picker cards; drives the type-dependent validators. */
  protected setType(kind: 'BEAN' | 'HTTP'): void {
    this.form.controls.taskType.setValue(kind);
  }

  /** Switch required validators to match the selected task kind. */
  private applyType(kind: 'BEAN' | 'HTTP'): void {
    this.type.set(kind);
    const { className, methodName, url } = this.form.controls;
    if (kind === 'HTTP') {
      className.clearValidators();
      methodName.clearValidators();
      url.setValidators([Validators.required]);
    } else {
      className.setValidators([Validators.required]);
      methodName.setValidators([Validators.required]);
      url.clearValidators();
    }
    className.updateValueAndValidity({ emitEvent: false });
    methodName.updateValueAndValidity({ emitEvent: false });
    url.updateValueAndValidity({ emitEvent: false });
  }

  /** Pretty-print the parameter/payload field if it holds valid JSON; otherwise say so. */
  formatJson(): void {
    const ctrl = this.form.controls.initialParameter;
    const raw = (ctrl.value ?? '').trim();
    if (!raw) {
      return;
    }
    try {
      ctrl.setValue(JSON.stringify(JSON.parse(raw), null, 2));
    } catch {
      this.snack.open('Not valid JSON — left as is', 'OK', { duration: 2500 });
    }
  }

  openBuilder(): void {
    const ref = this.dialog.open(ScheduleBuilderDialog, {
      data: { value: this.form.controls.cron.value },
      autoFocus: false,
      restoreFocus: false,
    });
    ref.afterClosed().subscribe((result: string | undefined) => {
      if (result) {
        this.form.patchValue({ cron: result });
      }
    });
  }

  submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.saving.set(true);
    const v = this.form.getRawValue();
    const body: TaskMetadata = {
      taskGroup: v.taskGroup, taskName: v.taskName, taskType: v.taskType,
      className: v.className, beanName: v.beanName || v.className, methodName: v.methodName,
      url: v.url, httpMethod: v.httpMethod,
      initialParameter: v.initialParameter, cron: v.cron, parser: v.parser, description: v.description,
      timeout: v.timeout, maxRetryCount: v.maxRetryCount, retryInterval: v.retryInterval,
      misfirePolicy: v.misfirePolicy,
      // Convert the datetime-local value (entered in the active mode) back to the UTC the server stores.
      repeatCount: v.repeatCount, stopAt: fromInputDateTime(v.stopAt),
    };
    this.api.save(body).subscribe({
      next: () => {
        this.snack.open(`Task ${v.taskGroup}/${v.taskName} saved`, 'OK', { duration: 3000 });
        this.router.navigate(['/tasks', v.taskGroup, v.taskName]);
      },
      error: (e) => {
        this.saving.set(false);
        this.snack.open('Save failed: ' + (e?.error?.message ?? e?.message ?? 'error'), 'Dismiss', { duration: 5000 });
      },
    });
  }
}
