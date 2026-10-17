# cronflower console (web UI)

The web console for **cronflower**, a distributed, stateful cron scheduler with an optional DAG
workflow engine. It is a zero-dependency Node static server plus reverse proxy, so no nginx is
needed. Hand it one or more scheduler nodes and it discovers the whole cluster and load-balances the
API across it with failover.

- Project: https://github.com/paganini2008/cronflower
- Scheduler image: https://hub.docker.com/r/fredfeng033/cronflower-server-api
- Multi-arch: linux/amd64, linux/arm64

## What it shows

- Dashboard, Tasks (list, detail, create or edit), System (Executors, Cluster, Health, Settings),
  and, when the backend has cronflow, DAG (Workflows and Runs) with a drag or JSON canvas and a live
  run monitor.
- Self-balancing. Given one seed it finds every node from `/actuator/health` and round-robins the
  API with failover.

## Tags

| Tag | Meaning |
|-----|---------|
| `latest` | the newest release |
| `x.y.z` | a specific release, for example `1.0.0` |
| `x.y` | the latest patch of a minor line |

## Run

Use the three-node compose template, which wires the console to the cluster for you:

https://github.com/paganini2008/cronflower/blob/main/backend/cronflower-docker/docker-compose.yaml

```bash
docker compose up -d
# open http://localhost:7200   (sign in admin / admin123)
```

## Configuration

The container listens on port 80. Map it to a host port, for example `7200:80`.

| Env var | Purpose |
|---------|---------|
| `SCHEDULER_URL` | comma separated scheduler addresses to seed discovery, for example `http://cronflower-api-1:8080,http://cronflower-api-2:8080` |
| `API_PREFIX` | REST prefix, must match the backend. Default `/cronsmith` |
| `CRONFLOW_PREFIX` | DAG REST prefix, must match the backend. Default `/cronflow` |
| `DISCOVERY` | set `off` to pin to the seeds only. Default `on` |
| `PORT` / `WEB_ROOT` | listen port (80) and the built SPA directory |

Default login is `admin` / `admin123`. Full reference:
https://github.com/paganini2008/cronflower/blob/main/docs/configuration.md
