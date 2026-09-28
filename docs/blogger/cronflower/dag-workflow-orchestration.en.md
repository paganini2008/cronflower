# cronflower: run a DAG workflow across your cluster, instead of chaining cron jobs

Every team ends up here. One cron job at 02:00 charges the orders. Another at 02:15 ships them, set
fifteen minutes later because that's *usually* long enough for the first one to finish. There's no
real dependency between them, no shared data, and no way to see, after the fact, whether step two ran
because step one actually succeeded or just because the clock moved.

That's the wall plain cron hits: it schedules single jobs, it can't orchestrate a flow of them.

**cronflower** is an open-source distributed scheduler for Spring Boot with a web console, and it has
two sides: `cronsmith`, the distributed scheduler (its own post), and `cronflow`, the DAG
orchestrator this post is about. With `cronflow` you declare the steps and how they depend on each
other as a graph, and the same cluster runs the graph, node by node, with the data flowing between
them and every run recorded. This is the usage tour.

## Declare a workflow

A DAG is a Spring bean. `@Dag` names it; each `@DagNode` method is a step; the `to` list is the edges.
Nodes hand data to each other by returning a `Map` of named channel writes, and read what upstream
wrote from the `DagState`:

```java
@Dag(name = "scoring-flow", inputs = {"input"}, channels = {
        @Channel(name = "score",     reducer = ChannelReducer.SUM_INT),
        @Channel(name = "maxWeight", reducer = ChannelReducer.MAX),
        @Channel(name = "factors",   reducer = ChannelReducer.JOIN_CSV)})
@Component
public class ScoringFlow {

    // entry node fans out to three scorers that run in parallel
    @DagNode(entry = true, to = {"credit", "income", "collateral"})
    public Map<String, Object> intake(DagState state) {
        return Map.of("applicant", state.getString("input"));
    }

    @DagNode(to = {"decide"})
    public Map<String, Object> credit(DagState state) {
        return Map.of("score", 40, "maxWeight", 40, "factors", "credit");
    }

    @DagNode(to = {"decide"})
    public Map<String, Object> income(DagState state) {
        return Map.of("score", 30, "maxWeight", 30, "factors", "income");
    }

    @DagNode(to = {"decide"})
    public Map<String, Object> collateral(DagState state) {
        return Map.of("score", 20, "maxWeight", 20, "factors", "collateral");
    }

    // join: wait for ALL three, then read the merged channels
    @DagNode(trigger = "ALL")
    public Map<String, Object> decide(DagState state) {
        long total = state.getLong("score");          // 40 + 30 + 20 = 90
        return Map.of("decision", total >= 70 ? "APPROVED" : "REJECTED");
    }
}
```

The console renders exactly what you declared, so you can see the shape before you ever run it:

![The registered workflows, with the selected graph's shape](https://raw.githubusercontent.com/paganini2008/cronflower/main/docs/images/dag-workflows.jpg)

## Channels do the plumbing

Three scorers write to `score` at the same time. In hand-rolled code that's a race and a lock; here
it's a **channel with a reducer**. Each `@Channel` says how concurrent writes are merged, so `decide`
reads a single, already-combined value (`score` summed to 90, `maxWeight` maxed to 40, `factors`
joined to `"credit,income,collateral"`). No shared mutable state, no ordering assumptions.

The built-in reducers cover the usual merges:

| Reducer | Merge |
|---------|-------|
| `SUM_INT` / `SUM_LONG` | add the numbers |
| `MAX` / `MIN` | keep the largest / smallest |
| `AND` / `OR` | boolean fold |
| `CONCAT_LIST` / `UNION_SET` | append / union collections |
| `CONCAT_STRING` / `JOIN_CSV` | join text |
| `MERGE_MAP` | merge maps |
| `LAST_WINS` / `FIRST_WINS` / `WRITE_ONCE` | pick one writer |

Need something else? Point a channel at your own bean with `customReducer`.

## Branch, join, nest, fan out

The edges aren't just straight lines.

**Conditional routing.** A node can pick its next step from the data with a SpEL expression:

```java
@DagNode(when = @When(expr = "#risk > 80", to = "humanReview"), otherwise = {"fulfilment"})
public Map<String, Object> riskScore(DagState state) { /* writes risk */ }
```

**Join modes.** `trigger = "ALL"` waits for every upstream edge (the `decide` above); `trigger =
"ANY"` fires as soon as the first one arrives, for a first-response-wins step.

**Subgraphs.** A node can be a whole other DAG, so you compose workflows instead of copying them:

```java
@DagNode(subgraph = "fulfilment-flow", to = {"notify"})
public Map<String, Object> fulfilment(DagState state) { /* runs the fulfilment-flow DAG */ }
```

**Dynamic fan-out.** When the width is only known at run time, `@Shard` splits a list from one
channel, runs the node once per item, and collects the results into another:

```java
@DagNode(shard = @Shard(input = "ids", output = "squares"), to = {"report"})
public Map<String, Object> square(DagState state) { /* runs once per id */ }
```

## Three ways to start it

- **By hand**, from the console's *Trigger* button, with an optional JSON seed for the input channels.
- **From a finished task.** Put a cronsmith `@Task` in the same bean; its return value becomes the
  DAG's input, so a schedule kicks off the flow:

  ```java
  @Task(cron = "0/30 * * * * ?", description = "kick off the scoring-flow DAG")
  public String kickoff() { return "applicant-42"; }
  ```

- **On a schedule directly.** In the Tasks module, *Create Task* and choose *Trigger DAG*, and the
  scheduler fires the workflow on a cron with no kickoff code at all.

## Watch it run, distributed

A run is not a black box. Open it and you see the same graph light up node by node, with a panel
telling you what triggered it, how long it took and how many nodes ran:

![A completed run: the graph, and how it was triggered](https://raw.githubusercontent.com/paganini2008/cronflower/main/docs/images/dag-run.jpg)

Below the graph, every node is a row: what it invoked, whether it succeeded, how long it took, and
crucially **which executor ran it**. The engine doesn't run the flow in one process; it drives the
graph across the whole scheduler cluster and dispatches each node to a live executor, so a wide
fan-out actually runs in parallel on different machines:

![Per-node results, including which executor ran each node](https://raw.githubusercontent.com/paganini2008/cronflower/main/docs/images/dag-run-nodes.jpg)

That's the payoff over chained cron jobs: the dependencies are real, the data flows through typed
channels, a branch is a branch, and after the fact you can point at exactly which node ran where and
what it produced.

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
containerised version. The bundled executor already ships the workflows from this post, so they are in
the console ready to trigger the moment it opens; declare your own `@Dag` beans to add workflows of
your own.
