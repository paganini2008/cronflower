import { Component, computed, input } from '@angular/core';

export interface DonutSegment {
  label: string;
  value: number;
  color: string;
}

interface Arc extends DonutSegment {
  dash: string;
  offset: number;
  pct: number;
}

@Component({
  selector: 'cf-donut',
  template: `
    <div class="donut-wrap">
      <svg viewBox="0 0 42 42" class="donut" role="img">
        <circle class="ring-bg" cx="21" cy="21" r="15.915" fill="none" stroke-width="4" />
        @for (a of arcs(); track a.label) {
          <circle
            cx="21" cy="21" r="15.915" fill="none" stroke-width="4" stroke-linecap="butt"
            [attr.stroke]="a.color"
            [attr.stroke-dasharray]="a.dash"
            [attr.stroke-dashoffset]="a.offset"
          />
        }
        <text x="21" y="20.5" class="donut-total">{{ total() }}</text>
        <text x="21" y="25.5" class="donut-caption">{{ caption() }}</text>
      </svg>
      <ul class="legend">
        @for (a of arcs(); track a.label) {
          <li>
            <span class="dot" [style.background]="a.color"></span>
            <span class="lg-label">{{ a.label }}</span>
            <span class="lg-value">{{ a.value }}</span>
          </li>
        }
      </ul>
    </div>
  `,
  styles: [`
    .donut-wrap { display: flex; align-items: center; gap: 1.5rem; flex-wrap: wrap; }
    .donut { width: 170px; height: 170px; transform: rotate(-90deg); }
    .ring-bg { stroke: #eef2f7; }
    .donut circle { transition: stroke-dasharray .4s ease; }
    .donut-total { transform: rotate(90deg); transform-origin: 21px 21px; text-anchor: middle;
      font-size: 7px; font-weight: 700; fill: #0f2c4d; }
    .donut-caption { transform: rotate(90deg); transform-origin: 21px 21px; text-anchor: middle;
      font-size: 2.6px; fill: #94a3b8; letter-spacing: .3px; }
    .legend { list-style: none; margin: 0; padding: 0; min-width: 160px; flex: 1; }
    .legend li { display: flex; align-items: center; gap: 0.5rem; padding: 0.2rem 0; font-size: 0.85rem; }
    .dot { width: 0.7rem; height: 0.7rem; border-radius: 3px; flex: none; }
    .lg-label { color: #475569; flex: 1; }
    .lg-value { font-weight: 700; color: #0f2c4d; }
  `],
})
export class DonutChart {
  readonly segments = input.required<DonutSegment[]>();
  readonly caption = input('total');

  protected readonly total = computed(() => this.segments().reduce((a, s) => a + s.value, 0));

  protected readonly arcs = computed<Arc[]>(() => {
    const segs = this.segments().filter((s) => s.value > 0);
    const total = this.total();
    if (total === 0) {
      return [];
    }
    let cumulative = 0;
    return segs.map((s) => {
      const pct = (s.value / total) * 100;
      const arc: Arc = {
        ...s,
        pct,
        dash: `${pct} ${100 - pct}`,
        offset: (100 - cumulative + 25) % 100,
      };
      cumulative += pct;
      return arc;
    });
  });
}
