# deploy — one-click runners

Two runners that **build the backend + console and start the whole stack** with one command, locally
(bare JVM) or on Docker. Same option surface for both.

## Features

- **One command, whole stack** — scheduler node(s) + the Angular console + optional executor(s).
- **Pick the topology** — `-n` scheduler nodes, `-e` executor nodes.
- **Zero-config store** — embedded H2 by default; each node gets its own file and the leader broadcasts writes (node-local replicated). Point at MySQL/PostgreSQL for a shared cluster, no flag.
- **Builds itself** — uses the project's Maven Wrapper (`backend/mvnw`), no system Maven; stages runnable jars into `bin/`.
- **Safe by default** — preflights JDK/Node/Docker and runs a capacity guard (refuses to start if the requested heap would exceed 70% of available RAM).
- **One entry point** — the console (`:7200`) discovers every node and load-balances `/cronsmith` + `/cronflow` + `/actuator` with failover; no external nginx/KONG required.

| flag | meaning | default |
|------|---------|---------|
| `-n N` | number of **scheduler** (server) nodes | `1` |
| `-e M` | number of **executor** (client) nodes; `0` = none | `0` |

## Requirements

- **JDK 17+** and **Node 20+** (preflighted on startup).
- **Docker** only for `run-docker.sh`.
- `cronsmith` (engine) and `openspreader` (cluster lib) are not on Maven Central yet, so they resolve
  from your **local** Maven repository. If a `cronsmith` checkout sits next to `cronflower`, the scripts
  `mvn install` it first; otherwise they assume it is already installed.

## Quick Start

### Local — `run-local.sh` (bare JVM, no Docker)

```bash
cd deploy
./run-local.sh                # 1 scheduler (H2 file) + console   (== `up`)
./run-local.sh -n 2 -e 1      # 2 schedulers, each with its own H2 file, + 1 executor
./run-local.sh logs scheduler-1   # tail a log (scheduler-1 | executor-1 | frontend)
./run-local.sh down           # stop everything it started
```

Open <http://localhost:7200>, sign in **admin / admin123**. The console is the one entry point;
schedulers and executors take **random free ports in 50000-60000** (reach any node through the
console). Logs in `logs/`, pids in `run/`, H2 files in `data/`.

### Docker — `run-docker.sh` (multi-node)

```bash
cd deploy
./run-docker.sh               # 1 scheduler + console on Docker (H2, zero config)
./run-docker.sh -n 3 -e 2     # 3 schedulers + 2 executors + console
./run-docker.sh logs          # all logs (or `logs scheduler-1` for one service)
./run-docker.sh down          # stop + remove containers (keeps the H2 data volumes)
```

Inside the network the console discovers nodes by **container name** (`scheduler-N:8080`), so the
random host ports are for external access only.

## Configuration

Advanced config is **external — no rebuild**. Each file layers on top of its jar's packaged defaults;
`run-local` passes them via `--spring.config.additional-location`, `run-docker` mounts them into every
container.

| File | Tunes |
|------|-------|
| `conf/server.properties` | scheduler: datasource (MySQL/PostgreSQL), engine (zone, windowing, claim interval, sharding), logging, CORS, actuator exposure |
| `conf/executor.properties` | executors: register/heartbeat interval, weight, timeouts, dispatch callback (`base-url` / `advertise-*`) |

- **Shared / sharded cluster:** uncomment a datasource block in `conf/server.properties` (store kind
  is auto-detected; it takes over with CAS instead of broadcast). Or build with `DEPLOY_ENV=prod`
  (`mvn -Pprod`) to emit the MySQL overrides.
- **API prefix — one edit, whole chain:** set `cronsmith.server.api-prefix` (default `/cronsmith`) and
  both runners propagate it to the executor, the console proxy, and the served `config.json`.
- **Login + roles:** the server API needs a bearer token (`/auth/login`, default **admin / admin123**);
  accounts live in the XML user store. Full auth reference:
  [server module README](../backend/cronflow-server-api/README.md).
- **Env overrides:** `MVN=…`, `M2_REPO=…`, `CRONSMITH_REPO=…`, `WEB_PORT=9000`, `NG_CONFIG=production`,
  `DEPLOY_ENV=prod`, `SCHED_XMX_GB` / `EXEC_XMX_GB` (per-node heap, default `1`), `MEM_BUDGET_PCT`
  (capacity cap, default `70`), `EXEC_PORT_LO` / `EXEC_PORT_HI`.

> Per-node keys the scripts own (scheduler `server.port` + cluster peers; executor `server.port`, app
> name, `server-urls`, `server-api-prefix`) are injected on the command line and **outrank** these
> files — don't pin them here.

## Notes & see also

- `bin/*.jar`, `web-dist/`, `docker-compose.generated.yml`, `logs/`, `run/`, `data/` are build/runtime
  outputs (git-ignored), not source.
- Root [`README.md`](../README.md) · full config reference [`../docs/configuration.md`](../docs/configuration.md).
