// Tutorial 2: DAG workflow orchestration, starting from New Workflow. Covers the whole surface, not
// just CRUD: authoring a real @Dag on the canvas / as code, creating it against a live executor,
// the workflow graphs, triggering a REAL run, watching it flow node by node across the cluster, the
// per-node detail, and the run history.

export const meta = {
  out: 'dag-workflows',
  introName: 'cron<b>flower</b>',
  version: '1.0.0-SNAPSHOT',
  tagline: 'DAG workflow orchestration. Declare a @Dag of @DagNode steps and run it across the cluster.',
};

const GRAPH = 'ui-demo-flow';     // authored on the canvas in this tutorial
const RUNGRAPH = 'fulfilment-flow'; // identical shape, reliably runnable, used for the live run
// A real, runnable graph: it reuses the demo executor's fulfilment-flow beans (pickStock fans out to
// packBox and printLabel, which fan in to handToCourier), so the created DAG actually runs.
const DEF = {
  graph: GRAPH,
  inputs: [],
  channels: [{ name: 'steps', reducer: 'CONCAT_LIST' }],
  nodes: [
    { name: 'pickStock', entry: true, trigger: 'ALL', retries: 0, beanName: 'fulfilmentFlow', methodName: 'pickStock', subgraph: '' },
    { name: 'packBox', entry: false, trigger: 'ALL', retries: 0, beanName: 'fulfilmentFlow', methodName: 'packBox', subgraph: '' },
    { name: 'printLabel', entry: false, trigger: 'ALL', retries: 0, beanName: 'fulfilmentFlow', methodName: 'printLabel', subgraph: '' },
    { name: 'handToCourier', entry: false, trigger: 'ALL', retries: 0, beanName: 'fulfilmentFlow', methodName: 'handToCourier', subgraph: '' },
  ],
  edges: [
    { from: 'pickStock', to: 'packBox', condition: 'ON_SUCCESS' },
    { from: 'pickStock', to: 'printLabel', condition: 'ON_SUCCESS' },
    { from: 'packBox', to: 'handToCourier', condition: 'ON_SUCCESS' },
    { from: 'printLabel', to: 'handToCourier', condition: 'ON_SUCCESS' },
  ],
  conditionals: [],
};

const step = async (label, fn) => {
  try { await fn(); } catch (e) { console.warn('[skip] ' + label + ': ' + e.message); }
};

export async function run(ui) {
  // New Workflow (start here)
  await step('new-workflow', async () => {
    await ui.goto('/dag/new');
    await ui.say('Author a @Dag on the canvas, or write it as JSON or YAML.');
    await ui.moveTo('button:has-text("Add node")');
    await ui.moveTo('button:has-text("Connect")');
    await ui.wait(1600);
  });

  await step('code-author', async () => {
    await ui.say('Name it, choose a live executor, and write the definition.');
    await ui.type('.toolbar input, input[placeholder*="flow"]', GRAPH, 28);
    await step('app', async () => { await ui.page.selectOption('.toolbar select', { index: 0 }); await ui.wait(300); });
    await ui.click('button[role="tab"]:has-text("Code")');
    await ui.wait(600);
    await ui.paste('textarea.code', JSON.stringify(DEF, null, 2));
    await ui.wait(1800);
  });

  await step('create', async () => {
    await ui.say('Create it. It registers against the executor and can run at once.');
    await ui.click('button:has-text("Create")');
    await ui.wait(1800);
    if (!/\/dag\/workflows/.test(ui.page.url())) { await ui.goto('/dag/workflows'); await ui.wait(600); }
  });

  await step('workflows', async () => {
    await ui.say('Our graph: a fan-out to packBox and printLabel, then a join. Branches, subgraphs and @Shard fan-out too.');
    await ui.click(`button.row:has-text("${GRAPH}"), button.row:first-child`);
    await ui.wait(2800);
  });

  await step('trigger', async () => {
    await ui.say('Trigger a run and watch it move through the graph.');
    await ui.click(`button.row:has-text("${RUNGRAPH}")`);   // select a runnable graph of the same shape
    await ui.wait(500);
    await ui.click('button:has-text("Trigger")');
    await ui.wait(1400);
    await ui.say('Fire a real run.');
    await ui.click('.mat-mdc-dialog-container button:has-text("Trigger"), .cdk-overlay-container button:has-text("Trigger")');
    await ui.wait(2400);     // let the dags page auto-navigate to this run
  });

  await step('open-run', async () => {
    // Fall back to the runs page filtered to the graph we fired (demo kickoff tasks create many runs).
    if (!/\/dag\/runs\//.test(ui.page.url())) {
      await ui.goto('/dag/runs');
      await ui.wait(700);
      await step('filter-run', async () => { await ui.click('mat-select'); await ui.pickOption(RUNGRAPH); await ui.wait(900); });
      await ui.click('tbody tr:first-child, mat-row:first-child, .row:first-child, a[href*="/dag/runs/"], td');
    }
    await ui.wait(1200);
  });

  await step('live-flow', async () => {
    await ui.say('Watch it flow node by node across the cluster, the graph coloured by each outcome.');
    await ui.wait(3400);
    await step('refresh', async () => { await ui.click('button:has-text("Refresh")'); await ui.wait(2600); });
    await step('refresh2', async () => { await ui.click('button:has-text("Refresh")'); await ui.wait(2600); });
  });

  await step('node-detail', async () => {
    await ui.say('Each node reports its status, elapsed time, channel writes, and the executor that ran it.');
    await ui.scrollBy(260); await ui.wait(2600); await ui.scrollBy(-260);
  });

  await step('runs', async () => {
    await ui.goto('/dag/runs');
    await ui.say('Run history, filterable by workflow and status.');
    await ui.wait(2400);
    await ui.scrollBy(220); await ui.wait(1600); await ui.scrollBy(-220);
  });

  await ui.say('Same cluster, now a workflow engine.');
  await ui.wait(2000);
}
