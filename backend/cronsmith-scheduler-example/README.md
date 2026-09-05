# cronsmith-scheduler-example

A runnable, best-practice **scheduler** built on `cronsmith-spring-boot-starter` — the server side of
the cronsmith distributed scheduler. This is the reference the deploy scripts stage and run; it ships
as `cronsmith-scheduler-example-<version>.jar`.

## Run

```bash
java -jar cronsmith-scheduler-example-1.0.0-SNAPSHOT.jar
```

Open <http://localhost:19090/cronsmith/tasks>.

## Default configuration (`application.properties`)

| Key | Default | Notes |
|-----|---------|-------|
| `server.port` | **19090** | HTTP + REST API port |
| `spring.datasource.url` | `jdbc:h2:file:./data/cronsmith;...` | Persistent embedded H2 (zero external infra, survives restarts). Store is **auto-detected** from the JDBC connection — no `mode`/`replicated`/`shared` to set. |
| `spring.jpa.hibernate.ddl-auto` | `update` | Creates/updates the `cs_*` tables |
| `spring.spreader.name` | `cronsmith-application` | Cluster name (must match cluster-wide) |
| `spring.spreader.port` | `22000` | Cluster/leader port (same across the cluster) |
| `cronsmith.server.scheduler.zone` | `UTC` | Fire-time zone — MUST match cluster-wide |
| `cronsmith.server.scheduler.window-minutes` | `5` | Windowed loading: only tasks due within this window are held in memory |
| `cronsmith.server.scheduler.claim-interval-seconds` | `15` | How often due tasks are claimed |
| `cronsmith.server.scheduler.sharding` | `false` | Group sharding — effective only over a **shared** store (MySQL/PostgreSQL) |
| `management.endpoints.web.exposure.include` | `health,info,metrics` | Actuator, for the System Health page + executor liveness |
| `cronsmith.demo.cors-origins` | `*` | CORS origins for the cronflower frontend |

## Storage: auto-detected, zero config

- **No DataSource** → in-memory (node-local).
- **A DataSource** → the store kind is read from `Connection.getMetaData().getDatabaseProductName()`:
  MySQL / PostgreSQL / Oracle / SQL Server → **shared** (sharding-capable); H2 / SQLite → node-local.
- To use a shared database (and enable sharding across nodes), point the datasource at it, e.g.:

```bash
java -jar cronsmith-scheduler-example-1.0.0-SNAPSHOT.jar \
  --spring.datasource.url=jdbc:mysql://HOST:3306/DB \
  --spring.datasource.username=USER --spring.datasource.password=PASS \
  --cronsmith.server.scheduler.sharding=true
```

### Supported & tested databases

All six are regression-tested. The **JPA** store works on every one; the **jOOQ** store runs on the
four open-source jOOQ dialects — SQL Server and Oracle are jOOQ-commercial-only, so on those two the
store is JPA/Hibernate (which generates the schema itself, so the bundled `db/*.sql` are reference DDL).

| Database   | Tested server version        | JDBC driver          | Store tested   |
|------------|------------------------------|----------------------|----------------|
| H2         | 2.4.240 (embedded)           | bundled              | JPA + jOOQ     |
| SQLite     | via sqlite-jdbc 3.46.1.3     | sqlite-jdbc 3.46.1.3 | jOOQ           |
| MySQL      | 8.x                          | mysql-connector-j    | JPA + jOOQ     |
| PostgreSQL | 14+                          | postgresql 42.7.4    | JPA + jOOQ     |
| SQL Server | 2022 (`mssql/server:2022`)   | mssql-jdbc 12.8.1    | JPA¹           |
| Oracle     | Free 23c (`gvenzl/oracle-free:23`) | ojdbc11 23.7.0.25.01 | JPA¹     |

¹ jOOQ has no open-source dialect for SQL Server / Oracle; use the JPA store there.

## Cluster (multi-node)

One fixed port per machine; list the peer hosts. On a single host, run several instances (one grabs
the cluster port and is the leader; the rest are followers on ephemeral ports):

```bash
--spring.spreader.ip-addresses=host-a,host-b,host-c
```

## REST API (consumed by the cronflower frontend)

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/cronsmith/stats` | Dashboard aggregates (task counts by status, executor counts) |
| GET | `/cronsmith/tasks` | List tasks (filters: `group,name,taskClass,status,limit,offset`) |
| GET | `/cronsmith/tasks/{group}/{name}` | Task detail |
| POST | `/cronsmith/tasks` | Create / update a task |
| DELETE | `/cronsmith/tasks/{group}/{name}` | Delete a task |
| GET | `/cronsmith/tasks/{group}/{name}/logs` | Execution history |
| POST | `/cronsmith/tasks/{group}/{name}/pause`\|`resume`\|`cancel` | Task actions |
| GET | `/cronsmith/executors` | Registered executors (+ liveness) |
| GET | `/cronsmith/cluster` | Cluster nodes, leader, sharding, and the detected **StoreType** + DB metadata |
| GET | `/actuator/health` | Health (includes a `spreaderCluster` component) |
