import {
  AfterViewInit, Component, ElementRef, OnDestroy, effect, input, output, viewChild,
} from '@angular/core';
import cytoscape, { Core, ElementDefinition, NodeSingular } from 'cytoscape';
import dagre from 'cytoscape-dagre';
import { DagDefinition } from '../core/models';

cytoscape.use(dagre);

/**
 * Renders a {@link DagDefinition} as a directed graph on a dark, terminal-grade canvas
 * (cytoscape + dagre auto-layout). Read-only: it draws structure and, when a per-node status map is
 * supplied (a run drill-down), colours each node by outcome — a RUNNING node pulses so you can see at
 * a glance where the flow currently is. Entry nodes carry a teal accent, subgraph nodes an amber
 * dashed border; clicking a node emits its name so the host can show its details.
 */
@Component({
  selector: 'cf-dag-graph',
  template: `<div #host class="dag-host"></div>`,
  styles: [`
    :host { display: block; height: 100%; }
    .dag-host {
      width: 100%;
      height: 100%;
      min-height: 320px;
      border-radius: 14px;
      background-color: var(--cf-canvas, #0d1524);
      /* faint engineering dot-grid */
      background-image: radial-gradient(rgba(120, 150, 200, 0.12) 1px, transparent 1.4px);
      background-size: 22px 22px;
      background-position: -11px -11px;
      box-shadow: inset 0 0 0 1px rgba(120, 150, 200, 0.10),
                  inset 0 22px 48px -30px rgba(0, 0, 0, 0.7);
      overflow: hidden;
    }
  `],
})
export class DagGraph implements AfterViewInit, OnDestroy {
  readonly definition = input.required<DagDefinition>();
  /** node name -> run status (SUCCESS/FAILED/RUNNING/SKIPPED); empty for a definition-only preview. */
  readonly nodeStatus = input<Record<string, string>>({});
  readonly selected = input<string | null>(null);
  readonly nodeClick = output<string>();

  private readonly host = viewChild.required<ElementRef<HTMLElement>>('host');
  private cy?: Core;
  private pulsing?: NodeSingular;

  constructor() {
    effect(() => {
      const def = this.definition();
      const status = this.nodeStatus();
      const sel = this.selected();
      if (this.cy) {
        this.render(def, status, sel);
      }
    });
  }

  ngAfterViewInit(): void {
    this.cy = cytoscape({
      container: this.host().nativeElement,
      style: STYLE,
      wheelSensitivity: 0.2,
      minZoom: 0.2,
      maxZoom: 2.5,
    });
    this.cy.on('tap', 'node', (e) => this.nodeClick.emit(e.target.id()));
    this.render(this.definition(), this.nodeStatus(), this.selected());
  }

  ngOnDestroy(): void {
    this.stopPulse();
    this.cy?.destroy();
  }

  private render(def: DagDefinition, status: Record<string, string>, sel: string | null): void {
    if (!this.cy) {
      return;
    }
    this.stopPulse();
    const elements = buildElements(def, status);
    this.cy.json({ elements });
    this.cy.nodes().removeClass('selected');
    if (sel) {
      this.cy.$id(sel).addClass('selected');
    }
    this.cy.layout({
      name: 'dagre',
      rankDir: 'TB',
      nodeSep: 34,
      rankSep: 58,
      edgeSep: 14,
      padding: 16,
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } as any).run();
    this.cy.fit(undefined, 28);
    this.startPulse();
  }

  /** Loop a soft glow on the RUNNING node so the live position of the flow is unmistakable. */
  private startPulse(): void {
    if (!this.cy) {
      return;
    }
    const running = this.cy.nodes('[status = "RUNNING"]').first();
    if (!running || running.empty()) {
      return;
    }
    this.pulsing = running;
    const beat = (): void => {
      if (!this.pulsing || this.pulsing.removed()) {
        return;
      }
      this.pulsing
        .animate({ style: { 'border-width': 5, 'shadow-blur': 34 } }, { duration: 620 })
        .animate({ style: { 'border-width': 2.5, 'shadow-blur': 16 } }, { duration: 620, complete: beat });
    };
    beat();
  }

  private stopPulse(): void {
    if (this.pulsing && !this.pulsing.removed()) {
      this.pulsing.stop();
    }
    this.pulsing = undefined;
  }
}

