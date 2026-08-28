# Screenshots

The root `README.md` references the images below. They are **not** committed here — capture them from
a running console and drop them in (or upload to GCS and swap the README links for the GCS URLs).

Suggested shot list (start the stack with `deploy/run-local.sh -e 1`, open <http://localhost:7200>,
sign in `admin` / `admin`):

| file | page | what to show |
|------|------|--------------|
| `dashboard.png` | Dashboard | task counts by status + executor summary tiles |
| `tasks-list.png` | Tasks | the task list with the cron / **YCRON** type icons in the schedule column |
| `task-form.png` | Tasks → New | the create form with the **Syntax** selector (Cron / YCRON) |
| `task-detail.png` | Task detail | schedule, next fire times, and the execution log (incl. a retried `flaky` run) |
| `executors.png` | Executors | registered executors with liveness + weight |
| `cluster.png` | Cluster | nodes, leader, detected store type, sharding |
| `system-health.png` | System Health | actuator health incl. the `spreaderCluster` component |

Keep them reasonably sized (≈1600px wide, PNG). Recommended aspect keeps the README tidy.
