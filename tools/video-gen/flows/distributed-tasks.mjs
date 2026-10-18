// Tutorial 1: distributed task scheduling, starting from New Task. Covers the whole surface, not just
// CRUD: the three invocation types, an HTTP API task to a public endpoint, cron with Test, the visual
// cron builder, YCRON, options, create, the list with filter and the time-zone toggle, run-now with
// the real result, execution history, pause and resume, edit, executors, cluster, health, settings,
// and the REST API explorer.

export const meta = {
  out: 'distributed-tasks',
  introName: 'cron<b>flower</b>',
  version: '1.0.0-SNAPSHOT',
  tagline: 'Distributed cron for the JVM. Schedule a @Task across a self-clustering scheduler, with a console.',
};

const GROUP = 'demo';
const NAME = 'httpbinGet';
const step = async (label, fn) => {
  try { await fn(); } catch (e) { console.warn('[skip] ' + label + ': ' + e.message); }
};

export async function run(ui) {
  // New Task (start here)
  await step('new-task', async () => {
    await ui.goto('/tasks/new');
    await ui.say('Start a new task. Invoke a Spring bean, an HTTP API, or trigger a DAG.');
    await ui.moveTo('button.type-card:has-text("Spring Bean")');
    await ui.moveTo('button.type-card:has-text("Trigger DAG")');
    await ui.wait(1400);
  });

  await step('http-fields', async () => {
    await ui.say('Build an HTTP API task that calls a public endpoint directly.');
    await ui.click('button.type-card:has-text("HTTP API")');
    await ui.type('input[formcontrolname="taskGroup"]', GROUP, 28);
    await ui.type('input[formcontrolname="taskName"]', NAME, 28);
    await ui.click('mat-select[formcontrolname="httpMethod"]');
    await ui.pickOption('GET');
    await ui.type('input[formcontrolname="url"]', 'https://httpbin.org/get', 22);
    await ui.wait(700);
  });

  await step('cron-test', async () => {
    await ui.say('Schedule with cron, and Test previews the next fire times.');
    await ui.type('input[formcontrolname="cron"]', '0 0 12 * * ?', 40);
    await ui.click('button:has-text("Test")');
    await ui.wait(1800);
  });

  await step('build', async () => {
    await ui.say('Or build a cron visually, no syntax to remember.');
    await ui.click('button:has-text("Build")');
    await ui.wait(1600);
    await step('builder-test', async () => { await ui.click('.mat-mdc-dialog-container button:has-text("Test")'); await ui.wait(1600); });
    await ui.click('button:has-text("Use this")');
    await ui.wait(1000);
  });

  await step('ycron', async () => {
    await ui.say('YCRON adds year-based schedules, like the 200th day of the year.');
    await ui.click('mat-select[formcontrolname="parser"]');
    await ui.pickOption('YCRON');
    await ui.click('input[formcontrolname="cron"]');
    await ui.press('Meta+A'); await ui.press('Backspace');
    await ui.type('input[formcontrolname="cron"]', '0 0 12 ? ? 200', 40);
    await ui.click('button:has-text("Test")');
    await ui.wait(1800);
    await ui.click('mat-select[formcontrolname="parser"]');
    await ui.pickOption('Cron');
    await ui.click('input[formcontrolname="cron"]');
    await ui.press('Meta+A'); await ui.press('Backspace');
    await ui.type('input[formcontrolname="cron"]', '0 0 12 * * ?', 28);
  });

  await step('options', async () => {
    await ui.say('Retry, timeout, misfire policy, repeat count and stop-at, all declarative.');
    await ui.scrollBy(300); await ui.wait(1600); await ui.scrollBy(-300);
  });

  await step('create', async () => {
    await ui.say('Create the task.');
    await ui.click('button:has-text("Create task")');
    await ui.wait(1600);
  });

  await step('list', async () => {
    await ui.goto('/tasks');
    await ui.say('It joins the list: cron or YCRON, next fire, and run counts.');
    await ui.wait(1800);
    await step('filter', async () => {
      await ui.type('input', GROUP, 30);
      await ui.click('button:has-text("Filter")');
      await ui.wait(1600);
    });
    await step('tz', async () => {
      await ui.say('Times are UTC by default, with a per-viewer local toggle.');
      await ui.click('.tz-badge');
      await ui.click('button:has-text("Local")');
      await ui.wait(1500);
      await ui.click('.tz-badge');
      await ui.click('button:has-text("UTC")');
      await ui.wait(800);
    });
  });

  await step('run-now', async () => {
    await ui.goto('/tasks/' + GROUP + '/' + NAME);
    await ui.say('Run it now by hand to call the API immediately.');
    await ui.wait(1200);
    await ui.click('button:has-text("Run now")');
    await step('confirm', async () => {
      await ui.click('.mat-mdc-dialog-container button:has-text("Run now"), .cdk-overlay-container button:has-text("Run now")');
    });
    await ui.say('The run lands in the history: result, timing, scheduler and executor.');
    await ui.wait(1800);
    await ui.scrollBy(300); await ui.wait(1600); await ui.scrollBy(-300);
  });

  await step('pause-resume', async () => {
    await ui.goto('/tasks');
    await step('filter2', async () => {
      await ui.type('input', GROUP, 25);
      await ui.click('button:has-text("Filter")');
      await ui.wait(1000);
    });
    await ui.say('Pause and resume a task without a redeploy.');
    await ui.click('tbody tr:first-child button:has-text("more_vert")');
    await ui.click('button:has-text("Pause")');
    await ui.wait(1600);
    await ui.click('tbody tr:first-child button:has-text("more_vert")');
    await ui.click('button:has-text("Resume")');
    await ui.wait(1400);
  });

  await step('edit', async () => {
    await ui.say('Edit a task anytime, or withdraw and delete it.');
    await ui.click('tbody tr:first-child button:has-text("more_vert")');
    await ui.click('a:has-text("Edit")');
    await ui.wait(1800);
  });

  await step('executors', async () => {
    await ui.goto('/system/executors');
    await ui.say('Executors register on boot: liveness, dispatch weight, and callback URL.');
    await ui.wait(1900);
  });
  await step('cluster', async () => {
    await ui.goto('/system/cluster');
    await ui.say('Cluster: leader election, the auto-detected store, and group sharding.');
    await ui.wait(1900);
  });
  await step('health', async () => {
    await ui.goto('/system/health');
    await ui.say('Live health of every node.');
    await ui.wait(1600);
  });
  await step('settings', async () => {
    await ui.goto('/system/settings');
    await ui.say('Every effective setting, read-only.');
    await ui.wait(1600);
  });
  await step('api', async () => {
    await ui.goto('/system/api');
    await ui.say('And a built-in REST API explorer, live against the cluster.');
    await ui.wait(1900);
  });

  await ui.say('Distributed, stateful, no broker.');
  await ui.wait(1600);
}
