# cronflow-server-api

The runnable **Cronflow server** (product). It bundles `cronsmith-spring-boot-starter` (the distributed
scheduler) and `cronflow-spring-boot-starter` (the DAG engine) behind **login + role based
authorization**, and is the artifact the deploy scripts stage and run. It ships as
`cronflow-server-api-<version>.jar`.

## Run

```bash
# dev profile (default): embedded H2, demo accounts seeded
java -jar cronflow-server-api-1.0.0-SNAPSHOT.jar
```

Sign in first, then call the API with the returned bearer token:

```bash
TOKEN=$(curl -s localhost:19090/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')
curl -s localhost:19090/cronsmith/tasks -H "Authorization: Bearer $TOKEN"
```

Swagger UI: <http://localhost:19090/swagger-ui.html>.

## Login and authorization

Authentication is a **stateless HMAC signed JWT (bearer token)**: `POST /auth/login` verifies the
credentials and returns a token carrying the user's roles; send it as `Authorization: Bearer <token>`
on every call. Because every node signs with the SAME secret (`cronflow.security.jwt.secret`), any node
behind the round-robin console proxy accepts the token, so there is no session to pin to a node.

| Endpoint | Purpose |
|----------|---------|
| `POST /auth/login` | Sign in `{username,password}` → `{token,tokenType,username,roles,expiresInSeconds}` |
| `GET /auth/me` | Identity encoded in the current token |
| `POST /auth/logout` | No op (stateless): the client discards its token |

### Roles

| Role | Can manage |
|------|-----------|
| `admin` | cronsmith + cronflow + system + dashboard (everything) |
| `scheduler_admin` | cronsmith (tasks) + dashboard |
| `workflow_admin` | cronflow (DAG) + dashboard |
| `user` | dashboard only |

Enforced by URL + method: writes (POST/PUT/DELETE) under `cronsmith.server.api-prefix` need
`admin`/`scheduler_admin`; writes under `cronflow.server.api-prefix` need `admin`/`workflow_admin`;
`/actuator/**` beyond health/info needs `admin`; reads (dashboard, lists, run detail) need any signed in
user. `/actuator/health` stays public (the console load balancer discovers the cluster from it, and K8s
probes read it).

The **executor → scheduler machine endpoints stay open** (`/executors/register`, `/executors/heartbeat`,
`/executions/complete`, and cronflow `/dags/register`, `/dags/heartbeat`), because executors are
independent clients driven by the cronsmith/cronflow executor starters. Securing them with a shared
bearer is an extension point both ends would agree on.

### Accounts — where to add / change users

Cronflower has **no self-registration**: accounts are provisioned in an **XML user store**. Edit
**`src/main/resources/users.xml`** (packaged as `classpath:users.xml`, the dev default), or point at an
external file without rebuilding:

```properties
cronflow.security.users-file=file:/opt/cronflow/conf/users.xml
```

```xml
<!-- users.xml -->
<users>
  <user username="admin"    password="admin123"        roles="admin"/>
  <user username="ops"      password="{bcrypt}$2a$10$…" roles="scheduler_admin,workflow_admin"/>
  <user username="viewer"   password="viewer"           roles="user"/>
</users>
```

`password` is a raw value (bcrypt-encoded on load) or an already-encoded `{bcrypt}$2a$…` value
(recommended for production); `roles` is comma-separated from `admin` / `scheduler_admin` /
`workflow_admin` / `user`. Changes take effect on restart.

`cronflow.security.enabled=false` disables auth entirely (dev only). `cronflow.security.jwt.secret`
MUST be set and identical on every node in production (the prod profile requires `CRONFLOW_JWT_SECRET`).

## Config: common jar + external per-environment overrides

The **jar ships only `application.properties`** (common, environment-agnostic defaults; it includes a
dev-friendly H2 so a bare `java -jar` runs). The dev/prod differences live in
`src/main/resources/application-{dev,prod}.properties`, which hold **only what changes** per
environment. Two independent mechanisms use them:

- **Local debug (IDE):** the profile files stay in `src/main/resources` (they are excluded from the
  jar) and load when you activate the matching Spring profile, e.g. `-Dspring.profiles.active=prod`.
- **Packaging (deploy):** `mvn -Pdev` (default) / `mvn -Pprod` emits the chosen
  `application-<env>.properties` as an external `conf/server.properties`, which the deploy scripts layer
  on top of the jar at runtime (`--spring.config.additional-location=file:conf/server.properties`). No
  Spring profile is required at runtime; `server.properties` carries the environment's overrides.

| | dev (default) | prod (`mvn -Pprod`) |
|---|---|---|
| Store | embedded H2 file `./data/cronflow` (node-local) | shared **MySQL** (`CRONFLOW_DB_*` env), sharding on |
| Schema | `ddl-auto=update` | `ddl-auto=update` (switch to `validate` + migrations to harden) |
| Logs | `DEBUG` for cronsmith/cronflow | `INFO` |
| JWT secret | packaged dev default | **required** via `CRONFLOW_JWT_SECRET` (fail fast) |

```bash
# build a prod artifact (emits conf/server.properties with the MySQL overrides)
mvn -Pprod clean package
# run it against MySQL
export CRONFLOW_DB_URL='jdbc:mysql://HOST:3306/cronflow?...' CRONFLOW_DB_USERNAME=... CRONFLOW_DB_PASSWORD=...
export CRONFLOW_JWT_SECRET='a-long-random-secret-shared-by-every-node'
java -jar target/cronflow-server-api-1.0.0-SNAPSHOT.jar \
  --spring.config.additional-location=file:target/conf/server.properties
```

To use PostgreSQL instead, override `spring.datasource.url`/`driver-class-name` (the drivers are on the
path). Store kind is **auto-detected** from the JDBC connection: MySQL/PostgreSQL/Oracle/SQL Server →
shared (sharding-capable); H2/SQLite → node-local.

### Supported & tested databases

The **JPA** store works on every one; the **jOOQ** store runs on the four open-source jOOQ dialects
(SQL Server and Oracle are jOOQ-commercial-only, so those use the JPA store).

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

One cluster port per machine; list the peer hosts. On a single host, run several instances (one grabs
the cluster port and is the leader; the rest are followers). Every node must share the same JWT secret.

```bash
--spring.spreader.ip-addresses=host-a,host-b,host-c
```

## REST API (consumed by the Cronflow console)

All business endpoints require a bearer token (see roles above).

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
| GET | `/cronsmith/cluster` | Cluster nodes, leader, sharding, detected **StoreType** + DB metadata |
| GET | `/cronflow/dags` · `/cronflow/runs` · `/cronflow/runs/{id}` | DAG definitions and runs |
| POST | `/cronflow/dags` · `/cronflow/dags/{graph}/trigger` | Author / trigger a DAG |
| GET | `/actuator/health` | Health (includes a `spreaderCluster` component); public |
