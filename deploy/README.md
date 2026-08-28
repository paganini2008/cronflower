# deploy — one-click cronsmith

Two runners that build the backend + console and start the whole stack. Same option surface for both:

| flag | meaning | default |
|------|---------|---------|
| `-n N` | number of **scheduler** (server) nodes | `1` |
| `-e M` | number of **executor** (client) nodes; `0` = start none | `0` |

The store is an embedded **H2 file** by default (zero config; `up` is optional). Each scheduler node
gets its **own independent** H2 file — `run-local` at `deploy/data/cronsmith-<n>`, `run-docker` on a
per-node Docker volume — and the leader **broadcasts every write** so each node keeps its own copy in
sync (node-local replicated store). Both persist across `down`/`up`. For a **shared** cluster on real
infra (MySQL/PostgreSQL), uncomment a datasource block in `conf/scheduler.properties` — it takes over
(CAS instead of broadcast), no flag.

**Linux/macOS**: `run-local.sh` / `run-docker.sh`. **Windows**: the PowerShell mirrors `run-local.ps1` /
`run-docker.ps1` (same flags; if scripts are blocked run `powershell -ExecutionPolicy Bypass -File .\run-local.ps1 -n 2 -e 1`).

On startup each runner **preflights** the environment (JDK 17+, Node 20+, and Docker for the docker
runner) and runs a **capacity guard**: each node gets a `1G` JVM heap by default, and it refuses to
start if the requested heap would exceed 70% of available RAM (host RAM locally, the Docker engine's
RAM for docker) — so `-n 100` stops with an error instead of thrashing your machine.

Both build with the project's **Maven Wrapper** (`backend/mvnw`) — no system Maven needed — and stage
the two runnable jars into `bin/`:

```
bin/cronsmith-scheduler-example-1.0.0-SNAPSHOT.jar
bin/cronsmith-executor-example-1.0.0-SNAPSHOT.jar
```

> `cronsmith` (the engine) and `openspreader` (its cluster library) are not on Maven Central **yet**;
> for now they resolve from your **local** Maven repository. If a `cronsmith` checkout sits next to
> `cronflower`, the scripts `mvn install` it first; otherwise they assume it is already installed.

---

## 1. Local — `run-local.sh` (bare JVM processes, no Docker)

Starts scheduler node(s) first, then the Angular dev server, then — only with `-e` — the executor(s).

```bash
cd deploy
./run-local.sh                # 1 scheduler (H2 file) + frontend   (== `up`)
./run-local.sh -n 2 -e 1      # 2 schedulers, each with its own H2 file, + 1 executor
./run-local.sh logs scheduler-1   # tail a log (scheduler-1 | executor-1 | frontend)
./run-local.sh down           # stop everything it started
```

- Ports: schedulers `19090, 19091, …` · console (ng serve) `7200` · executors **random 50000-60000**
- The dev server proxies `/cronsmith` + `/actuator` to the first scheduler (`:19090`).
- Store: each node has its **own** H2 file `data/cronsmith-<n>` (the leader broadcasts writes so every
  node keeps a copy; persists across restarts); logs in `logs/`, pids in `run/`.
- `down` kills the whole process tree and frees the frontend port (never touches browser tabs).

## 2. Docker — `run-docker.sh` (multi-node, each on its own port)

Generates `docker-compose.generated.yml` for the chosen topology and brings it up. The console
container proxies `/cronsmith` + `/actuator` across **all** scheduler nodes (failing over when one —
even the leader — is down), so the browser only ever talks to `:7200`.

```bash
cd deploy
./run-docker.sh               # 1 scheduler + console on Docker (H2, zero config)
./run-docker.sh -n 3 -e 2     # 3 schedulers + 2 executors + console
./run-docker.sh logs          # all logs (or `logs scheduler-1` for one service)
./run-docker.sh down          # stop + remove containers (keeps the H2 data volumes)
```

- Ports on the host: schedulers `19090…` · console `7200` · executors **random 50000-60000**
- Default H2 is **node-local** (each node has its own store) — fine for a demo. For a real
  shared/sharded cluster, point `conf/scheduler.properties` at a shared MySQL/PostgreSQL reachable
  from the containers (it's mounted into every node); no flag needed.

---

## Advanced configuration (no rebuild)

`conf/scheduler.properties` (scheduler) and `conf/executor.properties` (executors) are each layered on
top of their jar's packaged best-practice defaults — edit them to tune a running deployment WITHOUT
rebuilding. The scheduler file switches the datasource (MySQL/PostgreSQL) or tunes the engine (zone,
windowing, claim interval, sharding, logging, CORS); the executor file tunes the register/heartbeat
interval, weight, timeouts, dispatch callback (`base-url`/`advertise-*`) and actuator. `run-local.sh`
passes each via `--spring.config.additional-location`; `run-docker.sh` mounts them into every
scheduler / executor container. The per-node keys the scripts must own — the scheduler's `server.port`
and cluster peers; the executor's `server.port`, app name, `server-urls` and `server-api-prefix` — are
injected on the command line and OUTRANK these files, so don't pin them here.

**API prefix — one edit, whole chain.** Set `cronsmith.server.api-prefix` (default `/cronsmith`) in
`conf/scheduler.properties` and both runners propagate that one value everywhere it has to match: the
scheduler reads it from the file, and the scripts pass it to the **executor**
(`cronsmith.client.server-api-prefix`), the **console proxy** (`API_PREFIX`, dev + Node) and the
served **`config.json`** (`apiPrefix`). So changing the API namespace is a single-line edit — no need
to touch the executor, the proxy or the frontend by hand. (The executor's *own* `/cronsmith/run` +
`/cronsmith/ping` endpoints are a separate, point-to-point namespace and stay `/cronsmith`.)

## Notes
- `bin/*.jar`, `web-dist/`, `docker-compose.generated.yml`, `logs/`, `run/`, `data/` are build/runtime
  outputs (git-ignored), not source.
- Override anything via env, e.g. `MVN=…`, `M2_REPO=…`, `CRONSMITH_REPO=…`, `WEB_PORT=9000`,
  `SCHED_BASE_PORT=…`, `NG_CONFIG=production`.
- Tune resources via env: `SCHED_XMX_GB` / `EXEC_XMX_GB` (per-node JVM heap in GB, default `1`),
  `MEM_BUDGET_PCT` (capacity cap, default `70`), `EXEC_PORT_LO` / `EXEC_PORT_HI` (executor port range).
