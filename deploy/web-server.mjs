// The cronflower web console server — a zero-dependency Node static server + reverse proxy.
//
// Replaces nginx/KONG: it serves the built Angular SPA (with client-side-routing fallback to
// index.html) and proxies the two API prefixes to the scheduler cluster, so the browser only ever
// talks to one origin (no CORS) and you need NO external load balancer.
//
// It self-discovers the cluster: point it at ONE seed scheduler and it polls that node's
// `<API_PREFIX>/cluster` endpoint, learns every member, and load-balances (round-robin) across them
// with failover — following nodes joining/leaving. Writes are routed to the leader by the scheduler
// itself, so any node may answer and which one does is irrelevant.
//
// Config (env):
//   PORT                  listen port (default 80)
//   WEB_ROOT              static dir (default /usr/share/web)
//   SCHEDULER_URL         one or more seed scheduler URLs, comma-separated
//                         (default http://scheduler-1:8080). Any live one bootstraps discovery.
//   API_PREFIX            backend cronsmith.server.api-prefix (default /cronsmith)
//   SCHED_HTTP_PORT       FALLBACK HTTP port, used only for nodes whose roster entry has no advertised
//                         httpPort (older backend). Normally each node advertises its real HTTP port
//                         via metadata, so discovery works even with different/random ports per node.
//                         Default: the seed's port.
//   DISCOVERY             'off' to disable auto-discovery and use SCHEDULER_URL verbatim (default on)
//   DISCOVERY_INTERVAL_MS refresh period (default 10000)
import { createServer, request as httpRequest } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { extname, join, normalize } from 'node:path';

const PORT = Number(process.env.PORT || 80);
const ROOT = process.env.WEB_ROOT || '/usr/share/web';

const SEEDS = (process.env.SCHEDULER_URL || 'http://scheduler-1:8080')
  .split(',').map((s) => s.trim()).filter(Boolean).map((s) => new URL(s));

const API_PREFIX = (() => {
  let p = (process.env.API_PREFIX || '/cronsmith').trim();
  if (!p.startsWith('/')) p = '/' + p;
  return p.replace(/\/+$/, '') || '/cronsmith';
})();
const PROXY_PREFIXES = [API_PREFIX, '/actuator'];

// The HTTP port every scheduler is assumed to listen on (see SCHED_HTTP_PORT note above).
const HTTP_PORT = String(process.env.SCHED_HTTP_PORT || SEEDS[0].port || 80);
const DISCOVERY = (process.env.DISCOVERY || 'on').toLowerCase() !== 'off';
const DISCOVERY_INTERVAL_MS = Number(process.env.DISCOVERY_INTERVAL_MS || 10000);

// The live pool of scheduler URLs the proxy round-robins over. Seeded with SCHEDULER_URL, then kept
// fresh by discovery. Never allowed to go empty (discovery keeps the last good pool on total failure).
let pool = [...SEEDS];
let rr = 0;

const MIME = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8', '.map': 'application/json',
  '.ico': 'image/x-icon', '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg',
  '.gif': 'image/gif', '.svg': 'image/svg+xml', '.webp': 'image/webp',
  '.woff': 'font/woff', '.woff2': 'font/woff2', '.ttf': 'font/ttf', '.eot': 'application/vnd.ms-fontobject',
  '.txt': 'text/plain; charset=utf-8', '.webmanifest': 'application/manifest+json',
};

const isProxied = (p) => PROXY_PREFIXES.some((pre) => p === pre || p.startsWith(pre + '/'));

// --- Cluster discovery ---------------------------------------------------------------------------

/** GET a small JSON document from a base URL, with a short timeout. Resolves parsed JSON or rejects. */
function getJson(base, path) {
  return new Promise((resolve, reject) => {
    const req = httpRequest(
      { protocol: base.protocol, hostname: base.hostname, port: base.port || 80, path, method: 'GET',
        headers: { host: base.host, accept: 'application/json' }, timeout: 3000 },
      (res) => {
        if ((res.statusCode || 500) >= 400) { res.resume(); return reject(new Error('HTTP ' + res.statusCode)); }
        const chunks = [];
        res.on('data', (c) => chunks.push(c));
        res.on('end', () => { try { resolve(JSON.parse(Buffer.concat(chunks).toString('utf8'))); } catch (e) { reject(e); } });
      },
    );
    req.on('error', reject);
    req.on('timeout', () => req.destroy(new Error('timeout')));
    req.end();
  });
}

