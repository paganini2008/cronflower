// The cronflower web console server — a zero-dependency Node static server + reverse proxy.
//
// Replaces nginx: it serves the built Angular SPA (with client-side-routing fallback to index.html)
// and proxies the two API prefixes to the scheduler, so the browser only ever talks to one origin
// (no CORS). Uses only Node's built-in modules — nothing to npm install.
//
// Config (env): PORT (default 80), WEB_ROOT (static dir), SCHEDULER_URL (proxy target),
// API_PREFIX (the backend's cronsmith.server.api-prefix, default /cronsmith).
import { createServer, request as httpRequest } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { extname, join, normalize } from 'node:path';

const PORT = Number(process.env.PORT || 80);
const ROOT = process.env.WEB_ROOT || '/usr/share/web';
// One or more scheduler URLs (comma-separated). The proxy tries them in turn so the console keeps
// working when a node — even the leader — is killed. Any node serves the API (reads local, writes
// routed to the leader on the server side), so which one answers does not matter.
const TARGETS = (process.env.SCHEDULER_URL || 'http://scheduler-1:8080')
  .split(',').map((s) => s.trim()).filter(Boolean).map((s) => new URL(s));
// The REST API prefix, matching the backend's cronsmith.server.api-prefix. /actuator is always
// proxied and is never under the API prefix.
const API_PREFIX = (() => {
  let p = (process.env.API_PREFIX || '/cronsmith').trim();
  if (!p.startsWith('/')) p = '/' + p;
  return p.replace(/\/+$/, '') || '/cronsmith';
})();
const PROXY_PREFIXES = [API_PREFIX, '/actuator'];

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

function proxy(req, res) {
  // Buffer the (small, JSON) request body so the call can be retried against the next node when one
  // is unreachable. Then try each target in order; a connection error falls through to the next.
  const chunks = [];
  req.on('data', (c) => chunks.push(c));
  req.on('end', () => {
    const body = Buffer.concat(chunks);
    const attempt = (i) => {
      if (i >= TARGETS.length) {
        res.writeHead(502, { 'content-type': 'text/plain' });
        res.end('bad gateway: no scheduler reachable');
        return;
      }
      const t = TARGETS[i];
      const headers = { ...req.headers, host: t.host };
      delete headers['transfer-encoding'];
      headers['content-length'] = body.length;
      const upstream = httpRequest(
        { protocol: t.protocol, hostname: t.hostname, port: t.port || 80, method: req.method, path: req.url, headers },
        (up) => { res.writeHead(up.statusCode || 502, up.headers); up.pipe(res); },
      );
      upstream.on('error', () => attempt(i + 1)); // node down/unreachable -> try the next one
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
  console.log(`cronflower web server on :${PORT} — static ${ROOT}, proxy ${PROXY_PREFIXES.join(', ')} -> ${TARGETS.map((t) => t.origin).join(', ')}`);
});
