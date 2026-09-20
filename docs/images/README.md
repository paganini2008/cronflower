# Screenshots

Single source for every screenshot in the repo. They are referenced by the root
[`README.md`](../../README.md) and by the blog posts under `docs/blogger/` (which is not committed)
with relative paths, so they render on GitHub with no external hosting. Captured from a running
console (`deploy/run-local.sh`, <http://localhost:7200>, sign in `admin` / `admin123`) with the
time-zone toggle set to **UTC**.

| file | page | what it shows |
|------|------|---------------|
| `tasks-list.jpg` | Tasks | the task list with cron / **YCRON** schedules, runs, and next fire |
| `execution-history.jpg` | Task detail | the execution log with retries (a `flaky` run) + scheduler/executor |
| `executors.jpg` | Executors | registered executors with liveness, weight, and run URL |
| `cluster.jpg` | Cluster | three nodes, leader, detected store type, sharding |
| `dag-workflows.jpg` | DAG → Workflows | the registered `@Dag` workflows and the selected graph's shape |
| `dag-run.jpg` | DAG → Run | a completed run: the graph and how it was triggered |
| `dag-run-nodes.jpg` | DAG → Run | per-node results, including which executor ran each node |

To refresh a shot, retake it from the running console at ~1440px wide and overwrite the file in place.
