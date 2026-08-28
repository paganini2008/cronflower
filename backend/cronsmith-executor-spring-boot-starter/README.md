# cronsmith-executor-spring-boot-starter

The **executor** side of the cronsmith distributed scheduler. Add it to any Spring Boot
application, annotate a method with `@Task`, and that method becomes a scheduled task: the
cronsmith server owns the schedule and, when the task is due, calls back into your application to
run the method.

This starter is a small, stateless library. It holds no database and no schedule of its own — it
only discovers `@Task` methods, registers them with the server, runs what it is told to run, and
reports the result back. Retry, timeout and logging all live on the server.

```
server (leader)  ──POST /cronsmith/run──▶  your app (@Task method runs)
      ▲                                              │
      └────────POST /cronsmith/executions/complete───┘   (result / error)
```

---

## Requirements

- **JDK 17 or later**
- **Spring Boot 4.1+**, tested against 4.1.1
- A web application — **Spring MVC (servlet) or Spring WebFlux (reactive)**, either works. The
  starter brings neither server; your application supplies one
  (`spring-boot-starter-web` / `-webmvc` or `-webflux`).

## Install

Published to your Maven repository. To build and install from source:

```bash
mvn clean install
```

Then add the dependency:

```xml
<dependency>
    <groupId>com.github.paganini2008</groupId>
    <artifactId>cronsmith-executor-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Auto-configuration switches on by itself; there is nothing to `@Enable`.

## Quick start

**1. Point the executor at your server(s):**

```properties
# application.properties
cronsmith.client.server-urls=http://cronsmith-server:8080
```

**2. Annotate a bean method:**

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

That is the whole integration. On startup the executor discovers the method, registers it with the
server, and the method runs on schedule — but on the *server's* clock, coordinated across the
cluster, not locally.

## Writing a task

`@Task` goes on a Spring bean method that takes **either no argument or a single `String`**. When it
takes a `String`, the task's `initialParameter` is passed in; a non-`void` return value is reported
back and stored in the execution log.

| Attribute | Default | Meaning |
|---|---|---|
| `cron` | *(required)* | Cron expression. Validated by the server, so any dialect it understands works. |
| `group` | application name | Task group. |
| `name` | `beanName.methodName` | Task name, unique within its group. |
| `description` | `""` | Shown in the console. |
| `initialParameter` | `""` | Constant, or a `#{...}` SpEL template (see below). |
| `timeout` | `-1` | Per-run timeout in ms (`-1` = none). Enforced by the server. |
| `maxRetryCount` | `0` | Retries after a failure. Driven by the server. |
| `retryInterval` | `1000` | Base backoff between retries, in ms. |
| `misfirePolicy` | `FIRE_ONCE_NOW` | `FIRE_ONCE_NOW`, `SKIP` or `FIRE_ALL`. |

### `initialParameter`: constant or SpEL

Plain text is passed verbatim. Text containing a `#{...}` template is evaluated **on the executor,
at run time**, so it can read beans or compute a fresh value on every fire:

```java
@Task(cron = "*/30 * * * * ?", initialParameter = "hello")                       // constant
@Task(cron = "*/30 * * * * ?", initialParameter = "#{@clock.nowIso()}")          // a bean call
@Task(cron = "*/30 * * * * ?", initialParameter = "#{T(java.time.LocalDate).now().toString()}")
```

## Configuration

All properties are under `cronsmith.client`:

| Property | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Turn the whole starter off. |
| `server-urls` | *(empty)* | Server base URLs, e.g. `http://host:8080`. One is enough; several are tried in turn. Every write is routed to the leader, so which node you list makes no difference. |
| `application` | `spring.application.name` | This executor's application name. |
| `advertise-host` | local host address | Address peers can dial to reach this executor. |
| `advertise-port` | running web server port | Port peers can dial. |
| `scheme` | `http` | Scheme used to build this executor's URLs. |
| `base-url` | *(auto)* | Full external base URL (through a proxy / rewritten path). Overrides scheme/host/port/path detection. |
| `health-check-url` | *(auto)* | Full external health URL. Overrides all detection. |
| `register-interval-seconds` | `30` | Heartbeat interval. |
| `connect-timeout-millis` | `3000` | Connect timeout for calls back to the server. |
| `read-timeout-millis` | `10000` | Read timeout for calls back to the server. |
| `invoker-pool-size` | `8` | Threads that run task methods. |

### Registration and heartbeat

- **On startup** the executor sends its task list to the server once (a *saveOrUpdate*), retrying
  until a server accepts it. Tasks are reconciled only at (re)start.
- **On an interval** (`register-interval-seconds`) it sends a lightweight heartbeat that carries no
  task list; it only keeps this executor present and reachable in the server's in-memory list, so it
  survives a server restart or a leader change with no coordination.

### Endpoints exposed on the executor

