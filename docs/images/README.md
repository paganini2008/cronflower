# Screenshots

These images are referenced by the root [`README.md`](../../README.md) with relative paths, so they
render on GitHub with no external hosting. They were captured from a running console
(`deploy/run-local.sh -e 1`, <http://localhost:7200>, sign in `admin` / `admin`) with the time-zone
toggle set to **UTC**.

| file | page | what it shows |
|------|------|---------------|
| `dashboard.jpg` | Dashboard | task counts by status + executor / cluster summary tiles |
| `tasks-list.jpg` | Tasks | the task list with cron / **YCRON** schedules, runs, and next fire |
| `task-detail.jpg` | Task detail | schedule, next/previous fire, and **repeat count / stop-at** |
| `task-form.jpg` | Tasks → New | the create form (Spring Bean / HTTP API invocation) |
| `task-edit.jpg` | Tasks → Edit | the schedule & options, incl. repeat count and stop-at |
| `execution-history.jpg` | Task detail | the execution log with retries (a `flaky` run) + scheduler/executor |
| `executors.jpg` | Executors | registered executors with liveness, weight, and run URL |
| `cluster.jpg` | Cluster | nodes, leader, detected store type, sharding |
| `system-health.jpg` | System Health | actuator health incl. the `spreaderCluster` component |
| `timezone-toggle.jpg` | (top bar) | the UTC ↔ local time-zone switcher |

To refresh a shot, retake it from the running console at ~1440px wide and overwrite the file in place.
