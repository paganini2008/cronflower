// Generates public/config.json from .env (+ real environment variables), so the console's runtime
// configuration lives in one place. Zero dependencies; run by the `config` npm script before
// start/build. process.env wins over .env, and every value has a sensible default, so a missing
// .env still produces a working same-origin config.
import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, '..');

/** Parse a .env file into a plain object (KEY=VALUE lines; # comments and blanks ignored). */
function parseEnv(path) {
  if (!existsSync(path)) {
    return {};
  }
  const out = {};
  for (const raw of readFileSync(path, 'utf8').split('\n')) {
    const line = raw.trim();
    if (!line || line.startsWith('#')) {
      continue;
    }
    const eq = line.indexOf('=');
    if (eq === -1) {
      continue;
    }
    const key = line.slice(0, eq).trim();
    let val = line.slice(eq + 1).trim();
    if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
      val = val.slice(1, -1);
    }
    out[key] = val;
  }
  return out;
}

const fileEnv = parseEnv(resolve(root, '.env'));
// process.env overrides the .env file.
const env = { ...fileEnv, ...process.env };
const pick = (key, fallback) => (env[key] !== undefined && env[key] !== '' ? env[key] : fallback);
// apiBaseUrl is intentionally allowed to be empty (same-origin), so read it directly.
const apiBaseUrl = env.CF_API_BASE_URL !== undefined ? env.CF_API_BASE_URL : '';

const config = {
  apiBaseUrl,
  apiPrefix: pick('CF_API_PREFIX', '/cronsmith'),
  cronflowPrefix: pick('CF_CRONFLOW_PREFIX', '/cronflow'),
};

const outDir = resolve(root, 'public');
mkdirSync(outDir, { recursive: true });
const outFile = resolve(outDir, 'config.json');
writeFileSync(outFile, JSON.stringify(config, null, 2) + '\n');
console.log(`[gen-config] wrote ${outFile} (apiBaseUrl='${apiBaseUrl}', apiPrefix='${config.apiPrefix}', cronflowPrefix='${config.cronflowPrefix}')`);
