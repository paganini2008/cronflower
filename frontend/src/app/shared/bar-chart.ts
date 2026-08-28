import { Component, computed, input } from '@angular/core';

export interface Bar {
  label: string;
  value: number;
  sub?: string;
}

@Component({
  selector: 'cf-bars',
  template: `
    @if (rows().length) {
      <div class="bars">
        @for (b of rows(); track b.label) {
          <div class="bar-row">
            <div class="bar-head">
              <span class="bar-label">{{ b.label }}</span>
              <span class="bar-value">{{ b.value }}</span>
            </div>
            <div class="bar-track">
              <div class="bar-fill" [style.width.%]="b.pct"></div>
            </div>
            @if (b.sub) { <div class="bar-sub">{{ b.sub }}</div> }
          </div>
        }
      </div>
    } @else {
      <p class="muted">No data yet.</p>
    }
  `,
  styles: [`
    .bars { display: flex; flex-direction: column; gap: 0.85rem; }
    .bar-head { display: flex; justify-content: space-between; font-size: 0.85rem; margin-bottom: 0.2rem; }
    .bar-label { color: #334155; font-weight: 500; }
    .bar-value { color: #0f2c4d; font-weight: 700; }
    .bar-track { height: 8px; background: #eef2f7; border-radius: 999px; overflow: hidden; }
    .bar-fill { height: 100%; border-radius: 999px;
      background: linear-gradient(90deg, #1565c0, #42a5f5); transition: width .4s ease; }
    .bar-sub { font-size: 0.72rem; color: #94a3b8; margin-top: 0.15rem; }
  `],
})
export class BarChart {
  readonly items = input.required<Bar[]>();

  protected readonly rows = computed(() => {
    const items = this.items();
    const max = Math.max(1, ...items.map((i) => i.value));
    return items.map((i) => ({ ...i, pct: (i.value / max) * 100 }));
  });
}