| Endpoint | Purpose |
|---|---|
| `POST /cronsmith/run` | The server dispatches a run here; returns `202` immediately and runs asynchronously. |
| `GET /cronsmith/ping` | Liveness fallback, returns `PONG` — **registered only when Spring Boot Actuator is absent**. |

**Health probe is chosen automatically:** when actuator is on the classpath the executor registers
`/actuator/health` as its liveness URL (honouring `management.endpoints.web.base-path` and a
separate `management.server.port`); otherwise it registers its own `/cronsmith/ping`.

### Context paths and reverse proxies

The registered URLs are fully resolved, honouring `server.servlet.context-path` and
`spring.mvc.servlet.path` (servlet), `spring.webflux.base-path` (reactive), and the actuator paths
above. For anything these do not capture — a reverse proxy, a rewritten path — set `base-url`
and/or `health-check-url` explicitly.

### Running behind a gateway (KONG / nginx / Envoy)

Executors often sit on private addresses the scheduler cannot dial directly, so **both traffic
directions can traverse a gateway**. The guiding principle:

> **Load balancing is the scheduler's own capability — it is never delegated to the gateway.**
> The gateway is a transparent transport (reachability / NAT / TLS / auth), so the whole thing works
> even with a "dumb" gateway that has no round-robin of its own, and you can swap KONG ↔ nginx ↔
> Envoy freely.

Each direction is independent and neither depends on the gateway balancing anything:

**A. executor → scheduler** (`server-urls`, for register / heartbeat / complete)
- Point `server-urls` at the gateway, e.g. `http://kong:7500/cs`, and set `server-api-prefix` to
  match how the scheduler is exposed (with the gateway's `stripPath`): `server-urls=http://kong/cs`
  \+ `server-api-prefix=/cronsmith` → the gateway strips `/cs` and the scheduler sees
  `/cronsmith/register`.
- The gateway only has to reach **any one** scheduler node — the cluster forwards every write to the
  leader internally, so which node it lands on is irrelevant. There is no "which node was chosen"
  semantic here, so letting the gateway pool the scheduler nodes is fine but never *required*; a
  single-node target works too.

**B. scheduler → executor** (the dispatch callback to this executor's advertised `run` URL)
- The **scheduler's own round-robin** picks an instance and POSTs to *that instance's* advertised
  URL. Keep this working through the gateway by giving each executor a **unique, deterministically
  proxied** URL via `base-url` (or `advertise-host` / `advertise-port`):

  ```nginx
  # nginx as a transparent per-instance reverse proxy — NOT a load-balancing upstream pool
  location /exec-1/ { proxy_pass http://10.0.0.11:8080/; }   # → executor-1 (private)
  location /exec-2/ { proxy_pass http://10.0.0.12:8080/; }   # → executor-2 (private)
  ```
  executor-1: `cronsmith.client.base-url=http://gw/exec-1` · executor-2: `.../exec-2`.

  The scheduler picks inst-1 → `http://gw/exec-1/cronsmith/run` → the gateway deterministically
  forwards to executor-1. Round-robin behaves exactly as in a direct-connect deployment; the gateway
  is invisible to it.
- **Do not** put multiple executors into a single gateway upstream pool for this direction. That
  hands load balancing to the gateway and nullifies the scheduler's round-robin (the scheduler
  thinks it targeted inst-1 while the pool may run inst-2). Provenance (`executor_repr`) stays
  correct either way — the executor that actually runs reports itself — but you lose the scheduler's
  routing strategy/weights.
- The routing key must be **stable and per-instance**, so key it on each executor's own
  `advertise-*` / a fixed path prefix, **not** on the scheduler-minted `instanceId` (that UUID is
  ephemeral and awkward to build gateway routes for).

**Liveness is unaffected by private addresses:** presence is maintained by the executor's *push*
heartbeat (direction A, through the gateway), not by the scheduler probing back, so the registry
stays accurate even when the scheduler cannot reach the executor directly.

## Overriding behaviour

Every bean is `@ConditionalOnMissingBean`, so you can replace any part. The two seams are
interfaces:

- `CronsmithServerClient` — how the executor talks to the server (default: `WebClient` over the JDK
  HttpClient connector).
- `TaskExecutionService` — how a dispatch is run (default: reflective invocation on a thread pool).

```java
@Bean
CronsmithServerClient cronsmithServerClient(CronsmithClientProperties props) {
    return new MyServerClient(props);   // e.g. mTLS, a different transport, a test double
}
```

## Notes

- The executor is **stateless**: no datasource, no persistence. Retry, timeout and the execution log
  are the server's responsibility.
- Task bodies are invoked on a shared pool and a bean is reused across runs, so **make task methods
  thread-safe**.
- Business exceptions thrown by your task propagate as the run's error and are reported back to the
  server verbatim.
