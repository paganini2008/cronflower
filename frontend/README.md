# cronflower (frontend)

The **web console** for the cronsmith distributed scheduler + cronflow DAG add-on. Angular 21
(standalone + signals) + RxJS + Angular Material + Tailwind. It is the **one entry point**: it needs
only a seed node and discovers the whole cluster, load-balancing across it with failover: no external
nginx/KONG required.

## Features

- **Full operator UI**: Dashboard, Tasks (list / detail / create-edit), **System** (Executors / Cluster / Health / Settings) and, when the backend has cronflow, **DAG** (Workflows / Runs) with a drag-or-JSON/YAML canvas and a live run monitor.
- **Self-balancing**: handed one or more seeds, it discovers every node (and each node's real port) from `/actuator/health` and round-robins the API across them with failover.
- **One `.env`**: all frontend config lives in a single file, read by the dev proxy, the standalone server, and the build.
- **Two deploy modes**: self-balance the cluster itself (default), or sit behind your own gateway (one switch).
- **Zero-dependency server**: `server.mjs` serves the built SPA and reverse-proxies the API, no nginx needed.

```
┌──────────────┐   /cronsmith /cronflow /actuator   ┌──────── scheduler cluster ────────┐
│  cronflower  │ ─ round-robin + failover ────────▶ │  node · node · node (random ports) │
│  :7200       │   discovers members via            └────────────────────────────────────┘
└──────────────┘   any node's /actuator/health
```

## Requirements

- **Node 20+** (and npm). The backend is a separate process: see the root runners to start the whole stack.

## Quick Start

```bash
cp .env.example .env      # first time; all frontend config lives here
npm install               # first time only
npm start                 # ng serve on http://localhost:7200 (regenerates config.json from .env)
```

Sign in with the demo credentials **admin / admin123**. The dev proxy (`proxy.conf.cjs`) forwards
`/cronsmith` + `/cronflow` + `/actuator` to `CF_SEED_URL` (default `http://localhost:19090`).

To run the **whole stack** (backend + this console), use the root runners:

```bash
cd ../deploy && ./run-local.sh -e 1     # scheduler + this console + an executor
```

Build for production: `npx ng build --configuration production` (outputs `dist/cronflower/browser`).

## Configuration

All frontend config lives in **`.env`** (copy from `.env.example`, git-ignored). `gen-config.mjs`
writes the browser's `public/config.json` from it, `server.mjs` and `proxy.conf.cjs` read it directly.

| `.env` key | What it does |
|------------|--------------|
| `CF_API_BASE_URL` | **the mode switch**: empty = same-origin, the console self-balances the cluster (default), a gateway origin (KONG/nginx) = the browser calls it directly and `server.mjs` serves static only |
| `CF_API_PREFIX` / `CF_CRONFLOW_PREFIX` | REST prefixes, **must match** the backend `cronsmith.server.api-prefix` / `cronflow.server.api-prefix` (default `/cronsmith` / `/cronflow`) |
| `CF_AUTH_USER` / `CF_AUTH_PASS` | the demo login (client-side only, not a secret) |
| `CF_SEED_URL` | one or more seed schedulers (comma-separated) for `server.mjs` + the dev proxy. Blank by default: the launchers inject every node's address |
| `CF_WEB_PORT` / `CF_WEB_ROOT` / `CF_DISCOVERY_INTERVAL_MS` | `server.mjs` only: listen port (7200), built-SPA dir, member-list refresh period |

## How it works

### Standalone server: `server.mjs` (no nginx/KONG)

`npm run serve` starts a zero-dependency Node server that serves the built SPA **and** reverse-proxies
the API across every cluster member with failover. It is handed one or more **seeds** (`CF_SEED_URL`)
and discovers the full member list (and each node's real port) from `/actuator/health`, refreshing on
a timer. This is what the `run-local` / `run-docker` launchers use.

### Optional: put your own gateway in front

Set `CF_API_BASE_URL` to a KONG / nginx origin that fronts the scheduler pool, the browser then calls
it directly while `server.mjs` serves only the static SPA. The backend must allow CORS for the
console's origin, **including actuator CORS** so the System page can read `/actuator/health`.

```nginx
upstream cronsmith { server node-a:8080; server node-b:8080; server node-c:8080; }
server {
  listen 80; server_name console.example.com;
  location /cronsmith/ { proxy_pass http://cronsmith; }
  location /cronflow/  { proxy_pass http://cronsmith; }
  location /actuator/  { proxy_pass http://cronsmith; }
  location /           { root /var/www/cronflower; try_files $uri /index.html; }
}
```

## See also

Root [`README.md`](../README.md) · runner options [`../deploy/README.md`](../deploy/README.md) ·
full configuration reference [`../docs/configuration.md`](../docs/configuration.md).
