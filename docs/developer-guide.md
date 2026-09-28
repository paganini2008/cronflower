---
title: Developer Guide
---

# Developer Guide

This guide is for developers who want to go past the defaults: declare tasks and workflows, and plug
in their own behaviour. It assumes you can already run the stack (see the
[README quickstart](https://github.com/paganini2008/cronflower#quickstart)) and know the basics from
[Architecture](architecture.html) and [Configuration](configuration.html).

## Mental model

There are two kinds of process, and **which one an extension lives in matters**:

- **Executor** — *your* application. It hosts `@Task` / `@Dag` beans and runs the code when the
  scheduler calls back. Add the `cronsmith-executor-spring-boot-starter` (and, for DAGs,
  `cronflow-executor-spring-boot-starter`).
- **Scheduler** — the server process that owns schedules and durable state, elects a leader, and
  dispatches runs. It runs the `cronsmith-spring-boot-starter` (and optionally
  `cronflow-spring-boot-starter`).

Almost every moving part is an ordinary Spring bean guarded by `@ConditionalOnMissingBean`, so you
extend the platform by **defining your own bean of the same type** — no forking. Put the bean in the
right process:

| I want to… | Extension | Lives in |
|------------|-----------|----------|
| Schedule a method | `@Task` | Executor |
| Build a schedule in code (incl. a deadline) | `CronExpressionBuilder` bean | Executor |
| Orchestrate steps | `@Dag` / `@DagNode` (or a `CronflowDag` bean) | Executor |
| Merge concurrent channel writes my own way | `Reducer` bean + `@Channel(customReducer=…)` | Scheduler (bean) + Executor (annotation) |
| Change how runs are routed to executors | property, or a `TaskDispatcher` bean | Scheduler |
| Store schedules in my own backend | `TaskManager` bean / `StoreType.register` | Scheduler |
| Define a task inside the scheduler itself | implement `Task` (+ `handleResult`) | Scheduler |
| Observe every task | `TaskListener` / `ErrorHandler` bean | Scheduler |
| Swap the executor↔server transport | `CronsmithServerClient` / `CronflowServerClient` bean | Executor |
| Change how a DAG node is invoked | `NodeExecutionService` bean | Executor |

---

## Declaring work

### Tasks — `@Task`

A task is a bean method annotated with `@Task` (annotation:
`cronsmith-executor-spring-boot-starter` → `executor/Task.java`). The method takes no argument or a
single `String` (the `initialParameter`); a non-void return value is logged.

```java
@Component
public class DemoTasks {

    @Task(cron = "0 0 12 * * ?", group = "showcase", name = "nightlyRollup",
          maxRetryCount = 2, retryInterval = 1000, timeout = 30_000, repeatCount = 30,
          misfirePolicy = "FIRE_ONCE_NOW")
    public void nightlyRollup() { /* ... */ }

    // initialParameter can be a SpEL template, evaluated on the executor on every fire.
    @Task(cron = "0 0 * * * ?", group = "showcase", name = "heartbeatTick",
          initialParameter = "#{T(java.time.LocalDate).now().toString()}")
    public void heartbeatTick(String today) { /* today is fresh each hour */ }

    // A schedule the month-based grammar can't say: noon on the 200th day of the year.
    @Task(cron = "0 0 12 ? ? 200", parser = "ycron", group = "showcase", name = "dayOfYear200")
    public void dayOfYear200() { /* ... */ }
}
```

The full attribute set (`cron`, `parser`, `interval`+`intervalUnit`, `iso`, `builder`, `group`,
`name`, `description`, `initialParameter`, `maxRetryCount`, `retryInterval`, `timeout`,
`misfirePolicy`, `repeatCount`) is documented in the
[`@Task` reference](configuration.html#task-reference). One gotcha: **there is no `stopAt`
attribute** — a constant can't express "now + N", so a deadline is set through a `CronExpressionBuilder`
(below). `misfirePolicy` is one of `FIRE_ONCE_NOW` (default), `SKIP`, `FIRE_ALL`.

### Schedules in code — `CronExpressionBuilder` + `CronBuilder`

For a schedule you'd rather compute (and self-validate) than hand-type, point the task at a builder
bean. `CronExpressionBuilder` (`cronsmith-executor-spring-boot-starter` →
`executor/CronExpressionBuilder.java`) is the SPI, and `CronBuilder` (`cronsmith` engine →
`cron/CronBuilder.java`) is the fluent expression API. When `builder` is set it **wins over**
`cron` / `parser` / `interval` / `iso` / `repeatCount`, and it is the **only way to set a deadline**.

```java
@Component("weeklyForAMonth")
class WeeklyForAMonth implements CronExpressionBuilder {
    @Override public String buildCron() {
        return new CronBuilder().everyWeek().Mon().at(9, 0).toString();  // validated in code
    }
    @Override public int getRepeatCount() { return 4; }                 // stop after 4 fires
    @Override public LocalDateTime getStopAt() {                        // …or by a deadline
        return LocalDateTime.now().plusMonths(1);
    }
}

@Task(builder = "weeklyForAMonth", description = "weekly report, for a month")
public void weeklyReport() { /* ... */ }
```

### Workflows — `@Dag`

A DAG is a bean carrying `@Dag`; each `@DagNode` method is a step, and the `to` list is the edges.
Nodes hand data to each other by returning a `Map` of named channel writes and read upstream values
from a `DagState`. Annotations live in `cronflow-executor-spring-boot-starter` → `executor/`.

```java
@Dag(name = "scoring-flow", inputs = {"input"}, channels = {
        @Channel(name = "score",   reducer = ChannelReducer.SUM_INT),
        @Channel(name = "factors", reducer = ChannelReducer.JOIN_CSV)})
@Component
public class ScoringFlow {

    @DagNode(entry = true, to = {"credit", "income", "collateral"})     // fan out
    public Map<String, Object> intake(DagState state) {
        return Map.of("applicant", state.getString("input"));
    }

    @DagNode(to = {"decide"})
    public Map<String, Object> credit(DagState state) {
        return Map.of("score", 40, "factors", "credit");
    }
    // income(), collateral() … same shape, writing to the same channels

    @DagNode(trigger = "ALL")                                           // join: wait for all three
    public Map<String, Object> decide(DagState state) {
        long total = state.getLong("score");                           // reducer already summed them
        return Map.of("decision", total >= 70 ? "APPROVED" : "REJECTED");
    }
}
```

`@DagNode` also carries `trigger` (`ALL` / `ANY` / `AT_LEAST(n)`), `when=@When(expr, to)` +
`otherwise` for SpEL branching, `subgraph` to nest a whole other DAG, `shard=@Shard(input, output)`
for dynamic fan-out, and `retries` / `onFailure` / `onComplete`. `DagState` exposes typed getters:
`getString` / `getLong` / `getInt` / `getDouble` / `getBoolean` / `getList` (handy for a shard) /
`asMap`. See `cronflow-executor-example` (`ScoringFlow`, `FulfilmentFlow`, `ShardFlow`, `OrderFlow`).

---

## Extension points

### Custom channel reducers  *(scheduler bean + executor annotation)*

`@Channel(reducer = …)` picks a built-in from `ChannelReducer` (`LAST_WINS`, `FIRST_WINS`,
`CONCAT_LIST`, `UNION_SET`, `MERGE_MAP`, `SUM_LONG`, `SUM_INT`, `WRITE_ONCE`, `MAX`, `MIN`, `AND`,
`OR`, `CONCAT_STRING`, `JOIN_CSV`). When none fits, write your own.

A reducer is a `com.chaconneai.openspreader.dag.Reducer<?>` bean **on the scheduler**. The server
collects every such bean and joins them to the built-ins; you then reference it by name from the
channel. cronflow's own extra reducers are the model to copy
(`cronflow-spring-boot-starter` → `server/CronflowReducers.java`).

```java
// On the SCHEDULER application:
import com.chaconneai.openspreader.dag.Reducer;

@Bean
Reducer<?> topScore() {
    return Reducer.named("topScore", (current, incoming) ->
        Math.max(((Number) current).intValue(), ((Number) incoming).intValue()));
}
```

```java
// On the EXECUTOR, reference it by name:
@Channel(name = "score", customReducer = "topScore")
```

`customReducer` overrides `reducer` when set.

### Build a DAG in code — `CronflowDag`  *(executor)*

Prefer to assemble a graph programmatically (e.g. from config)? Expose a `CronflowDag` bean
(`cronflow-executor-spring-boot-starter` → `executor/CronflowDag.java`). The scanner registers it
exactly like an annotated one; the fluent API mirrors the annotations
(`input`, `channel(name, reducer)`, `node(name)` → `bean` / `method` / `subgraph` / `entry` /
`trigger` / `retry` / `to` / `onFailure` / `onComplete` / `when(expr, targets…)` / `otherwise`), plus
`triggeredBy(taskGroup, taskName)` to fire it from a finished `@Task`.

```java
@Bean
CronflowDag billingFlow() {
    return CronflowDag.define("billing-flow")
        .input("orderId")
        .channel("total", ChannelReducer.SUM_INT)
        .node("charge").entry().to("decide")
        .node("decide").trigger("ALL").when("#total > 0", "ship").otherwise("cancel")
        .node("ship")
        .node("cancel")
        .triggeredBy("ops", "nightlyBilling");
}
```

### Route runs across executors  *(scheduler)*

When an app runs several executors, the leader picks one per run. The strategy is a property:

```properties
# ROUND_ROBIN (default) | FIRST | LAST | RANDOM | CONSISTENT_HASH | WEIGHTED
cronsmith.server.dispatch.routing=WEIGHTED
```

`WEIGHTED` reads each executor's `cronsmith.client.weight`; `CONSISTENT_HASH` keeps a task group on
the same executor. This is a **closed enum** — there is no name-based custom-routing plug-in. For
logic beyond the six, replace the dispatcher (`scheduler/TaskDispatcher.java`:
`dispatchAndWait(DispatchRequest)` / `complete(CompleteRequest)`) or the `ExecutorRegistry` bean, both
`@ConditionalOnMissingBean`:

```java
@Bean(name = "cronsmithTaskDispatcher")
TaskDispatcher myDispatcher(/* inject what the default takes */) {
    return new MyTaskDispatcher(/* … */);
}
```

### Store schedules in your own backend  *(scheduler)*

The store is the `TaskManager` SPI (`scheduler/TaskManager.java`): full CRUD plus scheduling state
(`saveTask`, `removeTask`, `getTaskDetail`, `findUpcomingTasksBetween`, `computeNextFiredDateTime`,
`restoreTasks`, `compareAndSetTaskStatus` (must be atomic), `recordExecution`, `recordMisfire`, …).
Built-ins are `InMemoryTaskManager`, `JpaTaskManager`, `JooqTaskManager`, wrapped by
`ClusterTaskManager` / `ShardingTaskManager`. Supply your own by naming the bean:

```java
@Bean(name = "cronsmithStorageTaskManager")
TaskManager myStore(/* … */) { return new MyTaskManager(/* … */); }
```

Two related seams:

- **A new database kind.** The store is auto-detected from the JDBC product name. Teach it a new one
  at startup with `StoreType.register(name, replicated, shared, aliases…)` (`scheduler/StoreType.java`)
  — `replicated` = node-local (leader broadcasts), `shared` = sharding-capable.
- **Serialization.** Schedules are serialized with a codec chosen by
  `cronsmith.server.storage.serialization` (`JDK` default, or `KRYO` if on the classpath).

### Define a task inside the scheduler  *(scheduler)*

Most tasks are `@Task` beans on an executor. But a task can also live entirely in the scheduler (the
`HTTP-API` task is the built-in example) by implementing `Task` directly
(`cronsmith-spring-boot-starter` → `scheduler/Task.java`). The key methods are `execute(String)` and,
as an outcome hook, `handleResult(Object result, Throwable reason)`; identity and limits
(`getCronExpression`, `getTaskId`, `getTimeout`, `getMaxRetryCount`, …) are overridable, most with
defaults.

```java
public class ReindexTask implements Task {
    @Override public CronExpression getCronExpression() {
        return new CronBuilder().everyDay().at(2, 0);
    }
    @Override public Object execute(String initialParameter) {
        // do the work; return a result (logged / passed to handleResult)
        return reindex();
    }
    @Override public void handleResult(Object result, Throwable reason) {
        if (reason != null) { /* alert on failure */ }
    }
}
```

> One instance is cached and reused per `Task` class, so keep the body **thread-safe** (no per-run
> mutable fields). How rows become `Task` objects is itself pluggable via the `TaskFactory` SPI
> (`scheduler/TaskFactory.java`), registered with `TaskReflectionUtils.setTaskFactory(…)` — this is how
> the server swaps in HTTP-dispatch tasks.

### Observe every task — `TaskListener` / `ErrorHandler`  *(scheduler)*

To watch the whole lifecycle cross-cuttingly (audit, metrics, alerting), register a `TaskListener`
bean (`scheduler/TaskListener.java`) — all methods are `default`, so override just what you need:

```java
@Component
class AuditListener implements TaskListener {
    @Override public void onTaskEnded(/* task, result, elapsed … */) { /* record */ }
    @Override public void onTaskMisfired(/* … */) { /* alert */ }
}
```

`ErrorHandler` (`scheduler/ErrorHandler.java`: `onHandleScheduler` / `onHandleTask` /
`onHandleTaskResult`) is the matching seam for error policy; built-ins are `LoggingErrorHandler` and
`DebugErrorHandler`.

### Swap the transport  *(executor)*

The executor talks to the server through an interface, so the wire protocol is replaceable. Implement
`CronsmithServerClient` (`register` / `heartbeat` / `complete`) and/or `CronflowServerClient`
(`register` / `heartbeat`) and define your own bean; the default WebClient impls step aside
(`@ConditionalOnMissingBean`).

```java
@Bean
CronsmithServerClient grpcClient(/* … */) { return new GrpcCronsmithServerClient(/* … */); }
```

### Change how a DAG node is invoked  *(executor)*

The executor resolves a node to a bean method and invokes it with the `DagState`. Replace
`NodeExecutionService` (`cronflow-executor-spring-boot-starter` → `executor/NodeExecutionService.java`:
`NodeRunResult run(NodeRunRequest)`) to change resolution or invocation (e.g. a custom argument
binder). The task side has the same seam in `TaskExecutionService` (default
`DefaultTaskExecutionService`, which is what evaluates the `initialParameter` SpEL).

---

## Overriding auto-configured beans

Every bean below is `@ConditionalOnMissingBean` — define your own of the same type (matching the name
where one is given) to replace it. Toggle a whole starter with its `*.enabled` flag.

**Scheduler — `CronsmithServerAutoConfiguration`**

| Bean (type / name) | Replace to… |
|--------------------|-------------|
| `StoreType cronsmithStoreType` | force / customise store detection |
| `TaskManager cronsmithStorageTaskManager` | use a custom store |
| `ExecutorRegistry cronsmithExecutorRegistry` | change executor tracking / routing |
| `TaskDispatcher cronsmithTaskDispatcher` | custom dispatch |
| `SchedulerLifecycle cronsmithSchedulerLifecycle` | custom start/stop |

**Executor — `CronsmithClientAutoConfiguration`** (`cronsmith.client.enabled`)

| Bean | Replace to… |
|------|-------------|
| `CronsmithServerClient` | custom transport |
| `TaskRegistry` | custom `@Task` discovery |
| `ExecutorIdentity` | custom node id / weight |
| `TaskExecutionService` | custom invocation / arg binding |

**cronflow server — `CronflowServerAutoConfiguration`** (`cronflow.server.enabled`)

| Bean | Replace to… |
|------|-------------|
| `DagStore cronflowDagStore` / `DagRunLog cronflowDagRunLogStore` | custom DAG persistence |
| `DagDefinitionCodec` | custom definition serialization |
| `DagNodeDispatcher` / `EngineDagRunner cronflowDagCoordinator` | custom orchestration |

**cronflow executor — `CronflowClientAutoConfiguration`** (`cronflow.client.enabled`)

| Bean | Replace to… |
|------|-------------|
| `CronflowServerClient` | custom transport |
| `DagScanner` | custom `@Dag` discovery |
| `NodeExecutionService` | custom node invocation |

---

## Examples in the repo

| Where | Shows |
|-------|-------|
| `cronflow-executor-example` → `DemoTasks` | `@Task` across cron / YCRON / interval / ISO, retries, timeout, misfire, repeat, SpEL params |
| `cronflow-executor-example` → `ScoringFlow` | fan-out / join with computing reducers (`SUM_INT`, `MAX`, `JOIN_CSV`) |
| `cronflow-executor-example` → `FulfilmentFlow` | `CONCAT_LIST`, and a DAG reused as a subgraph |
| `cronflow-executor-example` → `ShardFlow` | dynamic fan-out with `@Shard(input, output)` |
| `cronflow-spring-boot-starter` → `CronflowReducers` | the pattern for custom `Reducer` beans |
| `cronsmith-spring-boot-starter` `src/test` → `CustomTaskTests` / `TestTasks` | hand-written `Task` implementations |

For the config keys behind all of this, see [Configuration](configuration.html); for how the pieces
fit together, [Architecture](architecture.html).
