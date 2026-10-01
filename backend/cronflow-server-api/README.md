# cronflow-server-api

The runnable **Cronflow server** — the product you deploy. It bundles `cronsmith-spring-boot-starter`
(distributed scheduler) and `cronflow-spring-boot-starter` (DAG engine) behind **login + role-based
authorization**, and ships as `cronflow-server-api-<version>.jar` (what the deploy scripts stage and run).

## Features

- **Scheduler + DAG in one** — the full cronsmith scheduler and the cronflow DAG engine, one process.
- **Stateless JWT auth** — `POST /auth/login` returns a bearer token; any node signs with the same secret, so there is no session to pin.
- **Role-based authorization** — `admin` / `scheduler_admin` / `workflow_admin` / `user`, enforced by URL + method.
- **Multi-database** — store auto-detected from the JDBC connection (H2 / SQLite / MySQL / PostgreSQL / SQL Server / Oracle).
- **Environment profiles** — a common jar + external per-env overrides (dev = embedded H2, prod = shared MySQL).
- **Clustered** — one cluster port per machine; peers listed by host; every node shares the JWT secret.

## Requirements

- **JDK 17+**, **Spring Boot 4.1+**.
- A database only for a shared cluster — none needed for the embedded-H2 dev run.

## Quick Start

```bash
# dev profile (default): embedded H2, demo accounts seeded
java -jar cronflow-server-api-1.0.0-SNAPSHOT.jar
```

Sign in, then call the API with the returned bearer token:

```bash
TOKEN=$(curl -s localhost:19090/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')
curl -s localhost:19090/cronsmith/tasks -H "Authorization: Bearer $TOKEN"
```

Swagger UI: <http://localhost:19090/swagger-ui.html>.

## Login & authorization

Stateless **HMAC-signed JWT**: `POST /auth/login` verifies credentials and returns a token carrying the
user's roles; send it as `Authorization: Bearer <token>` on every call.

| Endpoint | Purpose |
|----------|---------|
| `POST /auth/login` | Sign in `{username,password}` → `{token,tokenType,username,roles,expiresInSeconds}` |
| `GET /auth/me` | Identity encoded in the current token |
| `POST /auth/logout` | No-op (stateless): the client discards its token |

| Role | Can manage |
|------|-----------|
| `admin` | cronsmith + cronflow + system + dashboard (everything) |
| `scheduler_admin` | cronsmith (tasks) + dashboard |
| `workflow_admin` | cronflow (DAG) + dashboard |
| `user` | dashboard only |

Writes under `cronsmith.server.api-prefix` need `admin`/`scheduler_admin`; writes under
`cronflow.server.api-prefix` need `admin`/`workflow_admin`; `/actuator/**` beyond health/info needs
`admin`; reads need any signed-in user. `/actuator/health` stays public (the console discovers the
cluster from it). The **executor → scheduler machine endpoints stay open** (`/executors/register`,
`/executors/heartbeat`, `/executions/complete`, cronflow `/dags/register`, `/dags/heartbeat`).

**Accounts** are provisioned in an XML user store (no self-registration). Edit
`src/main/resources/users.xml`, or point at an external file without rebuilding:

```properties
cronflow.security.users-file=file:/opt/cronflow/conf/users.xml
```

```xml
<users>
  <user username="admin"  password="admin123"        roles="admin"/>
  <user username="ops"    password="{bcrypt}$2a$10$…" roles="scheduler_admin,workflow_admin"/>
  <user username="viewer" password="viewer"           roles="user"/>
</users>
```

`password` is raw (bcrypt-encoded on load) or an already-encoded `{bcrypt}$2a$…`; changes take effect on
restart. `cronflow.security.jwt.secret` MUST be set and identical on every node in production
(the prod profile requires `CRONFLOW_JWT_SECRET`); `cronflow.security.enabled=false` disables auth (dev only).

## Configuration — common jar + external per-env overrides

The jar ships only `application.properties` (common defaults, incl. a dev H2 so a bare `java -jar` runs).
Per-env differences live in `application-{dev,prod}.properties`:

- **Local debug (IDE):** activate the Spring profile, e.g. `-Dspring.profiles.active=prod`.
- **Packaging (deploy):** `mvn -Pdev` / `-Pprod` emits the chosen file as an external
  `conf/server.properties`, layered on at runtime via `--spring.config.additional-location`.

| | dev (default) | prod (`mvn -Pprod`) |
|---|---|---|
| Store | embedded H2 `./data/cronflow` (node-local) | shared **MySQL** (`CRONFLOW_DB_*`), sharding on |
| Logs | `DEBUG` (cronsmith/cronflow) | `INFO` |
| JWT secret | packaged dev default | **required** via `CRONFLOW_JWT_SECRET` (fail fast) |

```bash
mvn -Pprod clean package
export CRONFLOW_DB_URL='jdbc:mysql://HOST:3306/cronflow?...' CRONFLOW_DB_USERNAME=... CRONFLOW_DB_PASSWORD=...
export CRONFLOW_JWT_SECRET='a-long-random-secret-shared-by-every-node'
java -jar target/cronflow-server-api-1.0.0-SNAPSHOT.jar \
  --spring.config.additional-location=file:target/conf/server.properties
```

Store kind is **auto-detected** from the JDBC connection: MySQL/PostgreSQL/Oracle/SQL Server → shared
(sharding-capable); H2/SQLite → node-local.

| Database | Tested version | Driver | Store |
|----------|----------------|--------|-------|
| H2 | 2.4.240 (embedded) | bundled | JPA + jOOQ |
| SQLite | sqlite-jdbc 3.46.1.3 | sqlite-jdbc | jOOQ |
| MySQL | 8.x | mysql-connector-j | JPA + jOOQ |
| PostgreSQL | 14+ | postgresql 42.7.4 | JPA + jOOQ |
| SQL Server | 2022 | mssql-jdbc 12.8.1 | JPA¹ |
| Oracle | Free 23c | ojdbc11 23.7 | JPA¹ |

¹ jOOQ has no open-source dialect for SQL Server / Oracle; use the JPA store there.

**Cluster (multi-node):** one cluster port per machine, list the peer hosts, same JWT secret on every node:

```bash
--spring.spreader.ip-addresses=host-a,host-b,host-c
```

## Examples — REST API

All business endpoints require a bearer token (see roles above).

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/cronsmith/stats` | Dashboard aggregates (task counts by status, executor counts) |
| GET · POST · DELETE | `/cronsmith/tasks` … `/{group}/{name}` | List / detail / create-update / delete tasks |
| GET | `/cronsmith/tasks/{group}/{name}/logs` | Execution history |
| POST | `/cronsmith/tasks/{group}/{name}/pause`\|`resume`\|`cancel` | Task actions |
| GET | `/cronsmith/executors` · `/cronsmith/cluster` | Registered executors · cluster nodes, leader, store |
| GET · POST | `/cronflow/dags` · `/cronflow/runs` · `/cronflow/dags/{graph}/trigger` | DAG definitions, runs, trigger |
| GET | `/actuator/health` | Health (incl. `spreaderCluster`); public |

## See also

Root [`README.md`](../../README.md) · full config reference
[`../../docs/configuration.md`](../../docs/configuration.md).
