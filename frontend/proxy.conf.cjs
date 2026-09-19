// Angular dev-server proxy. It reads the SAME .env the rest of the frontend uses (server.mjs,
// gen-config), so there is one place to configure everything: the scheduler it forwards to comes from
// CF_SEED_URL and the API prefixes from CF_API_PREFIX / CF_CRONFLOW_PREFIX. Real environment variables
// override the file, and the older SCHEDULER_URL / API_PREFIX / CRONFLOW_PREFIX names still work as a
// fallback. /actuator is always proxied and is never under the API prefix.
const { readFileSync, existsSync } = require('node:fs');
const { resolve } = require('node:path');

function loadEnv() {
  const path = resolve(__dirname, '.env');
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
const pick = (...keys) => {
  for (const k of keys) if (env[k] !== undefined && env[k] !== '') return env[k];
  return undefined;
};

const target = pick('CF_SEED_URL', 'SCHEDULER_URL') || 'http://localhost:19090';

const norm = (v, d) => {
  let p = (v || d).trim();
  if (!p.startsWith('/')) p = '/' + p;
  return p.replace(/\/+$/, '') || d;
};
const prefix = norm(pick('CF_API_PREFIX', 'API_PREFIX'), '/cronsmith');
const cfPrefix = norm(pick('CF_CRONFLOW_PREFIX', 'CRONFLOW_PREFIX'), '/cronflow');

module.exports = {
  [prefix]: { target, secure: false, changeOrigin: true },
  [cfPrefix]: { target, secure: false, changeOrigin: true },
  '/actuator': { target, secure: false, changeOrigin: true },
};
