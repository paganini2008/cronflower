# cronflower (frontend)

The web console for the **cronsmith** distributed scheduler + **cronflow** DAG add-on. Angular 21
(standalone + signals) + RxJS + Angular Material + Tailwind. Pages: Dashboard, Tasks (list / detail /
create-edit), **System** (Executors / Cluster / Health, tabbed) and — when the backend has cronflow —
**DAG** (Workflows / Runs, tabbed) with a drag-or-JSON/YAML canvas to author DAGs and a live run monitor.

```
┌──────────────┐   /cronsmith /cronflow /actuator   ┌──────────── scheduler cluster ────────────┐
│  cronflower  │ ─ round-robin + failover ────────▶ │  node · node · node  (random ports)       │
│  :7200       │   discovers members via            └───────────────────────────────────────────┘
└──────────────┘   any node's /actuator/health
```

The console is the **one entry point** (`:7200`). It needs only a **seed** — any one cluster node — and
discovers the full member list (and each node's real port) from `/actuator/health`, then load-balances
across every node with failover. So there is **no fixed backend port and no external nginx/KONG needed**
(you can still slot one in — see the switch below).

## Run just the frontend

```bash
cp .env.example .env      # first time; adjust if needed (all frontend config lives here)
npm install               # first time only
npm start                 # ng serve on http://localhost:7200 (also regenerates config.json from .env)
```

Sign in with the demo credentials **admin / admin123**. Everything is configured from **one `.env`**
(see below); `ng serve`'s proxy (`proxy.conf.cjs`) forwards `/cronsmith` + `/cronflow` + `/actuator`
to `CF_SEED_URL` (default `http://localhost:19090` for a plain local scheduler).

## Configuration — one `.env`

All frontend config lives in **`.env`** (copy from `.env.example`; `.env` is git-ignored). It is read
by three things, so there is one place to change: `scripts/gen-config.mjs` writes the browser's
`public/config.json` from it (run automatically by `npm start` / `build` / `serve`), and both
`server.mjs` (the standalone server) and `proxy.conf.cjs` (the dev proxy) read it directly.

| `.env` key | what it does |
|---|---|
| `CF_API_BASE_URL` | **the mode switch** — empty = same-origin, the console self-balances the cluster (default); a gateway origin (KONG/nginx) = the browser calls it directly and `server.mjs` serves static only |
| `CF_API_PREFIX` / `CF_CRONFLOW_PREFIX` | REST prefixes; **must match** the backend `cronsmith.server.api-prefix` / `cronflow.server.api-prefix` (default `/cronsmith` / `/cronflow`) |
| `CF_AUTH_USER` / `CF_AUTH_PASS` | the demo login (client-side only; not a secret) |
| `CF_SEED_URL` | one or more seed schedulers (comma-separated) for `server.mjs` + the dev proxy. Blank by default — the launchers inject every node's address; set it only for a manual run |
| `CF_WEB_PORT` / `CF_WEB_ROOT` / `CF_DISCOVERY_INTERVAL_MS` | `server.mjs` only: listen port (7200), built-SPA dir, member-list refresh period |

`gen-config.mjs` produces `public/config.json`, which the browser fetches at runtime (editable on a
deployed build without recompiling): `{ apiBaseUrl, apiPrefix, cronflowPrefix, auth }`. Same file for
local and Docker.

## Standalone server — `server.mjs` (no nginx/KONG)

`npm run serve` starts a zero-dependency Node server that serves the built SPA **and** reverse-proxies
the API to the cluster, load-balancing across every member with failover. It is handed one or more
**seeds** (`CF_SEED_URL`) — any reachable node — and discovers the full member list, and each node's
real port, from `/actuator/health`, refreshing on a timer so nodes joining/leaving are picked up. This
is what the `run-local` / `run-docker` launchers use; **no external load balancer is required.**

### Optional: put your own gateway in front

The switch is `CF_API_BASE_URL`: set it to a KONG / nginx origin that fronts the scheduler pool, and
the browser calls that directly while `server.mjs` serves only the static SPA (its own discovery +
proxy switch off). The backend must then allow CORS for the console's origin — **including actuator
CORS** (`management.endpoints.web.cors.*`) so the System page can read `/actuator/health`.

```nginx
# nginx in front of the scheduler pool (only if you set CF_API_BASE_URL to it)
upstream cronsmith { server node-a:8080; server node-b:8080; server node-c:8080; }  # + health checks
server {
  listen 80; server_name console.example.com;
  location /cronsmith/ { proxy_pass http://cronsmith; }
  location /cronflow/  { proxy_pass http://cronsmith; }
  location /actuator/  { proxy_pass http://cronsmith; }
  location /           { root /var/www/cronflower; try_files $uri /index.html; }
}
```

## Run the whole stack (backend + this console)

Use the one-click runners at the repo root — they build the backend, start the scheduler, and serve
this console:

```bash
cd ../deploy
./run-local.sh -e 1     # local: scheduler + this console (ng serve) + an executor
# or
./run-docker.sh         # docker: scheduler + console container
```

See the root [`README.md`](../README.md) and [`../deploy/README.md`](../deploy/README.md) for options
(`-n` nodes, `-e` executors), and [`../docs/configuration.md`](../docs/configuration.md) for the full
configuration reference.

## Build

```bash
npx ng build --configuration production   # outputs to dist/cronflower/browser
```