/**
 * Ask any currently-known node for the cluster roster and rebuild the pool as
 * {seed URLs} ∪ {scheme://<member host>:HTTP_PORT}. De-duplicated by origin, so explicit seeds (with
 * their real ports) always win over the assumed-port form of the same node. Keeps the last good pool
 * if every node is unreachable.
 */
async function discover() {
  const candidates = [...pool, ...SEEDS];
  for (const node of candidates) {
    try {
      const c = await getJson(node, API_PREFIX + '/cluster');
      // The roster is authoritative: build the pool from it alone (seeds are only for bootstrap and
      // as a fallback when the roster is empty), so a node reached two ways — e.g. the "localhost"
      // seed and its advertised host — doesn't get double weight.
      const byOrigin = new Map();
      for (const n of c.nodes || []) {
        if (!n.host) continue;
        // Each node advertises its real HTTP port (n.httpPort, from openspreader node metadata); this
        // is what makes discovery work even when nodes use different/random HTTP ports. Fall back to
        // the assumed HTTP_PORT only when an older backend doesn't advertise it.
        const port = n.httpPort || HTTP_PORT;
        const u = new URL(`${SEEDS[0].protocol}//${n.host}:${port}`);
        byOrigin.set(u.origin, u);
      }
      if (byOrigin.size) {
        const next = [...byOrigin.values()];
        const changed = next.map((u) => u.origin).sort().join(',') !== pool.map((u) => u.origin).sort().join(',');
        pool = next;
        if (changed) console.log(`[discovery] pool (${pool.length}): ${pool.map((u) => u.origin).join(', ')}`);
      }
      return;
    } catch { /* try the next candidate */ }
  }
  console.warn(`[discovery] no node reachable; keeping pool (${pool.length}): ${pool.map((u) => u.origin).join(', ')}`);
}

// --- Reverse proxy -------------------------------------------------------------------------------

function proxy(req, res) {
  // Buffer the (small, JSON) body so the call can be retried against another node on failure. Start
  // at a rotating offset (round-robin) and fall through the pool on connection errors.
  const chunks = [];
  req.on('data', (c) => chunks.push(c));
  req.on('end', () => {
    const body = Buffer.concat(chunks);
    const start = pool.length ? rr++ % pool.length : 0;
    const attempt = (k) => {
      if (k >= pool.length) {
        res.writeHead(502, { 'content-type': 'text/plain' });
        res.end('bad gateway: no scheduler reachable');
        return;
      }
      const t = pool[(start + k) % pool.length];
      const headers = { ...req.headers, host: t.host };
      delete headers['transfer-encoding'];
      headers['content-length'] = body.length;
      const upstream = httpRequest(
        { protocol: t.protocol, hostname: t.hostname, port: t.port || 80, method: req.method, path: req.url, headers },
        (up) => { res.writeHead(up.statusCode || 502, up.headers); up.pipe(res); },
      );
      upstream.on('error', () => attempt(k + 1)); // node down/unreachable -> try the next one
      if (body.length) upstream.write(body);
      upstream.end();
    };
    attempt(0);
  });
}

async function serveStatic(req, res, pathname) {
  // Block path traversal, then map the URL onto the static root.
  const rel = normalize(decodeURIComponent(pathname)).replace(/^(\.\.(\/|\\|$))+/, '');
  let filePath = join(ROOT, rel);
  try {
    let st = await stat(filePath);
    if (st.isDirectory()) filePath = join(filePath, 'index.html');
    const data = await readFile(filePath);
    res.writeHead(200, { 'content-type': MIME[extname(filePath).toLowerCase()] || 'application/octet-stream' });
    res.end(data);
  } catch {
    // SPA fallback: unknown paths return index.html so Angular's router can handle them.
    try {
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
  if (isProxied(pathname)) return proxy(req, res);
  return serveStatic(req, res, pathname);
}).listen(PORT, () => {
  console.log(`cronflower web server on :${PORT} — static ${ROOT}, proxy ${PROXY_PREFIXES.join(', ')}`);
  console.log(`  seeds: ${SEEDS.map((s) => s.origin).join(', ')}  ·  discovery ${DISCOVERY ? `on (every ${DISCOVERY_INTERVAL_MS}ms, http port ${HTTP_PORT})` : 'off'}`);
  if (DISCOVERY) { discover(); setInterval(discover, DISCOVERY_INTERVAL_MS); }
});
