// Standalone cronflower console server: serves the built Angular SPA and reverse-proxies the API to
// the scheduler cluster, load-balancing across every member with failover — so the console is started
// independently of the backend and needs only ONE seed scheduler address.
//
// It self-discovers the cluster from the seed's /actuator/health: the `spreaderCluster` component lists
// this node and every other member, each carrying its real HTTP port in metadata. The pool is rebuilt
// on a timer, so nodes joining or leaving are picked up automatically; if the seed is down, any member
// already in the pool bootstraps the next refresh. Writes are routed to the leader by the scheduler, so
// any member may answer and which one does is irrelevant.
//
// Zero dependencies. All configuration comes from .env (process.env overrides), the SAME .env the
// console's config.json is generated from — so there is one place to configure the frontend.
//
//   CF_WEB_PORT              listen port (default 7200)
//   CF_WEB_ROOT              built SPA dir (default dist/cronflower/browser)
//   CF_SEED_URL              seed scheduler URL, e.g. http://localhost:19090 (a fixed, known address)
//   CF_API_PREFIX            cronsmith API prefix (default /cronsmith)
//   CF_CRONFLOW_PREFIX       cronflow (DAG) API prefix (default /cronflow)
//   CF_DISCOVERY_INTERVAL_MS member-list refresh period (default 10000; 0 disables, uses the seed only)
import { createServer, request as httpRequest } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { readFileSync, existsSync } from 'node:fs';
import { extname, join, normalize, resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));

// --- .env (the one place the frontend is configured) ---------------------------------------------
function loadEnv() {
  const path = resolve(here, '.env');
  const out = {};
  if (existsSync(path)) {
    for (const raw of readFileSync(path, 'utf8').split('\n')) {
      const line = raw.trim();
      if (!line || line.startsWith('#')) continue;
      const eq = line.indexOf('=');
      if (eq === -1) continue;
      let val = line.slice(eq + 1).trim();
      if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
        val = val.slice(1, -1);
      }
      out[line.slice(0, eq).trim()] = val;
    }
  }
  return { ...out, ...process.env };
}
const env = loadEnv();
const pick = (k, d) => (env[k] !== undefined && env[k] !== '' ? env[k] : d);

const PORT = Number(pick('CF_WEB_PORT', 7200));
const ROOT = resolve(here, pick('CF_WEB_ROOT', 'dist/cronflower/browser'));
// One or more seed schedulers (comma-separated). There is nothing special about a seed beyond being a
// reachable entry point — the launcher hands every node's address here, and the first one that answers
// /actuator/health yields the full member list. No fixed port, and no baked-in default: a self-balancing
// run must be told where the cluster is (the launchers do this; a manual run sets CF_SEED_URL).
const SEEDS = (pick('CF_SEED_URL', '') || '')
  .split(',').map((s) => s.trim()).filter(Boolean).map((s) => new URL(s));
// The switch: an external API base (a KONG/Nginx gateway the browser reaches directly, same value the
// console's config.json carries) turns OFF this server's own discovery + proxy — it then only serves
// the static SPA, and the browser talks to that gateway. Empty is the default: self-discover + balance
// the cluster here, so no external proxy is needed at all.
const API_BASE = pick('CF_API_BASE_URL', '');
const EXTERNAL = API_BASE !== '';
const prefix = (v, d) => { let p = (v || d).trim(); if (!p.startsWith('/')) p = '/' + p; return p.replace(/\/+$/, '') || d; };
const API_PREFIX = prefix(env.CF_API_PREFIX, '/cronsmith');
const CRONFLOW_PREFIX = prefix(env.CF_CRONFLOW_PREFIX, '/cronflow');
const PROXY_PREFIXES = [API_PREFIX, CRONFLOW_PREFIX, '/actuator'];
const DISCOVERY_INTERVAL_MS = Number(pick('CF_DISCOVERY_INTERVAL_MS', 10000));

// The live pool of scheduler origins the proxy round-robins over, kept fresh by discovery. Seeded with
// the seed URL and never allowed to go empty (the last good pool survives a total outage).
let pool = [...SEEDS];
let rr = 0;

const MIME = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8', '.map': 'application/json',
  '.ico': 'image/x-icon', '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg',
  '.gif': 'image/gif', '.svg': 'image/svg+xml', '.webp': 'image/webp',
  '.woff': 'font/woff', '.woff2': 'font/woff2', '.ttf': 'font/ttf',
  '.txt': 'text/plain; charset=utf-8', '.webmanifest': 'application/manifest+json',
};
const isProxied = (p) => PROXY_PREFIXES.some((pre) => p === pre || p.startsWith(pre + '/'));

// --- Cluster discovery via /actuator/health ------------------------------------------------------
function getJson(base, path) {
  return new Promise((res, rej) => {
    const req = httpRequest(
      { protocol: base.protocol, hostname: base.hostname, port: base.port || 80, path, method: 'GET',
        headers: { host: base.host, accept: 'application/json' }, timeout: 3000 },
      (r) => {
        if ((r.statusCode || 500) >= 400) { r.resume(); return rej(new Error('HTTP ' + r.statusCode)); }
        const c = [];
        r.on('data', (x) => c.push(x));
        r.on('end', () => { try { res(JSON.parse(Buffer.concat(c).toString('utf8'))); } catch (e) { rej(e); } });
      },
    );
    req.on('error', rej);
    req.on('timeout', () => req.destroy(new Error('timeout')));
    req.end();
  });
}

