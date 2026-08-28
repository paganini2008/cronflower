# Architecture

cronsmith is a **distributed, stateful cron scheduler**. Responsibility is split three ways: the
**scheduler** owns the schedule and the durable state, **executors** own the business code, and the
**console** (cronflower) is the operator's window onto both.

```
                         ┌───────────────────────────────────────────────────┐
                         │                  cronflower (Angular)             │
                         │        Dashboard · Tasks · Executors · Cluster     │
                         └───────────────────────┬───────────────────────────┘
                                     /cronsmith, /actuator (one origin)
                                                 │
                 ┌───────────────────────────────┼───────────────────────────────┐
                 │                     scheduler cluster (server)                  │
                 │   ┌───────────┐     ┌───────────┐     ┌───────────┐             │
                 │   │scheduler-1│◀───▶│scheduler-2│◀───▶│scheduler-3│  spreader   │
                 │   │ (leader)  │     │ (follower)│     │ (follower)│  cluster    │
                 │   └─────┬─────┘     └─────┬─────┘     └─────┬─────┘  (leader     │
                 │         │ windowed load / claim / dispatch  │        election)  │
                 └─────────┼─────────────────┼─────────────────┼───────────────────┘
                           │                 │                 │
                     shared store  (H2 · MySQL · PostgreSQL — auto-detected)
                           │                 │                 │
                           ▼ dispatch (HTTP callback when a task is due)
                 ┌───────────┐     ┌───────────┐     ┌───────────┐
                 │ executor  │     │ executor  │     │ executor  │   @Task beans
                 │  :5xxxx    │     │  :5xxxx    │     │  :5xxxx    │   register on boot
                 └───────────┘     └───────────┘     └───────────┘
```

## Components

### Scheduler (`cronsmith-spring-boot-starter` → `cronsmith-scheduler-example`)
- A **spreader** cluster: nodes discover each other and elect a leader; the leader schedules and
  dispatches, followers serve reads and can take over on failover.
- **Windowed loading**: only tasks due within `window-minutes` are held in memory; the leader
  *claims* due tasks every `claim-interval-seconds` and dispatches them.
- **Store is auto-detected** from the JDBC connection: no DataSource → in-memory (node-local);
  H2/SQLite → node-local; MySQL/PostgreSQL/Oracle/SQL Server → **shared** (sharding-capable).
- **Group sharding** (over a shared store) partitions task groups across nodes for horizontal scale;
  **weighted dispatch** routes runs to executors by capacity.

### Executor (`cronsmith-executor-spring-boot-starter` → `cronsmith-executor-example`)
- On boot, scans beans for **`@Task`** methods, turns each into a definition and registers it with
  the scheduler. The scheduler owns the schedule; when a task is due it calls back into the method.
- Schedules: traditional **cron**, **YCRON** (year-based — week-of-year / day-of-year), fixed
  **interval**, or an **ISO-8601 duration**. Plus retry, per-run timeout, and a misfire policy.
- Tasks can also be created through the console/API against any executor bean method (e.g. a URL task).

### Console (`cronflower/frontend`)
- Angular standalone + signals; talks to **one** scheduler endpoint. Any node answers reads locally
  and routes writes to the leader, so a single address is enough (put a reverse proxy, e.g. nginx,
  in front of the pool in production).

## YCRON (year-based extension)
Traditional cron cannot express "the 200th day of the year" or "the first ISO week". YCRON adds a
year-scoped syntax — fields `‹sec› ‹min› ‹hour› ‹dow› ‹woy› ‹doy› ‹year›` — fully isolated from the
traditional parser. Pick it per task with `@Task(parser = "ycron")` (or the console's Syntax selector).
The engine still prefers traditional cron whenever a schedule *can* be expressed traditionally.

## Persistence & serialization
Task schedules are stored as a compact binary that fully reconstructs the expression tree (including
its `CronType`, so cron vs. YCRON survives a round-trip). The `cron_expression` binary column is the
source of truth; a human-readable `cron` string is kept alongside for display.
