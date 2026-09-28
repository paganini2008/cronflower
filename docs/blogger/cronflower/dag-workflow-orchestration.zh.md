# cronflower：在集群上跑一张 DAG 工作流，别再手动串 cron 任务了

每个团队最后都会走到这一步。一个 cron 任务 02:00 给订单扣款；另一个 02:15 发货，之所以定在晚 15 分钟，是因为第一个**通常**能在这之前跑完。两者之间没有真正的依赖，没有共享数据，事后也无从判断：第二步跑起来，是因为第一步真的成功了，还是仅仅因为时钟到点了。

这就是纯 cron 撞上的墙：它只能调度单个任务，编排不了一连串任务的流转。

**cronflower** 是一个面向 Spring Boot、自带控制台的开源分布式调度器，它有两面：`cronsmith`，分布式调度器（另有一篇）；以及 `cronflow`，也就是这篇要讲的 DAG 编排器。用 `cronflow`，你把「有哪些步骤、彼此怎么依赖」声明成一张图，同一个集群按图逐个节点地跑，数据在节点间流转，每次运行都留痕。这篇讲怎么用。

## 声明一个工作流

一个 DAG 就是一个 Spring bean。`@Dag` 给它命名；每个 `@DagNode` 方法是一个步骤；`to` 列表就是边。节点之间靠返回一个「命名 channel 写入」的 `Map` 传数据，靠 `DagState` 读上游写了什么：

```java
@Dag(name = "scoring-flow", inputs = {"input"}, channels = {
        @Channel(name = "score",     reducer = ChannelReducer.SUM_INT),
        @Channel(name = "maxWeight", reducer = ChannelReducer.MAX),
        @Channel(name = "factors",   reducer = ChannelReducer.JOIN_CSV)})
@Component
public class ScoringFlow {

    // 入口节点扇出到三个并行打分节点
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

    // join：等全部三个到齐，再读合并后的 channel
    @DagNode(trigger = "ALL")
    public Map<String, Object> decide(DagState state) {
        long total = state.getLong("score");          // 40 + 30 + 20 = 90
        return Map.of("decision", total >= 70 ? "APPROVED" : "REJECTED");
    }
}
```

控制台会照你声明的原样把图画出来，跑之前就能看清它的形状：

![已注册的工作流，右侧是选中图的形状](https://raw.githubusercontent.com/paganini2008/cronflower/main/docs/images/dag-workflows.jpg)

## channel 帮你把管道接好

三个打分节点同时往 `score` 写。手写代码里这是竞态加锁；在这里它是一个**带 reducer 的 channel**。每个 `@Channel` 声明并发写入怎么合并，于是 `decide` 读到的是一个已经合并好的单值（`score` 求和成 90、`maxWeight` 取最大成 40、`factors` 拼成 `"credit,income,collateral"`）。没有共享可变状态，也不用假设谁先谁后。

内置 reducer 覆盖了常见的合并：

| Reducer | 合并方式 |
|---------|----------|
| `SUM_INT` / `SUM_LONG` | 数值相加 |
| `MAX` / `MIN` | 取最大 / 最小 |
| `AND` / `OR` | 布尔折叠 |
| `CONCAT_LIST` / `UNION_SET` | 追加 / 并集 |
| `CONCAT_STRING` / `JOIN_CSV` | 文本拼接 |
| `MERGE_MAP` | 合并 map |
| `LAST_WINS` / `FIRST_WINS` / `WRITE_ONCE` | 只取一个写入者 |

要别的？用 `customReducer` 把 channel 指到你自己的 bean。

## 分支、汇合、嵌套、动态扇出

边不只是直线。

**条件路由。** 一个节点可以用 SpEL 表达式，按数据决定下一步：

```java
@DagNode(when = @When(expr = "#risk > 80", to = "humanReview"), otherwise = {"fulfilment"})
public Map<String, Object> riskScore(DagState state) { /* 写入 risk */ }
```

**汇合模式。** `trigger = "ALL"` 等所有上游边到齐（上面的 `decide`）；`trigger = "ANY"` 只要第一个到就触发，用于「先到先得」的步骤。

**子图。** 一个节点可以是另一整张 DAG，于是你是组合工作流，而不是复制：

```java
@DagNode(subgraph = "fulfilment-flow", to = {"notify"})
public Map<String, Object> fulfilment(DagState state) { /* 跑 fulfilment-flow 这张 DAG */ }
```

**动态扇出。** 宽度到运行时才知道时，`@Shard` 把某个 channel 里的列表拆开，节点按元素各跑一次，再把结果收集进另一个 channel：

```java
@DagNode(shard = @Shard(input = "ids", output = "squares"), to = {"report"})
public Map<String, Object> square(DagState state) { /* 每个 id 跑一次 */ }
```

## 三种触发方式

- **手动**，用控制台的 *Trigger* 按钮，可选传一段 JSON 作为输入 channel 的种子。
- **由一个完成的任务触发。** 在同一个 bean 里放一个 cronsmith `@Task`，它的返回值就是 DAG 的输入，于是一个排期就把流程启动了：

  ```java
  @Task(cron = "0/30 * * * * ?", description = "kick off the scoring-flow DAG")
  public String kickoff() { return "applicant-42"; }
  ```

- **直接按排期触发。** 在 Tasks 模块 *Create Task* 选 *Trigger DAG*，调度器就按 cron 触发这张工作流，一行 kickoff 代码都不用写。

## 看它分布式地跑

一次运行不是黑盒。点进去，你能看到同一张图逐个节点亮起来，旁边的面板告诉你是什么触发的、跑了多久、跑了几个节点：

![一次完成的运行：图 + 触发方式](https://raw.githubusercontent.com/paganini2008/cronflower/main/docs/images/dag-run.jpg)

图的下方，每个节点是一行：它调了什么、成不成功、耗时多少，以及最关键的：**由哪个执行器跑的**。引擎不是在一个进程里跑完整个流程，而是把图**跨整个调度集群**驱动，把每个节点派发给一个存活的执行器，所以一次宽扇出是真的在不同机器上并行跑：

![各节点结果，含每个节点由哪个执行器运行](https://raw.githubusercontent.com/paganini2008/cronflower/main/docs/images/dag-run-nodes.jpg)

这就是相比「手动串 cron」的回报：依赖是真的，数据走类型化的 channel 流转，分支就是分支，而且事后你能精确指出哪个节点在哪跑的、产出了什么。

## 把它跑起来

克隆 [cronflower](https://github.com/paganini2008/cronflower)，一条命令就把整套拉起来：一个调度器、控制台、外加一个执行器，底层用内嵌存储，什么都不用额外准备：

```bash
git clone https://github.com/paganini2008/cronflower
cd cronflower/deploy
./run-local.sh -e 1          # 调度器 + 控制台 + 1 个执行器
```

打开 <http://localhost:7200>，用 `admin` / `admin123` 登录。想变成真正的集群，用 `./run-local.sh -n 3 -e 2`（3 个调度器、2 个执行器），或用 `./run-docker.sh` 跑容器版。自带的执行器已经带了这篇里所有的工作流，所以控制台一打开，它们就在里面等你触发；要加自己的工作流，声明你自己的 `@Dag` bean 即可。