/** A member's HTTP origin = the host it advertises + its real HTTP port. A web member always publishes
 *  its actual bound port as metadata.server.port (openspreader's WebAddressMetadataListener), so read
 *  it straight; a member without it isn't a reachable web node — skip it rather than guess a port. */
function memberUrl(address, metadata) {
  const host = address && address.split(':')[0];
  const port = metadata && metadata['server.port'];
  if (!host || !port) return null;
  const scheme = (SEEDS[0] && SEEDS[0].protocol) || 'http:';
  try { return new URL(`${scheme}//${host}:${port}`); } catch { return null; }
}

/** Ask any known node for the full member list and rebuild the pool. The list is complete on every
 *  node, so one reachable member is enough; if none answers, keep the last good pool. */
async function discover() {
  for (const node of [...pool, ...SEEDS]) {
    try {
      const h = await getJson(node, '/actuator/health');
      const d = h && h.components && h.components.spreaderCluster && h.components.spreaderCluster.details;
      if (!d) continue;
      const byOrigin = new Map();
      const add = (u) => { if (u) byOrigin.set(u.origin, u); };
      add(memberUrl(d.address, d.metadata)); // self
      for (const m of d.otherMembers || []) add(memberUrl(m.address, m.metadata));
      if (byOrigin.size) {
        const next = [...byOrigin.values()];
        const changed = next.map((u) => u.origin).sort().join(',') !== pool.map((u) => u.origin).sort().join(',');
        pool = next;
        if (changed) console.log(`[discovery] members (${pool.length}): ${pool.map((u) => u.origin).join(', ')}`);
      }
      return;
    } catch { /* try the next candidate */ }
  }
  console.warn(`[discovery] no member reachable; keeping pool (${pool.length}): ${pool.map((u) => u.origin).join(', ')}`);
}

// --- Reverse proxy (round-robin + failover) ------------------------------------------------------
function proxy(req, res) {
  const chunks = [];
  req.on('data', (c) => chunks.push(c));
  req.on('end', () => {
    const body = Buffer.concat(chunks);
    const start = pool.length ? rr++ % pool.length : 0;
    const attempt = (k) => {
      if (k >= pool.length) { res.writeHead(502, { 'content-type': 'text/plain' }); return res.end('bad gateway: no scheduler reachable'); }
      const t = pool[(start + k) % pool.length];
      const headers = { ...req.headers, host: t.host };
      delete headers['transfer-encoding'];
      headers['content-length'] = body.length;
      const up = httpRequest(
        { protocol: t.protocol, hostname: t.hostname, port: t.port || 80, method: req.method, path: req.url, headers },
        (u) => { res.writeHead(u.statusCode || 502, u.headers); u.pipe(res); },
      );
      up.on('error', () => attempt(k + 1)); // member down -> next one
      if (body.length) up.write(body);
      up.end();
    };
    attempt(0);
  });
}

async function serveStatic(req, res, pathname) {
  const rel = normalize(decodeURIComponent(pathname)).replace(/^(\.\.(\/|\\|$))+/, '');
  let filePath = join(ROOT, rel);
  try {
    let st = await stat(filePath);
    if (st.isDirectory()) filePath = join(filePath, 'index.html');
    const data = await readFile(filePath);
    res.writeHead(200, { 'content-type': MIME[extname(filePath).toLowerCase()] || 'application/octet-stream' });
    res.end(data);
  } catch {
    try { // SPA fallback: unknown paths return index.html for Angular's router.
      const idx = await readFile(join(ROOT, 'index.html'));
      res.writeHead(200, { 'content-type': 'text/html; charset=utf-8' });
      res.end(idx);
    } catch {
      res.writeHead(404, { 'content-type': 'text/plain' });
      res.end('not found');
    }
  }
}

createServer((req, res) => {
  const pathname = new URL(req.url, 'http://localhost').pathname;
  // In external-gateway mode the browser calls the gateway directly, so we never proxy here.
  if (!EXTERNAL && isProxied(pathname)) return proxy(req, res);
  return serveStatic(req, res, pathname);
}).listen(PORT, () => {
  console.log(`cronflower console on :${PORT} — static ${ROOT}`);
  if (EXTERNAL) {
    console.log(`  API via external gateway ${API_BASE} (this server's discovery + proxy are off)`);
    return;
  }
  if (!SEEDS.length) {
    console.warn('  no CF_SEED_URL set — nothing to proxy to. Set it to any cluster node (the launchers do this).');
    return;
  }
  console.log(`  proxy ${PROXY_PREFIXES.join(', ')}  ·  seeds ${SEEDS.map((s) => s.origin).join(', ')}  ·  discovery via /actuator/health ${DISCOVERY_INTERVAL_MS > 0 ? `every ${DISCOVERY_INTERVAL_MS}ms` : 'off'}`);
  if (DISCOVERY_INTERVAL_MS > 0) { discover(); setInterval(discover, DISCOVERY_INTERVAL_MS); }
});
