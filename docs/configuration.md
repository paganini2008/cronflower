# Configuration

All settings are plain Spring Boot properties: set them on the `java -jar` line as `--key=value`, in
a properties/YAML file, or via `SPRING_APPLICATION_JSON` for containers. The deploy scripts inject the
per-node ones (port, datasource, cluster peers) for you; everything else has a best-practice default.

## Scheduler (server)

| Key | Default | Notes |
|-----|---------|-------|
| `server.port` | `19090` | REST + console API |
| `cronsmith.server.api-prefix` | `/cronsmith` | Base path for the REST API. Scoped to the cronsmith controllers only, so it never moves `/actuator` (unlike `server.servlet.context-path`). Blank or `/` serves at the root. **If changed, the executor's `server-api-prefix` and the console proxy must match.** |
| `spring.datasource.url` | H2 file (`./data/cronsmith`) | Point at MySQL/PostgreSQL for a **shared** store (auto-detected). Omit entirely for in-memory. |
| `spring.jpa.hibernate.ddl-auto` | `update` | Creates/updates the `cs_*` tables |
| `spring.spreader.name` | `cronsmith-application` | Cluster name — must match cluster-wide |
| `spring.spreader.port` | `22000` | Cluster/leader port — same across the cluster |
| `spring.spreader.ip-addresses` | *(local)* | Peer hosts for a multi-node cluster |
| `cronsmith.server.scheduler.zone` | `UTC` | Fire-time zone — **must** match cluster-wide |
| `cronsmith.server.scheduler.window-minutes` | `5` | Windowed loading horizon |
| `cronsmith.server.scheduler.claim-interval-seconds` | `15` | How often due tasks are claimed |
| `cronsmith.server.scheduler.sharding` | `false` | Group sharding — only effective over a **shared** store |
| `cronsmith.server.dispatch.routing` | `ROUND_ROBIN` | `FIRST`/`LAST`/`ROUND_ROBIN`/`RANDOM`/`CONSISTENT_HASH`/`WEIGHTED` |
| `management.endpoints.web.exposure.include` | `health,info,metrics` | Actuator, for the System Health page |
| `cronsmith.demo.cors-origins` | `*` | CORS origins for the console (applies to the `/cronsmith` API) |
| `management.endpoints.web.cors.allowed-origin-patterns` | `${cronsmith.demo.cors-origins:*}` | **Actuator CORS — separate from the MVC CORS above.** Required for the System Health page to read `/actuator/health` cross-origin (e.g. console at `:7200`, backend/gateway at another origin). Without it `/actuator/health` returns 200 but the browser blocks the response. |
| `management.endpoints.web.cors.allowed-methods` | `GET` | Methods allowed on the actuator CORS above |

The runnable defaults live in `backend/cronsmith-scheduler-example/src/main/resources/application.properties`.
For deploy-time tuning **without a rebuild**, edit `deploy/conf/scheduler.properties` (layered on top).

## Executor (client)

| Key | Default | Notes |
|-----|---------|-------|
| `server.port` | `18080` | The executor's own HTTP port (the deploy scripts override it with a random 50000-60000 port) |
| `cronsmith.client.server-urls` | `http://localhost:19090` | Scheduler URL(s), comma-separated. Defaults to a local scheduler when unset. |
| `cronsmith.client.server-api-prefix` | `/cronsmith` | The scheduler's `api-prefix`, prepended to register/heartbeat/complete calls. **Must match** the scheduler's `cronsmith.server.api-prefix`. |
| `cronsmith.client.base-url` | *(auto)* | The callback URL the scheduler dispatches to — set this when the scheduler reaches the executor on a different host/IP (e.g. containers → host) |
| `cronsmith.client.register-interval-seconds` | `30` | Re-registration / heartbeat interval (the executor example lowers it to `10`) |
| `cronsmith.client.weight` | `1` | Routing weight for `WEIGHTED` dispatch |
| `management.endpoints.web.exposure.include` | `health` | Exposes `/actuator/health` as the liveness probe |

### Two directions — and why nginx only fronts one of them

An executor talks to the cluster **both ways**, and they are configured independently:

- **executor → server** (register / heartbeat / complete) uses **`server-urls`**. It accepts a
  comma-separated list and fails over across it (a dead/5xx node is skipped, see
  `WebClientCronsmithServerClient`). **In production, point it at a single nginx endpoint** that
  fronts the scheduler pool (health-checked upstream + load balancing) instead of listing every node
  — same idea as the console's `apiBaseUrl`. The demo scripts list all nodes only because there is no
  load balancer.

