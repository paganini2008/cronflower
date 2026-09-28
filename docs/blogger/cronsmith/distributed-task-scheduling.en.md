# cronflower: turn a Spring Boot app into a distributed cron cluster

`@Scheduled` is fine until it isn't. It runs in one JVM, so the moment you scale to two instances the
job fires twice. It has no retry, no timeout, no record of what ran, and if the box reboots at 02:00
the nightly rollup just quietly doesn't happen. You end up bolting on Quartz, a database, a lock
table, and a dashboard you wrote yourself.

**cronflower** is that whole stack, open source: a distributed, stateful scheduler for Spring Boot
with a web console, that forms its own cluster and needs no external database, broker or coordinator.
It does two things, and this post is about one of them: distributed *task* scheduling (a companion
post, same product, covers its DAG workflows). You annotate a method, run cronflower alongside it, and
get a clustered, persistent, retrying scheduler with a console, without writing any of the plumbing.
This is the usage tour.

## Two roles: a scheduler and an executor

There are two kinds of process:

- A **scheduler** owns the schedule. It keeps task state in a store, decides when each task is due,
  and calls out to run it. Run several and they form a cluster with a leader.
- An **executor** is your application. It declares tasks with `@Task` and runs the code when the
  scheduler says it's time.

Your business code lives in the executor. The scheduler is infrastructure you run alongside it.

## Declare a task

On the executor, a task is a bean method with `@Task`. It's discovered on startup and registered
with the scheduler, which then owns its schedule:

```java
@Component
public class DemoTasks {

    // Traditional cron, every 5 seconds; takes its parameter as a String.
    @Task(cron = "*/5 * * * * ?", group = "showcase", name = "sayHello", initialParameter = "world")
    public String sayHello(String who) {
        return "hello, " + who + " @ " + LocalDateTime.now();
    }

    // Fixed interval, no cron needed.
    @Task(interval = 10, intervalUnit = TimeUnit.SECONDS, group = "showcase", name = "every10s")
    public void every10s() { /* ... */ }

    // An ISO-8601 duration for the intervals cron can't span (every 90 minutes).
    @Task(iso = "PT1H30M", group = "showcase", name = "every90m")
    public void every90m() { /* ... */ }
}
```

The parameter is optional. A method can take nothing, or a single `String` (the task's
`initialParameter`). That parameter can be a constant, or a SpEL template evaluated on the executor
at run time, so a value like today's date is fresh on every fire:

```java
@Task(cron = "0 0 * * * ?", group = "showcase", name = "heartbeatTick",
      initialParameter = "#{T(java.time.LocalDate).now().toString()}")
public void heartbeatTick(String today) { /* today is recomputed every hour */ }
```

Need a schedule the month-based grammar can't say, like "noon on the 200th day of the year"? Switch
the parser to year-based YCRON:

```java
@Task(cron = "0 0 12 ? ? 200", parser = "ycron", group = "showcase", name = "dayOfYear200")
public void dayOfYear200() { /* ... */ }
```

Every registered task shows up in the console, with its schedule, run count and next fire time:

