import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  AiGenerateResponse, AiProviderInfo, CronsmithApi,
} from '../../core/api.service';
import { DagGraph } from '../../shared/dag-graph';

/**
 * Generate a workflow from a prompt. The model drafts a DAG against the real node catalog; it is
 * validated and repaired server side, then shown on the read-only canvas for review. Nothing runs until
 * the operator picks an application and approves, which registers it through the normal cluster path.
 */
@Component({
  selector: 'cf-dag-ai',
  imports: [
    FormsModule, RouterLink, MatIconModule, MatButtonModule, MatProgressSpinnerModule,
    MatTooltipModule, DagGraph,
  ],
  template: `
    <div class="head">
      <a mat-icon-button routerLink="/dag/workflows" aria-label="Back"><mat-icon>arrow_back</mat-icon></a>
      <div>
        <h1 class="page-title">Generate with AI</h1>
        <p class="page-sub">Describe the workflow. The model drafts a DAG from your real nodes; you review and approve it.</p>
      </div>
    </div>

    <div class="prompt card">
      <textarea class="prompt-input" [(ngModel)]="prompt" rows="3"
        placeholder="e.g. Fetch yesterday's orders, score each one for risk, then notify the on-call team. Fetch runs first."></textarea>
      <div class="prompt-bar">
        <label class="field">
          <span class="lbl">Model</span>
          <select [(ngModel)]="selection">
            @for (p of providers(); track p.id) {
              @for (m of (p.models?.length ? p.models : [p.defaultModel]); track m) {
                <option [value]="p.id + '::' + m">{{ p.id }}{{ p.cloud ? ' (cloud)' : ' (private)' }} · {{ m }}</option>
              }
            }
          </select>
        </label>
        <label class="check" matTooltip="Refuse any provider that would send the prompt off your network">
          <input type="checkbox" [(ngModel)]="privateOnly" /> private only
        </label>
        <span class="flex-1"></span>
        <button mat-flat-button color="primary" (click)="generate()" [disabled]="generating() || !prompt.trim()">
          @if (generating()) { <mat-spinner diameter="18" /> } @else { <mat-icon>auto_awesome</mat-icon> }
          {{ generating() ? 'Generating…' : 'Generate' }}
        </button>
      </div>
      @if (generating()) {
        <p class="hint"><mat-icon>schedule</mat-icon> A private model on CPU can take up to a minute on the first call.</p>
      }
      @if (error()) { <div class="err"><mat-icon>error</mat-icon> {{ error() }}</div> }
    </div>

    @if (result(); as r) {
      <div class="result card">
        <div class="status">
          @if (r.valid) {
            <span class="badge ok"><mat-icon>check_circle</mat-icon> Valid</span>
          } @else {
            <span class="badge warn"><mat-icon>warning</mat-icon> Needs fixes</span>
          }
          <span class="meta">{{ r.provider }} · {{ r.model }}</span>
          <span class="meta">{{ r.promptTokens + r.completionTokens }} tokens</span>
          @if (r.repairs > 0) { <span class="meta">{{ r.repairs }} repair{{ r.repairs > 1 ? 's' : '' }}</span> }
          <span class="flex-1"></span>
          @if (r.dag) { <span class="meta mono">{{ r.dag.graph }}</span> }
        </div>

        @if (r.error) { <div class="err"><mat-icon>error</mat-icon> {{ r.error }}</div> }

        @if (r.issues.length) {
          <ul class="issues">
            @for (i of r.issues; track $index) {
              <li><code>{{ i.path }}</code> {{ i.message }}</li>
            }
          </ul>
        }

        @if (r.dag) {
          <div class="canvas"><cf-dag-graph [definition]="r.dag" /></div>

          <div class="approve">
            <label class="field">
              <span class="lbl">Application</span>
              <select [(ngModel)]="application">
                @if (!applications().length) { <option value="">— no live executor —</option> }
                @for (a of applications(); track a) { <option [value]="a">{{ a }}</option> }
              </select>
            </label>
            <span class="approve-note">
              <mat-icon>verified_user</mat-icon>
              Approving re-validates and registers it against a live executor. It never runs on its own.
            </span>
            <span class="flex-1"></span>
            <button mat-stroked-button (click)="generate()" [disabled]="generating()">
              <mat-icon>refresh</mat-icon> Regenerate
            </button>
            <button mat-flat-button color="primary" (click)="approve()"
              [disabled]="approving() || !application">
              @if (approving()) { <mat-spinner diameter="18" /> } @else { <mat-icon>rocket_launch</mat-icon> }
              {{ approving() ? 'Approving…' : 'Approve & register' }}
            </button>
          </div>
          @if (approveError()) { <div class="err"><mat-icon>error</mat-icon> {{ approveError() }}</div> }
        }
      </div>
    }
  `,
  styles: [`
    .head { display: flex; align-items: flex-start; gap: 0.5rem; margin-bottom: 1rem; }
    .head .page-title { margin: 0; } .head .page-sub { margin: 0.15rem 0 0; }
    .card { margin-bottom: 1rem; }
    .prompt { padding: 0.9rem 1rem; }
    .prompt-input { width: 100%; box-sizing: border-box; font: inherit; font-size: 0.95rem; line-height: 1.5;
      padding: 0.7rem 0.8rem; border: 1px solid #d7e0ec; border-radius: 10px; color: #0f2c4d; resize: vertical;
      outline: none; }
    .prompt-input:focus { border-color: #38bdf8; box-shadow: 0 0 0 3px rgba(56,189,248,0.15); }
    .prompt-bar { display: flex; align-items: flex-end; gap: 0.9rem; margin-top: 0.7rem; flex-wrap: wrap; }
    .flex-1 { flex: 1 1 auto; }
    .field { display: flex; flex-direction: column; gap: 0.25rem; }
    .field .lbl { font-size: 0.75rem; color: var(--muted-foreground); font-weight: 600; }
    select { font: inherit; padding: 0.45rem 0.6rem; border: 1px solid #d7e0ec; border-radius: 9px;
      background: #fff; color: #0f2c4d; outline: none; }
    .check { font-size: 0.85rem; color: var(--muted-foreground); display: inline-flex; align-items: center; gap: 0.35rem; }
    .hint { display: flex; align-items: center; gap: 0.4rem; color: var(--muted-foreground); font-size: 0.82rem; margin: 0.6rem 0 0; }
    .hint mat-icon, .approve-note mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .err { display: flex; align-items: center; gap: 0.4rem; color: #c02b4b; background: #ffe4e9;
      border-radius: 9px; padding: 0.55rem 0.8rem; font-size: 0.85rem; margin-top: 0.7rem; }
    .result { padding: 0.9rem 1rem; }
    .status { display: flex; align-items: center; gap: 0.7rem; flex-wrap: wrap; margin-bottom: 0.7rem; }
    .badge { display: inline-flex; align-items: center; gap: 0.3rem; font-weight: 700; font-size: 0.82rem;
      padding: 0.2rem 0.55rem; border-radius: 999px; }
    .badge mat-icon { font-size: 17px; width: 17px; height: 17px; }
    .badge.ok { color: #067a5b; background: #d6f7ea; }
    .badge.warn { color: #9a5b00; background: #ffeccc; }
    .meta { font-size: 0.8rem; color: var(--muted-foreground); }
    .mono { font-family: var(--cf-font-mono); }
    .issues { margin: 0 0 0.8rem; padding-left: 1.1rem; color: #9a5b00; font-size: 0.82rem; }
    .issues code { font-family: var(--cf-font-mono); color: #c02b4b; }
    .canvas { height: 56vh; min-height: 360px; border-radius: 14px; overflow: hidden; margin-bottom: 0.9rem; }
    .approve { display: flex; align-items: flex-end; gap: 0.9rem; flex-wrap: wrap; }
    .approve-note { display: inline-flex; align-items: center; gap: 0.35rem; color: var(--muted-foreground); font-size: 0.8rem; }
  `],
})
export class DagAiPage {
  private readonly api = inject(CronsmithApi);
  private readonly router = inject(Router);