- **server → executor** (the leader **dispatches** a due task to the executor) goes to the address
  the executor advertised at registration — resolved from **`cronsmith.client.base-url`** (or
  `advertise-host` + `advertise-port`), sent as `RegistrationRequest.runUrl`. **This direction does
  NOT go through nginx**: dispatch targets a *specific* executor instance (the one that registered the
  bean), not "any executor", so a load balancer in front of the executors doesn't fit. Each executor
  must therefore advertise an address reachable from **every** scheduler node (any node may become the
  leader) — e.g. its container/pod DNS or host IP. Set `base-url`/`advertise-*` when auto-detection
  can't see a routable address (containers → host, NAT, multiple NICs).

So: **`server-urls` can collapse to one nginx URL; the dispatch address must stay per-instance.**

## `@Task` reference

```java
@Task(cron = "0 0 3 * * ?",              // traditional cron  (or interval= / iso= / parser="ycron")
      parser = "cron",                    // "cron" (default) | "ycron"
      group = "reports", name = "nightly",
      description = "nightly rollup",
      initialParameter = "#{...}",        // constant, or SpEL evaluated on the executor per fire
      timeout = 60000,                    // per-run timeout ms; -1 = none
      maxRetryCount = 2, retryInterval = 1000,
      misfirePolicy = "FIRE_ONCE_NOW")    // FIRE_ONCE_NOW | SKIP | FIRE_ALL
```

Set exactly one of `cron` / `interval`(+`intervalUnit`) / `iso`. A method takes no args or a single
`String` (the `initialParameter`); a non-void return is stored in the execution log. See
`backend/cronsmith-executor-example/.../DemoTasks.java` for a worked example of every attribute.

## Storage matrix

Both scripts default to an embedded **H2 file**, one **independent** file per node (`run-local` at
`data/cronsmith-<n>`, `run-docker` a per-node volume). This is a **node-local replicated** store: the
leader broadcasts every write and each node applies it to its own copy, so a node keeps its data on
failover. Switch to a **shared** DB by uncommenting a datasource block in
`deploy/conf/scheduler.properties` — it takes over. The engine auto-detects the kind from the JDBC
connection:

| Store | How | Across nodes | Sharding |
|-------|-----|--------------|----------|
| H2 file | **default** (per node: `jdbc:h2:file:./data/cronsmith-<n>`) | node-local, kept in sync by leader **broadcast** | no |
| in-memory | set `jdbc:h2:mem:cronsmith` (or no DataSource) | node-local, broadcast | no |
| MySQL / PostgreSQL | uncomment a datasource block in `scheduler.properties` | **shared** (single store, CAS) | **yes** |

## Running behind nginx / KONG

**You don't need a gateway.** By default the web console (`deploy/web-server.mjs`) discovers the whole
scheduler cluster from a single seed and round-robins the API across the nodes with failover, and the
executor's `server-urls` fails over across nodes on its own. A reverse proxy is purely optional — reach
for one only when you want **TLS termination, a single fixed ingress, or NAT traversal**. The golden
rule stays: **load balancing is the scheduler's job; the gateway is transparent transport.**

### Browser → scheduler (the console)

Two equally valid setups:

- **Console self-discovery (default).** Give `web-server.mjs` one seed and let it find the rest:
  `SCHEDULER_URL=http://scheduler-1:8080` (discovery on). No gateway involved.
- **Behind a gateway.** Front the scheduler pool with nginx/KONG (health-checked upstream) and point
  the console at that single endpoint, turning discovery off so it just forwards:
  `SCHEDULER_URL=https://gw.example.com` and `DISCOVERY=off`. The browser still talks to one origin
  (the console), the console talks to one origin (the gateway).

nginx fronting the scheduler pool:

```nginx
upstream cronsmith_schedulers {
  server scheduler-1:8080 max_fails=2 fail_timeout=5s;
  server scheduler-2:8080 max_fails=2 fail_timeout=5s;
  server scheduler-3:8080 max_fails=2 fail_timeout=5s;
}
server {
  listen 443 ssl;
  # ... ssl_certificate ...
  location ~ ^/(cronsmith|actuator)/ {
    proxy_pass http://cronsmith_schedulers;
    proxy_set_header Host $host;
  }
}
```

KONG: create a Service pointing at the pool (or an upstream with the three targets) and a Route on the
`/cronsmith` and `/actuator` path prefixes — same shape, KONG does the health checks and balancing.

Reaching **any** scheduler node is enough: the cluster forwards writes to the leader internally, so the
gateway needs no leader awareness.

### Server → executor (dispatch)

This direction must **not** be pooled behind a shared gateway upstream — the scheduler's own
round-robin already picks the target instance, and dispatch addresses a *specific* executor. If a
gateway sits in front, give each executor a unique, deterministically-routed address via
`cronsmith.client.base-url` (or `advertise-host`/`advertise-port`), e.g. `http://gw/exec-1` → that
one instance. See [Two directions — and why nginx only fronts one of them](#two-directions--and-why-nginx-only-fronts-one-of-them) above for the full rationale.
