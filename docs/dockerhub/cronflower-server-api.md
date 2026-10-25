# cronflower server (scheduler)

The scheduler node of **cronflower**, a distributed, stateful cron scheduler with an optional DAG
workflow engine. It bundles the cronsmith scheduler and the cronflow add-on. Run three nodes and they
form a self-balancing cluster, no external gateway required.

- Project: https://github.com/paganini2008/cronflower
- Console image: https://hub.docker.com/r/fredfeng033/cronflower-ui
- Multi-arch: linux/amd64, linux/arm64

## What it does

- Owns the schedule and the durable state. A `@Task` becomes a persistent definition, the leader
  claims it when due and calls back over HTTP, so a restart or failover loses nothing.
- Windowed loading keeps only tasks due soon in memory, so a cluster with hundreds of thousands of
  tasks still starts instantly.
- Store is auto-detected from the JDBC url. Node-local H2 is replicated by the leader, a shared
  MySQL or PostgreSQL additionally unlocks group sharding.
- cronflow turns the same cluster into a DAG engine (`@Dag` of `@DagNode` steps).

## Tags

| Tag | Meaning |
|-----|---------|
| `latest` | the newest release |
| `x.y.z` | a specific release, for example `1.0.0` |
| `x.y` | the latest patch of a minor line |

## Run

The recommended way is the three-node compose template (3 schedulers plus console, optional
executor):

https://github.com/paganini2008/cronflower/blob/main/backend/cronflower-docker/docker-compose.yaml

```bash
docker compose up -d
# open http://localhost:7200   (sign in admin / admin123)
```

## Configuration

Everything is passed through `SPRING_APPLICATION_JSON` (or plain Spring env vars). Port is 8080.

| Setting | Purpose |
|---------|---------|
| `spring.spreader.port` / `spring.spreader.ip-addresses` | cluster port (22000) and the peer node addresses |
| `spring.datasource.url` / `username` / `password` | the store. Omit for the bundled per-node H2 |
| `cronflow.scheduler.sharding` | set `true` with a shared database to spread task groups |
| `cronflow.security.jwt.secret` | one long random value, the same on every node |

Default login is `admin` / `admin123`. Change the JWT secret and the demo accounts before any real
deployment. Full reference: https://github.com/paganini2008/cronflower/blob/main/docs/configuration.md
