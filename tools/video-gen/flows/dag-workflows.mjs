// The one cronflower tutorial: a single end-to-end walkthrough, 3 to 5 minutes. It opens on the
// console and scheduled tasks (bean / HTTP / script / DAG-trigger, each tagged), then covers the whole
// DAG surface: the workflow graphs, authoring a new @Dag on the canvas or as code, GENERATING one from
// a plain-language prompt with OpenAI, triggering a real run, watching it flow node by node across the
// cluster, the per-node detail, the run history, and the dark theme.

export const meta = {
  out: 'dag-workflows',
  introName: 'cron<b>flower</b>',
  version: '1.0.0-SNAPSHOT',
  tagline: 'From scheduled tasks to AI-authored DAG workflows, run across the cluster.',
};

const RUNGRAPH = 'fulfilment-flow'; // seeded demo @Dag: pickStock fans out, then joins at handToCourier
const AI_PROMPT =
  'Fetch orders from http://localhost:8000/fetch-orders, score them at /score-orders, ' +
  'then decide at /decide. Pass each response to the next step.';

const step = async (label, fn) => {
  try { await fn(); } catch (e) { console.warn('[skip] ' + label + ': ' + e.message); }
};

export async function run(ui) {
  // ============================ PART 1 — the console & tasks ============================
  await step('dashboard', async () => {
    await ui.goto('/dashboard');
    await ui.say('One console for the whole cluster: tasks, executors, DAG runs, and health, live.');
    await ui.wait(6000);
    await ui.scrollBy(220); await ui.wait(2600); await ui.scrollBy(-220);
  });

  await step('tasks', async () => {
    await ui.goto('/tasks');
    await ui.say('Every scheduled task, each tagged by kind: a Spring bean, an HTTP call, a script, or a DAG trigger.');
    await ui.wait(6000);
    await ui.scrollBy(240); await ui.wait(2600); await ui.scrollBy(-240);
  });

  await step('script-task', async () => {
    await ui.say('A task can also be a shell, Python, Java or SQL script, run in a per-run Docker sandbox with CPU, memory and network limits.');
    await ui.wait(5200);
    await ui.scrollBy(320); await ui.wait(3200); await ui.scrollBy(-320);
  });

  await step('task-detail', async () => {
    await step('open', async () => { await ui.click('.task-link'); await ui.wait(1600); });
    await ui.say('Cron or year-based YCRON, retries with back-off, a per-run timeout, and a misfire policy, all declarative.');
    await ui.wait(4800);
    await ui.scrollBy(260); await ui.wait(2600); await ui.scrollBy(-260);
  });

  await step('executors', async () => {
    await ui.goto('/system/executors');
    await ui.say('Executors register and heartbeat. The scheduler cluster dispatches due runs to them, weighted by capacity.');
    await ui.wait(5000);
  });

  await step('cluster', async () => {
    await ui.goto('/system/cluster');
    await ui.say('The scheduler nodes gossip and elect a leader, with no ZooKeeper or etcd. Followers fail over, so no run fires twice or goes missing.');
    await ui.wait(5600);
  });

  // ============================ PART 2 — DAG workflows ============================
  await step('workflows', async () => {
    await ui.goto('/dag/workflows');
    await ui.say('Workflows are @Dag definitions. Here: a fan-out to packBox and printLabel, then a join at handToCourier.');
    await ui.wait(1200);
    await ui.click(`button.row:has-text("${RUNGRAPH}"), button.row:first-child`);
    await ui.wait(5600);
    await ui.moveTo('cf-dag-graph, .graph'); await ui.wait(3400);
  });

  await step('new-canvas', async () => {
    await ui.click('a:has-text("New workflow"), button:has-text("New workflow")');
    await ui.wait(1400);
    await ui.say('Author a new @Dag on the canvas, with nodes and conditional edges.');
    await ui.wait(3400);
    await step('code-tab', async () => {
      await ui.click('button[role="tab"]:has-text("Code"), button:has-text("Code")');
      await ui.wait(1400);
      await ui.say('Or write it directly as JSON or YAML, channels, triggers and all.');
      await ui.wait(3600);
    });
  });

  await step('ai-generate', async () => {
    await ui.goto('/dag/ai');
    await ui.say('Or just describe the workflow in plain language, and let an LLM draft it.');
    await ui.wait(2600);
    await ui.type('textarea', AI_PROMPT, 24);
    await ui.wait(1000);
    // Pick an OpenAI model in the Model dropdown (robust: find the option by value/text, set + fire change).
    await step('pick-openai', async () => {
      await ui.page.evaluate(() => {
        const sel = document.querySelector('select');
        if (!sel) return;
        const opt = Array.from(sel.options).find((o) => /openai/i.test(o.value) || /openai/i.test(o.textContent || ''));
        if (opt) { sel.value = opt.value; sel.dispatchEvent(new Event('change', { bubbles: true })); }
      });
      await ui.wait(900);
    });
    await ui.say('Generate the DAG with OpenAI, fast enough to watch.');
    await step('generate', async () => {
      await ui.click('button:has-text("Generate")');
    });
    await ui.wait(9000);   // OpenAI returns in a few seconds; the graph then renders
    await ui.say('The model drafts HTTP nodes that call each endpoint, the output of one feeding the input of the next through channels.');
    await ui.wait(6400);
    await ui.scrollBy(240); await ui.wait(3400); await ui.scrollBy(-240);
  });

  // ============================ PART 3 — a live run ============================
  await step('trigger', async () => {
    await ui.goto('/dag/workflows');
    await ui.wait(1200);
    await ui.click(`button.row:has-text("${RUNGRAPH}")`);
    await ui.wait(1000);
    await ui.say('Trigger a real run and watch it move through the graph.');
    await ui.click('button:has-text("Trigger")');
    await ui.wait(1600);
    await step('confirm', async () => {
      await ui.click('.cdk-overlay-container button:has-text("Trigger"), .mat-mdc-dialog-container button:has-text("Trigger")');
    });
    await ui.wait(2800);
  });

  await step('open-run', async () => {
    if (!/\/dag\/runs\//.test(ui.page.url())) {
      await ui.goto('/dag/runs');
      await ui.wait(1200);
      await step('pick', async () => { await ui.click('tbody tr:first-child, .row:first-child, a[href*="/dag/runs/"]'); });
    }
    await ui.wait(1400);
  });

  await step('live-flow', async () => {
    await ui.say('It flows node by node across the cluster, the graph coloured by each outcome.');
    await ui.wait(5200);
    await step('r1', async () => { await ui.click('button:has-text("Refresh")'); await ui.wait(4400); });
    await ui.say('The entry node fires, fans out, and each branch runs on whichever executor is free.');
    await step('r2', async () => { await ui.click('button:has-text("Refresh")'); await ui.wait(4600); });
    await step('r3', async () => { await ui.click('button:has-text("Refresh")'); await ui.wait(4400); });
    await ui.say('The branches join, and the run completes.');
    await step('r4', async () => { await ui.click('button:has-text("Refresh")'); await ui.wait(4200); });
  });

  await step('node-detail', async () => {
    await ui.say('Each node reports its status, elapsed time, channel writes, and the executor that ran it.');
    await ui.scrollBy(300); await ui.wait(5000); await ui.scrollBy(-300);
  });

  await step('runs-history', async () => {
    await ui.goto('/dag/runs');
    await ui.say('A full run history, filterable by workflow and status.');
    await ui.wait(4600);
    await ui.scrollBy(240); await ui.wait(2200); await ui.scrollBy(-240);
  });

  await step('dark-theme', async () => {
    await ui.goto('/dag/workflows');
    await ui.wait(1200);
    await ui.say('Light or dark, whichever you prefer.');
    await step('toggle', async () => {
      await ui.page.evaluate(() => {
        const b = Array.from(document.querySelectorAll('button')).find((x) => {
          const i = x.querySelector('mat-icon');
          return i && /dark_mode|light_mode/.test(i.textContent || '');
        });
        if (b) b.click();
      });
    });
    await ui.wait(3400);
  });

  await ui.say('Tasks, scripts, DAGs, HTTP, and AI. Same cluster, one console.');
  await ui.wait(4200);
}
