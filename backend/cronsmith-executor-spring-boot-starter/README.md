# cronsmith-executor-spring-boot-starter

The **executor** side of the cronsmith distributed scheduler. Add it to any Spring Boot application,
annotate a method with `@Task`, and that method becomes a scheduled task: the server owns the schedule
and, when the task is due, calls back into your app to run the method.

## Features

- **One annotation**: `@Task` on a bean method, discovered and registered on startup, no `@Enable`.
- **Stateless**: no database, no local schedule, retry, timeout and logging all live on the server.
- **Server-driven schedule**: tasks run on the *server's* clock, coordinated across the cluster, not locally.
- **Flexible parameters**: a task takes nothing or a single `String`, `initialParameter` can be a constant or a `#{...}` SpEL template evaluated fresh on each fire.
- **Servlet or reactive**: works on Spring MVC or WebFlux (your app supplies the web server).
- **Replaceable seams**: every bean is `@ConditionalOnMissingBean`, swap the transport or the invoker.

```
server (leader)  ──POST /cronsmith/run──▶  your app (@Task method runs)
      ▲                                              │
      └────────POST /cronsmith/executions/complete───┘   (result / error)
```

## Requirements

- **JDK 17+**, **Spring Boot 4.1+** (tested against 4.1.1).
- A web application: **Spring MVC (servlet) or WebFlux (reactive)**, the starter brings neither server.

## Quick Start

```xml
<dependency>
    <groupId>com.github.paganini2008</groupId>
    <artifactId>cronsmith-executor-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

```properties
# application.properties — point the executor at your server(s)
cronsmith.client.server-urls=http://cronsmith-server:8080
```

```java
@Component
public class ReportTasks {

    @Task(cron = "0 0 3 * * ?", name = "daily-report", initialParameter = "sales")
    public String dailyReport(String kind) {
        // ... do the work ...
        return "report for " + kind + " generated";   // returned value is stored in the server log
    }
}
```

That is the whole integration: on startup the executor discovers the method, registers it, and the
method runs on schedule, coordinated across the cluster.

## Examples: writing a task

`@Task` goes on a bean method that takes **no argument or a single `String`**. A non-`void` return is
reported back and stored in the execution log.

| Attribute | Default | Meaning |
|-----------|---------|---------|
| `cron` | *(required)* | Cron expression (validated by the server, any dialect it understands) |
| `group` | application name | Task group |
| `name` | `beanName.methodName` | Task name, unique within its group |
| `description` | `""` | Shown in the console |
| `initialParameter` | `""` | Constant, or a `#{...}` SpEL template |
| `timeout` | `-1` | Per-run timeout in ms (`-1` = none), enforced by the server |
| `maxRetryCount` / `retryInterval` | `0` / `1000` | Retries after a failure, base backoff in ms |
| `misfirePolicy` | `FIRE_ONCE_NOW` | `FIRE_ONCE_NOW`, `SKIP` or `FIRE_ALL` |

`initialParameter` with a `#{...}` template is evaluated **on the executor, at run time**, so it can
read beans or compute a fresh value on every fire:

```java
@Task(cron = "*/30 * * * * ?", initialParameter = "hello")                                 // constant
@Task(cron = "*/30 * * * * ?", initialParameter = "#{@clock.nowIso()}")                    // a bean call
@Task(cron = "*/30 * * * * ?", initialParameter = "#{T(java.time.LocalDate).now().toString()}")
```

## Configuration

All properties are under `cronsmith.client`:

| Property | Default | Meaning |
|----------|---------|---------|
| `enabled` | `true` | Turn the whole starter off |
| `server-urls` | *(empty)* | Server base URL(s), one is enough, several are tried in turn (writes route to the leader) |
| `application` | `spring.application.name` | This executor's application name |
| `advertise-host` / `advertise-port` / `scheme` | auto | Address/port/scheme peers dial to reach this executor |
| `base-url` / `health-check-url` | auto | Full external URLs (through a proxy / rewritten path), override detection |
| `register-interval-seconds` | `30` | Heartbeat interval |
| `connect-timeout-millis` / `read-timeout-millis` | `3000` / `10000` | Timeouts for calls back to the server |
| `invoker-pool-size` | `8` | Threads that run task methods |

**Lifecycle:** on startup it sends its task list once (a *saveOrUpdate*, retrying until accepted), on
`register-interval-seconds` it sends a lightweight heartbeat that keeps it present and reachable, so it
survives a server restart or leader change with no coordination.

**Endpoints exposed on the executor:** `POST /cronsmith/run` (the server dispatches a run here, returns
`202` and runs async) and `GET /cronsmith/ping` (liveness fallback, **only when Actuator is absent** :
otherwise `/actuator/health` is registered as the liveness URL). Registered URLs honour
`server.servlet.context-path`, `spring.mvc.servlet.path`, `spring.webflux.base-path` and the actuator
paths, for a reverse proxy or rewritten path, set `base-url` / `health-check-url` explicitly.

### Behind a gateway (KONG / nginx / Envoy)

> **Load balancing is the scheduler's own capability: never delegated to the gateway.** The gateway is
> transparent transport (reachability / NAT / TLS), so KONG ↔ nginx ↔ Envoy swap freely.

- **executor → scheduler** (`server-urls`): point at the gateway and set `server-api-prefix` to match
  how the scheduler is exposed, the gateway only has to reach **any one** node (writes forward to the leader).
- **scheduler → executor** (dispatch callback): give each executor a **unique, deterministically proxied**
  URL via `base-url` / `advertise-*` so the scheduler's round-robin still targets a specific instance.
  Do **not** pool multiple executors into one upstream for this direction (it nullifies the scheduler's routing).

## Extending

Every bean is `@ConditionalOnMissingBean`, the two seams are interfaces:

```java
@Bean
CronsmithServerClient cronsmithServerClient(CronsmithClientProperties props) {
    return new MyServerClient(props);   // custom transport: mTLS, a different protocol, a test double
}
```

- `CronsmithServerClient`: how the executor talks to the server (default: WebClient over the JDK HttpClient).
- `TaskExecutionService`: how a dispatch is run (default: reflective invocation on a thread pool).

## Notes & see also

- The executor is **stateless**, task bodies run on a shared pool and a bean is reused, so **make task
  methods thread-safe**. Business exceptions propagate as the run's error, reported back verbatim.
- Root [`README.md`](../../README.md) · full config reference [`../../docs/configuration.md`](../../docs/configuration.md).
