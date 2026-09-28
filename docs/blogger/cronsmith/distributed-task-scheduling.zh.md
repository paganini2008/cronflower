# cronflower：把 Spring Boot 变成一个分布式定时任务集群

`@Scheduled` 好用，直到不好用为止。它只在一个 JVM 里跑，所以你一扩容到两个实例，任务就触发两次。它没有重试、没有超时、没有运行记录；机器要是在凌晨 2 点重启，那个夜间汇总就悄无声息地没跑。到最后你只能自己拼上 Quartz、一个数据库、一张锁表，外加一个手写的看板。

**cronflower** 就是这一整套，开源的：一个面向 Spring Boot、自带控制台的分布式有状态调度器，自己组建集群，不依赖任何外部数据库、消息队列或协调服务。它做两件事，这篇讲其中一件：分布式**任务**调度（另有一篇，同一个产品，讲它的 DAG 工作流）。你给方法加个注解，把 cronflower 跟它一起跑起来，就得到一个带控制台的、集群化、可持久化、会重试的调度器，而这些管道代码一行都不用写。这篇讲怎么用。

## 两个角色：调度器和执行器

进程分两类：

- **调度器（scheduler）** 掌管排期。它把任务状态存进一个 store，判断每个任务何时到点，然后发起调用。跑多个就组成一个带 leader 的集群。
- **执行器（executor）** 就是你的应用。它用 `@Task` 声明任务，调度器说到点了就跑对应的代码。

业务代码在执行器里；调度器是跟它一起部署的基础设施。

## 声明一个任务

在执行器里，任务就是一个加了 `@Task` 的 bean 方法。启动时被自动发现并注册到调度器，之后排期就归调度器管：

```java
@Component
public class DemoTasks {

    // 传统 cron，每 5 秒一次；参数以 String 传入。
    @Task(cron = "*/5 * * * * ?", group = "showcase", name = "sayHello", initialParameter = "world")
    public String sayHello(String who) {
        return "hello, " + who + " @ " + LocalDateTime.now();
    }

    // 固定间隔，不用写 cron。
    @Task(interval = 10, intervalUnit = TimeUnit.SECONDS, group = "showcase", name = "every10s")
    public void every10s() { /* ... */ }

    // cron 跨不了的间隔用 ISO-8601 时长（每 90 分钟）。
    @Task(iso = "PT1H30M", group = "showcase", name = "every90m")
    public void every90m() { /* ... */ }
}
```

参数可有可无。方法可以不带参，也可以带一个 `String`（任务的 `initialParameter`）。这个参数可以是常量，也可以是一段在执行器上、每次触发时求值的 SpEL 模板，所以像「今天的日期」这种值每次触发都是新的：

```java
@Task(cron = "0 0 * * * ?", group = "showcase", name = "heartbeatTick",
      initialParameter = "#{T(java.time.LocalDate).now().toString()}")
public void heartbeatTick(String today) { /* today 每小时重新算一次 */ }
```

想要月制语法说不出的排期，比如「一年第 200 天的中午」？把 parser 切成年制的 YCRON：

```java
@Task(cron = "0 0 12 ? ? 200", parser = "ycron", group = "showcase", name = "dayOfYear200")
public void dayOfYear200() { /* ... */ }
```

每个注册的任务都会出现在控制台里，带着它的排期、运行次数和下次触发时间：