function buildElements(def: DagDefinition, status: Record<string, string>): ElementDefinition[] {
  const nodeNames = new Set((def.nodes ?? []).map((n) => n.name));
  const nodes: ElementDefinition[] = (def.nodes ?? []).map((n) => ({
    data: {
      id: n.name,
      label: n.name,
      entry: n.entry ? 1 : 0,
      subgraph: n.subgraph ? 1 : 0,
      status: status[n.name] ?? '',
    },
  }));

  const seen = new Set<string>();
  const edges: ElementDefinition[] = [];
  const add = (from?: string, to?: string, conditional = false): void => {
    if (!from || !to || !nodeNames.has(from) || !nodeNames.has(to)) {
      return;
    }
    const id = `${from}__${to}`;
    if (seen.has(id)) {
      return;
    }
    seen.add(id);
    edges.push({ data: { id, source: from, target: to, conditional: conditional ? 1 : 0 } });
  };

  // Plain edges are the normal flow (their `condition` is usually ON_SUCCESS) — draw solid; only a
  // branch edge is conditional. True SpEL routing lives in `conditionals` below and is drawn dashed.
  for (const e of def.edges ?? []) {
    add(e.from, e.to, !!e.branch);
  }
  // Conditional routing may live only in `conditionals`; surface those targets as dashed edges too.
  for (const c of def.conditionals ?? []) {
    for (const src of c.sources ?? []) {
      for (const targets of Object.values(c.branches ?? {})) {
        for (const t of targets) {
          add(src, t, true);
        }
      }
      for (const t of c.elseTargets ?? []) {
        add(src, t, true);
      }
    }
  }
  return [...nodes, ...edges];
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const STYLE: any = [
  {
    selector: 'node',
    style: {
      'background-color': '#111c30',
      'border-width': 1.2,
      'border-color': '#2b3a56',
      shape: 'round-rectangle',
      'corner-radius': 9,
      label: 'data(label)',
      'text-valign': 'center',
      'text-halign': 'center',
      'font-family': 'JetBrains Mono, ui-monospace, monospace',
      'font-size': 11,
      'font-weight': 600,
      color: '#dce6f7',
      width: 'label',
      height: 30,
      padding: '12px',
      'text-wrap': 'wrap',
      'text-max-width': '150px',
      'shadow-blur': 0,
      'shadow-color': '#000000',
      'shadow-opacity': 0,
    },
  },
  // Entry: teal accent — the flow starts here.
  {
    selector: 'node[entry = 1]',
    style: { 'border-width': 2, 'border-color': '#38e0c8', color: '#eafff9' },
  },
  // Subgraph: amber dashed (deliberately not violet).
  {
    selector: 'node[subgraph = 1]',
    style: { 'border-style': 'dashed', 'border-width': 1.6, 'border-color': '#f5b544' },
  },
  {
    selector: 'node[status = "SUCCESS"]',
    style: {
      'background-color': '#0f2f2a', 'border-color': '#34d399', 'border-width': 1.8, color: '#bff5e3',
      'shadow-blur': 14, 'shadow-color': '#34d399', 'shadow-opacity': 0.35,
    },
  },
  {
    selector: 'node[status = "FAILED"]',
    style: {
      'background-color': '#331420', 'border-color': '#fb7185', 'border-width': 1.8, color: '#ffd7de',
      'shadow-blur': 16, 'shadow-color': '#fb7185', 'shadow-opacity': 0.4,
    },
  },
  {
    selector: 'node[status = "RUNNING"]',
    style: {
      'background-color': '#0e2740', 'border-color': '#38bdf8', 'border-width': 2.5, color: '#d5efff',
      'shadow-blur': 18, 'shadow-color': '#38bdf8', 'shadow-opacity': 0.55,
    },
  },
  {
    selector: 'node[status = "SKIPPED"]',
    style: {
      'background-color': '#171f2e', 'border-color': '#3d4a63', color: '#7c8aa5',
      'border-style': 'dotted',
    },
  },
  {
    selector: 'node.selected',
    style: {
      'border-width': 3, 'border-color': '#f5b544',
      'shadow-blur': 20, 'shadow-color': '#f5b544', 'shadow-opacity': 0.5,
    },
  },
  {
    selector: 'edge',
    style: {
      width: 1.4,
      'line-color': '#33507d',
      'target-arrow-color': '#4a6a9c',
      'target-arrow-shape': 'triangle',
      'arrow-scale': 0.95,
      'curve-style': 'taxi',
      'taxi-direction': 'downward',
      'taxi-turn': '48%',
      'taxi-turn-min-distance': 8,
    },
  },
  {
    selector: 'edge[conditional = 1]',
    style: { 'line-style': 'dashed', 'line-color': '#5a4a86', 'target-arrow-color': '#7c6bb0' },
  },
];