![The task list, one row per @Task](https://raw.githubusercontent.com/paganini2008/cronflower/main/docs/images/tasks-list.jpg)

## Reliability you configure, not code

The awkward parts of running a job in production are annotation attributes, and the **scheduler**
enforces them so every executor behaves the same way.

**Retry with back-off.** Set `maxRetryCount` and `retryInterval`; a failed run is retried, and every
attempt is logged:

```java
@Task(cron = "*/20 * * * * ?", group = "reliability", name = "flaky",
      maxRetryCount = 2, retryInterval = 1000)
public String flaky(String parameter) {
    // fails twice, succeeds on the third attempt
}
```

In the execution history that single fire is three rows: attempt `#0` fails, `#1` fails, `#2`
succeeds. The same view tells you **which scheduler node dispatched it and which executor ran it**,
which is the whole point of a cluster:

![Execution history showing retry attempts and the node that ran each one](https://raw.githubusercontent.com/paganini2008/cronflower/main/docs/images/execution-history.jpg)

**Per-run timeout.** `timeout = 2000` marks a run that overruns 2s as timed-out instead of letting
it hang:

```java
@Task(cron = "0 */5 * * * ?", group = "reliability", name = "slowJob", timeout = 2000)
public void slowJob() throws InterruptedException { Thread.sleep(5000); }
```

**Misfire policy.** If the scheduler was down when a fire time passed, decide what to do on recovery:
`SKIP` it, `FIRE_ONCE_NOW`, or `FIRE_ALL` the missed ones.

```java
@Task(cron = "0 0 2 * * ?", group = "reliability", name = "nightlyRollup", misfirePolicy = "SKIP")
public void nightlyRollup() { /* 02:00 daily; skips a missed fire rather than catching up */ }
```

**Bounded runs.** `repeatCount` finishes a task after a fixed number of fires; `stopAt` gives it a
deadline. A one-off warm-up that runs ten times and then retires:

```java
@Task(interval = 30, intervalUnit = TimeUnit.SECONDS, group = "ops", name = "warmup", repeatCount = 10)
public void warmup() { /* runs 10 times, then finishes on its own */ }
```

That's the whole annotation. The full attribute set, for reference:

| Attribute                     | What it does                                             |
|-------------------------------|----------------------------------------------------------|
| `cron` / `parser`             | a cron schedule (or YCRON, with `parser = "ycron"`)      |
| `interval` + `intervalUnit`   | a fixed interval, e.g. every 10 seconds                  |
| `iso`                         | a fixed interval as an ISO-8601 duration, e.g. `PT1H30M` |
| `initialParameter`            | a constant, or a SpEL template evaluated on each fire    |
| `group` / `name` / `description` | identity, and the label the console shows              |
| `maxRetryCount` / `retryInterval` | retry a failed run, with back-off                    |
| `timeout`                     | fail a run that overruns, in milliseconds                |
| `misfirePolicy`               | `FIRE_ONCE_NOW` (default) / `FIRE_ALL` / `SKIP`          |
| `repeatCount`                 | total fires before the task finishes (default unlimited) |
| `stopAt`                      | an ISO date-time after which it stops firing             |

## Not every task needs an executor

Some jobs are just "call this URL on a schedule" and don't deserve a bean at all. Create an
**HTTP-API task** and the scheduler node makes the call itself, no executor involved. You don't write
`@Task` for these; you create them from the console's *New task* form or the REST API, which is the
same way an operator adds or edits any task without a redeploy:

```
POST /cronsmith/tasks
{ "taskGroup": "ops", "taskName": "pingHealth", "taskType": "HTTP",
  "url": "https://example.com/health", "httpMethod": "GET",
  "cron": "0 */5 * * * ?" }
```

So there are two kinds of task: **bean tasks**, dispatched to an executor that hosts the code, and
**HTTP-API tasks**, run on the scheduler itself. Both share the schedules, retries, timeouts and
history above.

## It's a cluster, not a cron box

Run more than one scheduler and they elect a leader over gossip. Task state lives in a store, so a
restart or a failover loses nothing: the survivors keep firing, and a node that comes back rejoins
and reloads. No cron job fires twice, and none goes missing.

The console shows the cluster at a glance: the nodes, who's leader, the backing store, and the
scheduling mode.

![The cluster view: three scheduler nodes, one leader and two standby, over a replicated store](https://raw.githubusercontent.com/paganini2008/cronflower/main/docs/images/cluster.jpg)

The store is auto-detected from your datasource. With no database it's in-memory; point it at H2,
SQLite, MySQL or PostgreSQL and it persists there. A **node-local** store (per-node H2/SQLite) is
kept in sync by the leader; a **shared** database (MySQL/PostgreSQL) additionally unlocks **group
sharding**, where every node fires only the task groups that hash to it, spreading the load:

```properties
# turn a shared-DB cluster from leader-only into sharded
cronsmith.server.scheduler.sharding=true
```

On a node-local store it safely stays leader-only, so this setting is always safe to leave on.

Each executor registers itself and heartbeats, so the scheduler always dispatches to a live one:

![Registered executors, live and weighted](https://raw.githubusercontent.com/paganini2008/cronflower/main/docs/images/executors.jpg)

When an application runs several executors, the leader picks one per run. Round-robin by default;
switch it with `cronsmith.server.dispatch.routing` to `WEIGHTED` (by each executor's `weight`),
`CONSISTENT_HASH` (sticky, so one task group always lands on the same executor), `RANDOM`, `FIRST`
or `LAST`.

A word on how it scales: the leader does not hold your whole schedule in memory. It loads only the
tasks due in the next few minutes into a timing wheel and claims the rest from the store as they come
due, so a cluster with hundreds of thousands of tasks still starts instantly. Tune the horizon with
`cronsmith.server.scheduler.window-minutes`. Fire times are computed in `cronsmith.server.scheduler.zone`
(UTC by default), which must match across nodes.

## Run, pause, inspect

Everything an operator needs is on the task, over the REST API or the console: run once now, pause,
resume, cancel, and the full execution history with input, result and elapsed time per run. A paused
task holds its state and stops firing until you resume it; a canceled one stops but keeps its history.
Health and a Prometheus scrape endpoint (`/actuator/health`, `/actuator/prometheus`) come with the
starter, so the cluster drops straight into your existing monitoring.

## Run it

Clone [cronflower](https://github.com/paganini2008/cronflower) and one command brings up the whole
thing, a scheduler, the console, and an executor, over an embedded store with nothing to provision:

```bash
git clone https://github.com/paganini2008/cronflower
cd cronflower/deploy
./run-local.sh -e 1          # scheduler + console + 1 executor
```

Open <http://localhost:7200> and sign in with `admin` / `admin123`. Scale it into a real cluster with
`./run-local.sh -n 3 -e 2` (three schedulers, two executors), or `./run-docker.sh` for the
containerised version. The bundled executor already ships the `@Task` methods from this post, so
everything you have seen here is live the moment the console opens; declare your own `@Task` beans to
add tasks of your own.