  protected prompt = '';
  /** The chosen "providerId::model" from the dropdown. */
  protected selection = '';
  protected privateOnly = false;
  protected application = '';

  protected readonly providers = signal<AiProviderInfo[]>([]);
  protected readonly applications = signal<string[]>([]);
  protected readonly generating = signal(false);
  protected readonly approving = signal(false);
  protected readonly result = signal<AiGenerateResponse | null>(null);
  protected readonly error = signal('');
  protected readonly approveError = signal('');

  constructor() {
    this.api.aiProviders().subscribe({
      next: (ps) => {
        this.providers.set(ps);
        const def = ps.find((p) => !p.cloud) ?? ps[0];
        if (def && !this.selection) {
          const m = def.models?.length ? def.models[0] : def.defaultModel;
          this.selection = `${def.id}::${m}`;
        }
      },
      error: () => this.error.set('Could not load AI providers. Is cronflow.ai.enabled=true on the server?'),
    });
    this.api.dagApplications().subscribe((apps) => {
      this.applications.set(apps);
      if (apps.length && !this.application) { this.application = apps[0]; }
    });
  }

  protected generate(): void {
    if (!this.prompt.trim() || this.generating()) { return; }
    this.error.set('');
    this.approveError.set('');
    this.generating.set(true);
    const [provider, model] = this.selection.split('::');
    this.api.aiGenerate(this.prompt.trim(), provider || undefined, model || undefined, this.privateOnly)
      .subscribe({
      next: (r) => { this.result.set(r); this.generating.set(false); },
      error: (e) => {
        this.generating.set(false);
        this.error.set(e?.error?.error || e?.message || 'Generation failed.');
      },
    });
  }

  protected approve(): void {
    const r = this.result();
    if (!r?.dag || !this.application || this.approving()) { return; }
    this.approveError.set('');
    this.approving.set(true);
    this.api.aiApprove(r.id, r.dag, this.application).subscribe({
      next: (res) => {
        this.approving.set(false);
        if (res.registered) {
          this.router.navigate(['/dag/workflows']);
        } else {
          this.approveError.set(res.error || 'Approval was rejected.');
        }
      },
      error: (e) => {
        this.approving.set(false);
        const issues = e?.error?.issues as { path: string; message: string }[] | undefined;
        this.approveError.set(e?.error?.error
          + (issues?.length ? ': ' + issues.map((i) => i.message).join('; ') : '')
          || 'Approval failed.');
      },
    });
  }
}
