// Angular dev-server proxy. The REST API prefix is configurable via the API_PREFIX env var (default
// /cronsmith) so it can track the backend's cronsmith.server.api-prefix; the scheduler origin via
// SCHEDULER_URL (default http://localhost:19090). /actuator is always proxied and is never under the
// API prefix.
const target = process.env.SCHEDULER_URL || 'http://localhost:19090';

let prefix = (process.env.API_PREFIX || '/cronsmith').trim();
if (!prefix.startsWith('/')) prefix = '/' + prefix;
prefix = prefix.replace(/\/+$/, '') || '/cronsmith';

module.exports = {
  [prefix]: { target, secure: false, changeOrigin: true },
  '/actuator': { target, secure: false, changeOrigin: true },
};
