import {
  AfterViewInit, Component, ElementRef, OnDestroy, inject, signal, viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import cytoscape, { Core, NodeSingular } from 'cytoscape';
import dagre from 'cytoscape-dagre';
import edgehandles from 'cytoscape-edgehandles';
import * as yaml from 'js-yaml';
import { CronsmithApi } from '../../core/api.service';
import { DagDefinition } from '../../core/models';

cytoscape.use(dagre);
// eslint-disable-next-line @typescript-eslint/no-explicit-any
cytoscape.use(edgehandles as any);

interface NodeData {
  id: string; label: string; beanName: string; methodName: string; entry: boolean; trigger: string;
}

/**
 * The DAG canvas: draw a workflow two ways, switchable at any time.
 *
 * <ul>
 * <li><b>Canvas</b> — add nodes, drag from a node's rim to another to wire them, click a node to set
 * the bean·method it calls. Reuses the dark cytoscape canvas from the run views.</li>
 * <li><b>Code</b> — the same graph as JSON or YAML, to import an existing definition, tweak it, or
 * copy it out. Switching between the two keeps them in sync.</li>
 * </ul>
 *
 * Saving registers the graph against a live executor of the chosen application so it can run at once.
 */
@Component({
  selector: 'cf-dag-new',
  imports: [FormsModule, RouterLink, MatIconModule, MatButtonModule, MatTooltipModule],
  template: `
    <div class="head">
      <a mat-icon-button routerLink="/dag/workflows" aria-label="Back"><mat-icon>arrow_back</mat-icon></a>
      <div>
        <h1 class="page-title">New workflow</h1>
        <p class="page-sub">Draw it on the canvas, or write it as JSON/YAML. Switch anytime, they stay in sync.</p>
      </div>
    </div>

    <div class="toolbar card">
      <label class="field">
        <span class="lbl">Graph name</span>
        <input [(ngModel)]="graphName" placeholder="e.g. billing-flow" />
      </label>
      <label class="field">
        <span class="lbl">Application</span>
        <select [(ngModel)]="application">
          @if (!applications().length) { <option value="">— no live executor —</option> }
          @for (a of applications(); track a) { <option [value]="a">{{ a }}</option> }
        </select>
      </label>

      <div class="modes" role="tablist">
        <button role="tab" [class.on]="mode() === 'canvas'" (click)="switchTo('canvas')"><mat-icon>account_tree</mat-icon> Canvas</button>
        <button role="tab" [class.on]="mode() === 'code'" (click)="switchTo('code')"><mat-icon>code</mat-icon> Code</button>
      </div>

      <span class="flex-1"></span>
      @if (mode() === 'canvas') {
        <button mat-stroked-button (click)="addNode()"><mat-icon>add</mat-icon> Add node</button>
        <button mat-stroked-button class="connect-btn" [class.on]="connectMode()" (click)="toggleConnect()">
          <mat-icon>{{ connectMode() ? 'link' : 'add_link' }}</mat-icon> {{ connectMode() ? 'Connecting…' : 'Connect' }}
        </button>
      }
      <a mat-stroked-button routerLink="/dag/workflows">Cancel</a>
      <button mat-flat-button color="primary" (click)="save()" [disabled]="saving()">
        <mat-icon>save</mat-icon> {{ saving() ? 'Saving…' : 'Create' }}
      </button>
    </div>

    @if (error()) { <div class="err"><mat-icon>error</mat-icon> {{ error() }}</div> }

    <!-- Canvas is never removed from the DOM (only hidden) so the cytoscape instance survives a switch. -->
    <div class="canvas-card card" [style.display]="mode() === 'canvas' ? '' : 'none'">
      <div #cy class="canvas"></div>
      @if (picked(); as p) {
        <div class="props">
          <div class="props-head">
            <mat-icon>tune</mat-icon><strong>Node</strong>
            <span class="flex-1"></span>
            <button mat-icon-button (click)="deleteSelected()" matTooltip="Delete node" aria-label="Delete"><mat-icon>delete</mat-icon></button>
            <button mat-icon-button (click)="clearSel()" aria-label="Close"><mat-icon>close</mat-icon></button>
          </div>
          <label class="pf"><span>Name</span>
            <input [ngModel]="p.label" (ngModelChange)="setField('label', $event)" placeholder="node name" /></label>
          <label class="pf"><span>Bean</span>
            <input class="mono" [ngModel]="p.beanName" (ngModelChange)="setField('beanName', $event)" placeholder="spring bean" /></label>
          <label class="pf"><span>Method</span>
            <input class="mono" [ngModel]="p.methodName" (ngModelChange)="setField('methodName', $event)" placeholder="method" /></label>
          <div class="pf-row">
            <label class="pf-inline"><span>Trigger</span>
              <select [ngModel]="p.trigger" (ngModelChange)="setField('trigger', $event)">
                <option value="ALL">ALL</option><option value="ANY">ANY</option>
              </select></label>
            <label class="pf-check"><input type="checkbox" [ngModel]="p.entry" (ngModelChange)="setField('entry', $event)" /> entry node</label>
          </div>
        </div>
      }
      <div class="canvas-hint" [class.dim]="picked()">
        <mat-icon>touch_app</mat-icon>
        @if (connectMode()) {
          Connect mode: drag from one node onto another to draw an edge · turn off to move nodes
        } @else {
          Click a node to edit · drag to move it · turn on Connect to draw edges · select + ⌫ to delete
        }
      </div>
    </div>

    @if (mode() === 'code') {
      <div class="code-card card">
        <div class="code-head">
          <div class="fmt">
            <button [class.on]="format() === 'json'" (click)="setFormat('json')">JSON</button>
            <button [class.on]="format() === 'yaml'" (click)="setFormat('yaml')">YAML</button>
          </div>
          <span class="flex-1"></span>
          <span class="code-note">Paste a definition to import, or copy this out.</span>
          <button mat-stroked-button (click)="copyCode()"><mat-icon>content_copy</mat-icon> Copy</button>
        </div>
        <textarea class="code mono" [(ngModel)]="codeText" spellcheck="false"
          placeholder="Paste a DAG definition here…"></textarea>
        @if (codeError()) { <div class="code-err"><mat-icon>error</mat-icon> {{ codeError() }}</div> }
      </div>
    }
  `,
  styles: [`
    .head { display: flex; align-items: flex-start; gap: 0.5rem; margin-bottom: 1rem; }
    .head .page-title { margin: 0; } .head .page-sub { margin: 0.15rem 0 0; }
    .toolbar { display: flex; align-items: flex-end; gap: 0.9rem; padding: 0.85rem 1rem; flex-wrap: wrap; }
    .flex-1 { flex: 1 1 auto; }
    .field { display: flex; flex-direction: column; gap: 0.25rem; }
    .field .lbl { font-size: 0.75rem; color: #3d5372; font-weight: 600; }
    input, select, textarea { font: inherit; padding: 0.45rem 0.6rem; border: 1px solid #d7e0ec;
      border-radius: 9px; background: #fff; color: #0f2c4d; outline: none; }
    input:focus, select:focus, textarea:focus { border-color: #38bdf8; box-shadow: 0 0 0 3px rgba(56,189,248,0.15); }
    .mono { font-family: var(--cf-font-mono); }
    .modes { display: inline-flex; background: #eef2f7; border-radius: 10px; padding: 3px; gap: 2px; }
    .modes button { display: inline-flex; align-items: center; gap: 0.3rem; border: 0; background: transparent;
      color: #3d5372; font: inherit; font-weight: 600; font-size: 0.85rem; padding: 0.35rem 0.7rem;
      border-radius: 8px; cursor: pointer; }
    .modes button.on { background: #fff; color: #0f2c4d; box-shadow: 0 1px 3px rgba(15,44,77,0.12); }
    .connect-btn.on { background: #d9f5f0; color: #067a70; border-color: #38e0c8; }
    .modes mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .err { display: flex; align-items: center; gap: 0.4rem; color: #c02b4b; background: #ffe4e9;
      border-radius: 9px; padding: 0.55rem 0.8rem; font-size: 0.85rem; margin-bottom: 0.9rem; }
    .canvas-card { position: relative; padding: 0; overflow: hidden; height: 68vh; min-height: 440px; }
    .canvas { width: 100%; height: 100%;
      background-color: var(--cf-canvas, #0d1524);
      background-image: radial-gradient(rgba(120,150,200,0.12) 1px, transparent 1.4px);
      background-size: 22px 22px; background-position: -11px -11px; }
    .props { position: absolute; top: 12px; right: 12px; width: 258px; background: #fff; border-radius: 12px;
      box-shadow: 0 10px 30px -8px rgba(15,44,77,0.35); padding: 0.7rem 0.85rem; }
    .props-head { display: flex; align-items: center; gap: 0.4rem; color: #0f2c4d; margin-bottom: 0.5rem; }
    .props-head mat-icon { color: #38bdf8; }
    .pf { display: flex; flex-direction: column; gap: 0.2rem; margin-bottom: 0.5rem; font-size: 0.78rem; color: #3d5372; }
    .pf input { width: 100%; box-sizing: border-box; }
    .pf-row { display: flex; align-items: center; gap: 0.8rem; }
    .pf-inline { display: flex; flex-direction: column; gap: 0.2rem; font-size: 0.78rem; color: #3d5372; }
    .pf-check { font-size: 0.8rem; color: #3d5372; display: inline-flex; align-items: center; gap: 0.3rem; }
    .canvas-hint { position: absolute; left: 12px; bottom: 12px; display: flex; align-items: center; gap: 0.35rem;
      background: rgba(13,21,36,0.82); color: #aebfda; font-size: 0.76rem; padding: 0.4rem 0.7rem; border-radius: 999px;
      border: 1px solid rgba(120,150,200,0.18); }
    .canvas-hint mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .canvas-hint.dim { opacity: 0.35; }
    .code-card { padding: 0; overflow: hidden; }
    .code-head { display: flex; align-items: center; gap: 0.7rem; padding: 0.6rem 0.8rem; border-bottom: 1px solid #eef2f7; }
    .code-note { font-size: 0.76rem; color: #3d5372; }
    .fmt { display: inline-flex; background: #eef2f7; border-radius: 8px; padding: 2px; }
    .fmt button { border: 0; background: transparent; color: #3d5372; font: inherit; font-weight: 600;
      font-size: 0.78rem; padding: 0.25rem 0.6rem; border-radius: 6px; cursor: pointer; }
    .fmt button.on { background: #fff; color: #0f2c4d; box-shadow: 0 1px 2px rgba(15,44,77,0.12); }
    textarea.code { width: 100%; box-sizing: border-box; border: 0; border-radius: 0; height: 60vh; min-height: 400px;
      resize: vertical; font-size: 0.82rem; line-height: 1.55; color: #0f2c4d; background: #fbfdff; }
    textarea.code:focus { box-shadow: none; }
    .code-err { display: flex; align-items: center; gap: 0.4rem; color: #c02b4b; background: #ffe4e9;
      padding: 0.5rem 0.8rem; font-size: 0.82rem; }
  `],
})
export class DagNewPage implements AfterViewInit, OnDestroy {
  private readonly api = inject(CronsmithApi);
  private readonly router = inject(Router);
  private readonly cyHost = viewChild.required<ElementRef<HTMLElement>>('cy');

  protected graphName = '';
  protected application = '';
  protected codeText = '';
  protected readonly applications = signal<string[]>([]);
  protected readonly saving = signal(false);
  protected readonly error = signal('');
  protected readonly picked = signal<NodeData | undefined>(undefined);
  protected readonly mode = signal<'canvas' | 'code'>('canvas');
  protected readonly format = signal<'json' | 'yaml'>('json');
  protected readonly codeError = signal('');
  /** When on, dragging between nodes draws an edge; when off, dragging moves a node. */
  protected readonly connectMode = signal(false);

  private cy?: Core;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  private eh?: any;
  private seq = 0;

  constructor() {
    this.api.dagApplications().subscribe((apps) => {
      this.applications.set(apps);
      if (apps.length && !this.application) {
        this.application = apps[0];
      }
    });
  }

  ngAfterViewInit(): void {
    this.cy = cytoscape({
      container: this.cyHost().nativeElement,
      style: EDIT_STYLE,
      wheelSensitivity: 0.2,
      minZoom: 0.2,
      maxZoom: 2.5,
    });
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    this.eh = (this.cy as any).edgehandles({ snap: true, canConnect: () => true });
    this.eh.disableDrawMode(); // start in "move" mode; the Connect toggle turns drawing on
    this.cy.on('tap', 'node', (e) => this.select(e.target as NodeSingular));
    this.cy.on('tap', (e) => { if (e.target === this.cy) { this.clearSel(); } });
    this.cyHost().nativeElement.setAttribute('tabindex', '0');
    this.cyHost().nativeElement.addEventListener('keydown', (ev) => {
      if (ev.key === 'Delete' || ev.key === 'Backspace') { this.deleteSelected(); }
    });
    this.addNode(true);
  }

  ngOnDestroy(): void {
    this.cy?.destroy();
  }

  // ---- mode switching ---------------------------------------------------------------------------

  protected switchTo(m: 'canvas' | 'code'): void {
    if (m === this.mode()) {
      return;
    }
    if (m === 'code') {
      this.codeText = this.serialize(this.build(), this.format());
      this.codeError.set('');
      this.mode.set('code');
    } else {
      const def = this.tryParse();
      if (!def) {
        return; // stay in code mode; codeError explains why
      }
      this.loadDefinition(def);
      this.mode.set('canvas');
      // The container had display:none, so give cytoscape its size back.
      setTimeout(() => { this.cy?.resize(); this.cy?.fit(undefined, 28); }, 0);
    }
  }

  protected setFormat(f: 'json' | 'yaml'): void {
    if (f === this.format()) {
      return;
    }
    // Re-serialise the current text into the new format, so the editor doesn't lose work.
    const def = this.tryParse();
    this.format.set(f);
    if (def) {
      this.codeText = this.serialize(def, f);
      this.codeError.set('');
    }
  }

  protected copyCode(): void {
    navigator.clipboard?.writeText(this.codeText).catch(() => {});
  }

  protected toggleConnect(): void {
    const on = !this.connectMode();
    this.connectMode.set(on);
    if (on) {
      this.eh?.enableDrawMode();
    } else {
      this.eh?.disableDrawMode();
    }
  }

  private tryParse(): DagDefinition | undefined {
    try {
      const def = this.parse(this.codeText, this.format());
      if (!def || typeof def !== 'object') {
        throw new Error('not an object');
      }
      this.codeError.set('');
      return def;
    } catch (e) {
      this.codeError.set('Cannot parse ' + this.format().toUpperCase() + ': '
        + (e instanceof Error ? e.message : String(e)));
      return undefined;
    }
  }

  private serialize(def: DagDefinition, fmt: 'json' | 'yaml'): string {
    return fmt === 'yaml' ? yaml.dump(def, { noRefs: true, lineWidth: 100 })
      : JSON.stringify(def, null, 2);
  }

  private parse(text: string, fmt: 'json' | 'yaml'): DagDefinition {
    return (fmt === 'yaml' ? yaml.load(text) : JSON.parse(text)) as DagDefinition;
  }

  // ---- canvas editing ---------------------------------------------------------------------------

  protected addNode(entry = false): void {
    if (!this.cy) {
      return;
    }
    const id = `n${++this.seq}`;
    const name = entry && this.cy.nodes().empty() ? 'start' : `node${this.seq}`;
    const ext = this.cy.extent();
    const pos = { x: (ext.x1 + ext.x2) / 2 + (this.seq % 3) * 44, y: ext.y1 + 70 + (this.seq % 4) * 34 };
    const node = this.cy.add({
      group: 'nodes',
      data: { id, label: name, beanName: '', methodName: '', entry: entry ? 1 : 0, trigger: 'ALL' },
      position: pos,
    });
    this.select(node as NodeSingular);
  }

  private select(node: NodeSingular): void {
    this.cy?.elements().removeClass('sel');
    node.addClass('sel');
    this.picked.set(this.readNode(node));
  }

  protected clearSel(): void {
    this.cy?.elements().removeClass('sel');
    this.picked.set(undefined);
  }

  protected deleteSelected(): void {
    if (!this.cy) {
      return;
    }
    const sel = this.cy.$('.sel');
    if (sel.nonempty()) {
      sel.connectedEdges().remove();
      sel.remove();
      this.picked.set(undefined);
    }
  }

  protected setField(field: keyof NodeData, value: string | boolean): void {
    const cur = this.picked();
    if (!this.cy || !cur) {
      return;
    }
    const node = this.cy.$id(cur.id);
    if (field === 'entry') {
      node.data('entry', value ? 1 : 0);
    } else {
      node.data(field, value);
    }
    this.picked.set(this.readNode(node));
  }

  private readNode(node: NodeSingular): NodeData {
    const d = node.data();
    return {
      id: d.id, label: d.label ?? '', beanName: d.beanName ?? '', methodName: d.methodName ?? '',
      entry: !!d.entry, trigger: d.trigger ?? 'ALL',
    };
  }

  /** Rebuild the canvas from a definition (used when switching back from Code, or importing). */
  private loadDefinition(def: DagDefinition): void {
    if (!this.cy) {
      return;
    }
    this.graphName = def.graph || this.graphName;
    this.clearSel();
    this.cy.elements().remove();
    for (const n of def.nodes ?? []) {
      this.cy.add({ group: 'nodes', data: {
        id: n.name, label: n.name, beanName: n.beanName ?? '', methodName: n.methodName ?? '',
        entry: n.entry ? 1 : 0, trigger: n.trigger || 'ALL',
      } });
    }
    for (const e of def.edges ?? []) {
      if (e.from && e.to && this.cy.$id(e.from).nonempty() && this.cy.$id(e.to).nonempty()) {
        this.cy.add({ group: 'edges', data: { source: e.from, target: e.to } });
      }
    }
    this.seq = def.nodes?.length ?? 0;
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    this.cy.layout({ name: 'dagre', rankDir: 'TB', nodeSep: 34, rankSep: 58, padding: 16 } as any).run();
    this.cy.fit(undefined, 28);
  }

  // ---- save -------------------------------------------------------------------------------------

  protected save(): void {
    let def: DagDefinition;
    if (this.mode() === 'code') {
      const parsed = this.tryParse();
      if (!parsed) {
        return;
      }
      def = parsed;
      if (def.graph) {
        this.graphName = def.graph;
      }
    } else {
      def = this.build();
    }
    const err = this.validate(def);
    if (err) {
      this.error.set(err);
      return;
    }
    this.error.set('');
    this.saving.set(true);
    this.api.createDag(this.application, def).subscribe({
      next: () => this.router.navigate(['/dag/workflows']),
      error: (e) => { this.saving.set(false); this.error.set(e?.error?.error || 'Could not create the workflow.'); },
    });
  }

  /** Read the drawn graph back into a DagDefinition (node ids map to their editable names). */
  private build(): DagDefinition {
    const cy = this.cy;
    const idToName = new Map<string, string>();
    const nodes = (cy?.nodes() ?? []).map((n) => {
      const d = this.readNode(n as NodeSingular);
      idToName.set(d.id, d.label);
      return { name: d.label, entry: d.entry, trigger: d.trigger, retries: 0,
        beanName: d.beanName, methodName: d.methodName, subgraph: '' };
    });
    const edges = (cy?.edges() ?? []).map((e) => ({
      from: idToName.get(e.data('source')) ?? '', to: idToName.get(e.data('target')) ?? '',
      condition: 'ON_SUCCESS',
    })).filter((e) => e.from && e.to);
    return { graph: this.graphName, inputs: [], channels: [], nodes, edges, conditionals: [] };
  }

  private validate(def: DagDefinition): string {
    if (!this.graphName.trim() && !def.graph?.trim()) { return 'A graph name is required.'; }
    if (!this.application) { return 'Choose an application (a live executor to host the beans).'; }
    if (!def.nodes?.length) { return 'Add at least one node.'; }
    const names = new Set<string>();
    for (const n of def.nodes) {
      if (!n.name?.trim() || !n.beanName?.trim() || !n.methodName?.trim()) {
        return 'Every node needs a name, a bean and a method.';
      }
      if (names.has(n.name)) { return `Duplicate node name: ${n.name}.`; }
      names.add(n.name);
    }
    if (!def.nodes.some((n) => n.entry)) { return 'Mark at least one node as an entry.'; }
    return '';
  }
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const EDIT_STYLE: any = [
  {
    selector: 'node',
    style: {
      'background-color': '#111c30', 'border-width': 1.2, 'border-color': '#2b3a56',
      shape: 'round-rectangle', 'corner-radius': 9, label: 'data(label)',
      'text-valign': 'center', 'text-halign': 'center',
      'font-family': 'JetBrains Mono, ui-monospace, monospace', 'font-size': 11, 'font-weight': 600,
      color: '#dce6f7', width: 'label', height: 30, padding: '12px',
      'text-wrap': 'wrap', 'text-max-width': '150px',
    },
  },
  { selector: 'node[entry = 1]', style: { 'border-width': 2, 'border-color': '#38e0c8', color: '#eafff9' } },
  {
    selector: 'node.sel',
    style: { 'border-width': 3, 'border-color': '#f5b544', 'shadow-blur': 20, 'shadow-color': '#f5b544', 'shadow-opacity': 0.5 },
  },
  {
    selector: 'edge',
    style: {
      width: 1.6, 'line-color': '#33507d', 'target-arrow-color': '#4a6a9c', 'target-arrow-shape': 'triangle',
      'arrow-scale': 0.95, 'curve-style': 'taxi', 'taxi-direction': 'downward', 'taxi-turn': '48%',
    },
  },
  { selector: '.eh-handle', style: { 'background-color': '#38e0c8', width: 10, height: 10, 'border-width': 0 } },
  { selector: '.eh-ghost-edge, .eh-preview', style: { 'line-color': '#38e0c8', 'target-arrow-color': '#38e0c8', width: 1.8 } },
];