![任务列表，每个 @Task 一行](https://raw.githubusercontent.com/paganini2008/cronflower/main/docs/images/tasks-list.jpg)

## 可靠性是配出来的，不是写出来的

在生产里跑任务那些麻烦事，在这里都是注解属性，而且由**调度器**统一执行，所以每个执行器行为一致。

**带退避的重试。** 设 `maxRetryCount` 和 `retryInterval`；失败会重试，且每次尝试都记进日志：

```java
@Task(cron = "*/20 * * * * ?", group = "reliability", name = "flaky",
      maxRetryCount = 2, retryInterval = 1000)
public String flaky(String parameter) {
    // 前两次失败，第三次成功
}
```

在执行历史里，这一次触发是三行：尝试 `#0` 失败、`#1` 失败、`#2` 成功。同一个视图还告诉你**是哪个调度节点派发的、哪个执行器跑的**，这正是集群的意义：

![执行历史：重试尝试 + 每次由哪个节点运行](https://raw.githubusercontent.com/paganini2008/cronflower/main/docs/images/execution-history.jpg)

**单次超时。** `timeout = 2000` 会把超过 2 秒的这次运行标记为超时，而不是任由它挂着：

```java
@Task(cron = "0 */5 * * * ?", group = "reliability", name = "slowJob", timeout = 2000)
public void slowJob() throws InterruptedException { Thread.sleep(5000); }
```

**misfire 策略。** 如果某个触发时刻到点时调度器正好宕着，恢复后怎么办由你定：`SKIP` 跳过、`FIRE_ONCE_NOW`（默认）、或 `FIRE_ALL` 把错过的都补上。

```java
@Task(cron = "0 0 2 * * ?", group = "reliability", name = "nightlyRollup", misfirePolicy = "SKIP")
public void nightlyRollup() { /* 每天 02:00；错过的直接跳过，不补跑 */ }
```

**有限次运行。** `repeatCount` 让任务触发固定次数后自行结束；`stopAt` 给它一个截止时刻。一个跑 10 次就退休的预热任务：

```java
@Task(interval = 30, intervalUnit = TimeUnit.SECONDS, group = "ops", name = "warmup", repeatCount = 10)
public void warmup() { /* 跑 10 次后自行结束 */ }
```

注解就这些。完整属性表，备查：

| 属性                          | 作用                                             |
|-------------------------------|--------------------------------------------------|
| `cron` / `parser`             | cron 排期（或加 `parser = "ycron"` 用 YCRON）    |
| `interval` + `intervalUnit`   | 固定间隔，比如每 10 秒                            |
| `iso`                         | 以 ISO-8601 时长表示的固定间隔，比如 `PT1H30M`   |
| `initialParameter`            | 常量，或每次触发求值的 SpEL 模板                 |
| `group` / `name` / `description` | 标识，以及控制台显示的说明                     |
| `maxRetryCount` / `retryInterval` | 失败重试，带退避                             |
| `timeout`                     | 超时判定（毫秒）                                 |
| `misfirePolicy`               | `FIRE_ONCE_NOW`（默认）/ `FIRE_ALL` / `SKIP`     |
| `repeatCount`                 | 结束前的总触发次数（默认无限）                   |
| `stopAt`                      | 一个 ISO 时刻，过后停止触发                      |

## 有些任务根本不需要执行器

有些活儿就是「按点调一个 URL」，压根不值得写个 bean。建一个 **HTTP-API 任务**，调度节点自己发起调用，不涉及任何执行器。这类不用写 `@Task`，而是在控制台的 *New task* 表单或 REST API 里创建，这也是运维不重新部署就能加/改任意任务的方式：

```
POST /cronsmith/tasks
{ "taskGroup": "ops", "taskName": "pingHealth", "taskType": "HTTP",
  "url": "https://example.com/health", "httpMethod": "GET",
  "cron": "0 */5 * * * ?" }
```

所以任务分两种：**bean 任务**，派发给承载代码的执行器；**HTTP-API 任务**，在调度器自己身上跑。两者共享上面所有的排期、重试、超时和历史。

## 它是集群，不是一台 cron 机器

跑多个调度器，它们通过 gossip 选出 leader。任务状态存在 store 里，所以重启或故障转移都不丢：存活节点继续触发，恢复的节点重新加入并重载。没有 cron 会触发两次，也没有会漏。

控制台一眼看清集群：有哪些节点、谁是 leader、后端 store 是什么、调度模式如何。

![集群视图：三个调度节点，一个 leader 两个 standby，共享一份复制存储](https://raw.githubusercontent.com/paganini2008/cronflower/main/docs/images/cluster.jpg)

store 会从你的数据源自动识别。没有数据库就是内存；指到 H2、SQLite、MySQL 或 PostgreSQL 就持久化在那里。**节点本地**的 store（每节点一份 H2/SQLite）由 leader 保持同步；**共享**数据库（MySQL/PostgreSQL）还额外解锁**分组分片（group sharding）**，让每个节点只触发哈希到自己的那些任务组，从而分摊负载：

```properties
# 让共享库集群从 leader-only 变成分片
cronsmith.server.scheduler.sharding=true
```

在节点本地 store 上它会安全地退回 leader-only，所以这个开关一直开着也没问题。

每个执行器都会注册并发心跳，所以调度器总是派发给一个存活的执行器：

![已注册的执行器，存活且带权重](https://raw.githubusercontent.com/paganini2008/cronflower/main/docs/images/executors.jpg)

当一个应用跑多个执行器时，leader 每次运行挑一个。默认轮询（round-robin）；用 `cronsmith.server.dispatch.routing` 可切成 `WEIGHTED`（按各执行器的 `weight`）、`CONSISTENT_HASH`（黏性，同一任务组总落到同一执行器）、`RANDOM`、`FIRST` 或 `LAST`。

顺带说下它怎么扛量：leader 不会把整份排期都放内存里。它只把接下来几分钟内到点的任务加载进时间轮，其余留在 store 里、到点了再认领，所以几十万任务的集群依然秒起。用 `cronsmith.server.scheduler.window-minutes` 调这个视野窗口。触发时间在 `cronsmith.server.scheduler.zone`（默认 UTC）里计算，且必须全集群一致。

## 运行、暂停、查看

运维需要的一切都挂在任务上，走 REST API 或控制台都行：立即运行一次、暂停、恢复、取消，以及每次运行的完整执行历史（入参、结果、耗时）。暂停的任务保留状态、停止触发，直到你恢复；取消的任务停止但保留历史。健康检查和 Prometheus 抓取端点（`/actuator/health`、`/actuator/prometheus`）随 starter 自带，集群可直接接入你现有的监控。

## 把它跑起来

克隆 [cronflower](https://github.com/paganini2008/cronflower)，一条命令就把整套拉起来：一个调度器、控制台、外加一个执行器，底层用内嵌存储，什么都不用额外准备：

```bash
git clone https://github.com/paganini2008/cronflower
cd cronflower/deploy
./run-local.sh -e 1          # 调度器 + 控制台 + 1 个执行器
```

打开 <http://localhost:7200>，用 `admin` / `admin123` 登录。想变成真正的集群，用 `./run-local.sh -n 3 -e 2`（3 个调度器、2 个执行器），或用 `./run-docker.sh` 跑容器版。自带的执行器已经带了这篇里所有的 `@Task` 方法，所以控制台一打开，你看到的这些就都是活的；要加自己的任务，声明你自己的 `@Task` bean 即可。
