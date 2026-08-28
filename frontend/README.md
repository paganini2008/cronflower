# cronflower (frontend)

The web console for the **cronsmith** distributed scheduler. Angular 21 (standalone + signals) +
RxJS + Angular Material + Tailwind. Pages: Dashboard, Tasks (list / detail / create-edit), Executors,
Cluster, System Health.

```
┌──────────────┐    /cronsmith, /actuator (dev proxy)    ┌─────────────────────┐
│  cronflower  │ ───────────────────────────────────────▶│  cronsmith scheduler │
│  :7200 (ng)  │                                          │  :19090 (REST)       │
└──────────────┘                                          └─────────────────────┘
```

The console talks to **one** scheduler endpoint. Any node serves reads locally and routes writes to
the leader, so a single address is enough for development; in production put a reverse proxy (nginx,
or the bundled Node proxy in `deploy/`) in front of the node pool and point the console at it.

## Run just the frontend

```bash
npm install        # first time only
npx ng serve       # http://localhost:7200
```

Sign in with the demo credentials **admin / admin**. `ng serve` proxies the API prefix (default
`/cronsmith`) and `/actuator` to `http://localhost:19090` (see `proxy.conf.cjs`); override the target
with `SCHEDULER_URL` and the prefix with `API_PREFIX` if your scheduler runs elsewhere or uses a
different prefix.

## Runtime config — `public/config.json`

Served alongside the app (from `public/`), so it can be edited on a **deployed build without
recompiling** — just refresh. Same file for local and Docker.

```json
{
  "apiBaseUrl": "",
  "apiPrefix": "/cronsmith",
  "auth": { "username": "admin", "password": "admin" }
}
```

- **`apiBaseUrl`** — backend base URL. Defaults to **`http://localhost:19090`** (a local scheduler)
  when the key is **absent**. Set it to **`""` (empty)** to call the API on the **same origin** as the
  console — for the dev-server proxy or the deployed Node/nginx proxy, which forward the API prefix +
  `/actuator` to the backend (zero config, no CORS); this is what the `run-local` / `run-docker`
  demos use. Set it to any other backend/gateway origin (e.g. `http://localhost:7500/cs` behind KONG)
  when the console is served **without** a proxy (a static host / CDN); the backend must then allow
  CORS — **including actuator CORS** (`management.endpoints.web.cors.*`) so the System Health page can
  read `/actuator/health`.
- **`apiPrefix`** — the backend's REST API prefix; **must match** the scheduler's
  `cronsmith.server.api-prefix` (default `/cronsmith`). All API calls go under it; `/actuator` is
  separate and never prefixed. Change it only if you changed the backend prefix — and then also point
  the proxies at the new value via the `API_PREFIX` env var (see below).
- **`auth`** — the client-side demo login credentials (the server ships without auth; this is not a
  secret — the file is fetched by the browser).

> **Changing the API prefix end-to-end.** The dev-server proxy (`proxy.conf.cjs`) and the deployed
> Node proxy (`deploy/web-server.mjs`) both read the prefix from the **`API_PREFIX`** env var
> (default `/cronsmith`), so set `API_PREFIX` (and `SCHEDULER_URL` for the target) before `ng serve`
> or on the web container. All three — backend `cronsmith.server.api-prefix`, the executor's
> `cronsmith.client.server-api-prefix`, and this `apiPrefix`/`API_PREFIX` — must agree.

### Production: point `apiBaseUrl` at an nginx that fronts the cluster

The dev-server proxy and the bundled Node proxy each target the scheduler nodes directly (the Node
one even fails over across them), which is fine for the demo. **In production, don't let the console
depend on any single node** — put **nginx** (or any load balancer) in front of the scheduler pool
with a health-checked upstream, and set **`apiBaseUrl` to that one nginx endpoint**. nginx then
handles failover (a dead node is dropped and the next is tried) and load-balances reads, so the
console keeps working when any node — even the leader — dies.

```nginx
# nginx in front of the scheduler pool
upstream cronsmith { server node-a:8080; server node-b:8080; server node-c:8080; }  # + health checks

server {
  listen 80;
  server_name console.example.com;
  location /cronsmith/ { proxy_pass http://cronsmith; }
  location /actuator/  { proxy_pass http://cronsmith; }
  location /           { root /var/www/cronflower; try_files $uri /index.html; }   # if serving the SPA here too
}
```

Then, in `public/config.json`:

- serve the console from the **same** nginx → keep `"apiBaseUrl": ""` (same-origin, no CORS); or
- serve the console elsewhere (a static host / CDN) → `"apiBaseUrl": "https://console.example.com"`
  (the scheduler must allow CORS for that origin).

Either way the console talks to **one stable URL** that survives node failure — no per-node proxy
target, no leader dependency.

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
