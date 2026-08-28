# cronsmith · cronflower

> A **distributed, stateful cron scheduler** for the JVM — with a year-aware schedule syntax, an
> auto-detecting multi-database store, cluster sharding & weighted dispatch, and a first-class web
> console. Drop `@Task` on a Spring bean; the cluster owns the schedule and calls you back when it's due.

`cronsmith` is the engine and Spring Boot starters; **cronflower** is the Angular console and this
monorepo that packages everything into a one-click, runnable demo.

---

## Why it stands out

- **Truly distributed & HA** — nodes form a cluster (leader election via *spreader*); the leader
  schedules and dispatches, followers fail over. No single point of failure.
- **Stateful & durable** — schedules and execution history live in a store that is **auto-detected**
  from the JDBC connection (in-memory → H2/SQLite → MySQL/PostgreSQL). Nothing to configure to switch.
- **Scales horizontally** — **group sharding** partitions work across nodes over a shared store;
  **weighted dispatch** sends runs to executors by capacity.
- **YCRON — year-based schedules** — express "the 200th day of the year" or "the first ISO week",
  which no traditional cron field can. Opt in per task; fully isolated from the classic parser.
- **Rich `@Task` model** — cron / YCRON / fixed-interval / ISO-8601 duration, plus retry with
  back-off, per-run timeout, and misfire policy — all declarative.
- **Operator console** — Dashboard, Tasks (create/edit with a live schedule builder), Executors,
  Cluster, and System Health, talking to a single endpoint.

## Tech stack

| Layer | Stack |
|-------|-------|
| Engine | Java 17, an ANTLR 4 cron/YCRON grammar, a timing wheel, `openspreader` clustering |
| Starters | Spring Boot 4.1, Spring MVC, JPA/Hibernate + jOOQ storage tiers, Actuator |
| Stores | H2 · SQLite · MySQL · PostgreSQL (auto-detected) |
| Console | Angular 21 (standalone + signals), RxJS, Angular Material, Tailwind |
| Delivery | Maven Wrapper build · Docker / docker-compose · a zero-dependency Node static+proxy server |

## Architecture

```
   cronflower (Angular)  ──/cronsmith,/actuator──▶  scheduler cluster  ──dispatch──▶  executors
     Dashboard/Tasks/…                              scheduler-1 (leader)             @Task beans
                                                     scheduler-2/3 (followers)        :5xxxx (random)
                                                            │
                                              shared store (H2 · MySQL · PostgreSQL)
```

Full write-up, component responsibilities, and the persistence/serialization model:
[`docs/architecture.md`](docs/architecture.md).

## Repository layout

```
cronflower/
├── backend/                              # Maven reactor (mvnw included — no system Maven needed)
│   ├── cronsmith-spring-boot-starter/        # scheduler (server) starter
│   ├── cronsmith-executor-spring-boot-starter/ # executor (client) starter
│   ├── cronsmith-scheduler-example/          # runnable scheduler — best-practice reference
│   └── cronsmith-executor-example/           # runnable executor — full @Task showcase
├── frontend/                             # the cronflower Angular console
├── deploy/                               # one-click runners (local + docker), Dockerfiles, web server
│   ├── run-local.sh   ·   run-docker.sh
│   ├── conf/scheduler.properties         # externalised advanced config (no rebuild)
│   └── bin/                              # staged runnable jars (build output)
├── docs/                                 # architecture, configuration, screenshots
└── README.md
```

## Quickstart

Prerequisites: **JDK 17+**, **Node 20+** (Angular via `npx`), and — for the Docker path — **Docker**.
The build uses the bundled **Maven Wrapper**, so no system Maven is required.

> `cronsmith` and `openspreader` are not on Maven Central **yet** (they will be). For now they resolve
> from your local Maven repository; if a `cronsmith` checkout sits next to this repo the scripts
> install it automatically. See [`deploy/README.md`](deploy/README.md).

### Local (bare JVM)

```bash
cd deploy
./run-local.sh -e 1        # 1 scheduler (H2 file) + console + 1 executor
```

Open **<http://localhost:7200>** and sign in with **admin / admin**. Stop with `./run-local.sh down`.

### Docker (multi-node)

```bash
cd deploy
./run-docker.sh -n 3 -e 2    # 3 schedulers + 2 executors + console
```

Open **<http://localhost:7200>**. Stop with `./run-docker.sh down`.

Both runners take the same two flags — `-n` scheduler nodes and `-e` executor nodes (`up` is the
default action, so it can be omitted). The store is always **H2 with zero config**; for a real
database, edit `deploy/conf/scheduler.properties` (no flag). Details and env overrides:
[`deploy/README.md`](deploy/README.md).

## Configuration

Best-practice defaults ship in each example; tune the scheduler at deploy time (no rebuild) via
`deploy/conf/scheduler.properties`. Full key reference and the `@Task` cheatsheet:
[`docs/configuration.md`](docs/configuration.md).

**Production HA:** put **nginx** (or any load balancer) in front of the scheduler pool with a
health-checked upstream, and point the console's `apiBaseUrl` (`frontend/public/config.json`) at that
one nginx endpoint — so the UI survives any node failure, not just the data. See
[`frontend/README.md`](frontend/README.md#production-point-apibaseurl-at-an-nginx-that-fronts-the-cluster).

## Screenshots

<!-- Capture from a running console (see docs/images/README.md) or swap these for GCS URLs. -->

| Dashboard | Tasks (cron + YCRON) | Task detail & logs |
|-----------|----------------------|--------------------|
| ![Dashboard](docs/images/dashboard.png) | ![Tasks](docs/images/tasks-list.png) | ![Task detail](docs/images/task-detail.png) |

| Create task (Syntax selector) | Executors | Cluster |
|-------------------------------|-----------|---------|
| ![Task form](docs/images/task-form.png) | ![Executors](docs/images/executors.png) | ![Cluster](docs/images/cluster.png) |

## License

See the `LICENSE` files in the backend modules.
