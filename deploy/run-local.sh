#!/usr/bin/env bash
# ============================================================================================
# cronsmith — one-click LOCAL runner (bare JVM processes on your host, no Docker).
#
#   ./run-local.sh       [-n N] [-e M]                    build & start everything (`up` is optional)
#   ./run-local.sh down                                   stop everything this script started
#   ./run-local.sh logs [scheduler-1|executor-1|frontend] tail a log
#
# Options for `up`:
#   -n N   number of scheduler (server) nodes         (default 1)
#   -e M   number of executor (client) nodes          (default 0 = do not start any executor)
#
# Start order matches a real bring-up: scheduler node(s) first, then the frontend, then — only if
# -e was given — the executor(s) last, so they register against an already-running scheduler.
#
# Ports: frontend console 7200 (the one entry point) · scheduler nodes AND executors on random free
# ports in 50000-60000. No fixed scheduler port: server.mjs is handed every node's address as a seed
# and discovers the full member list (and each node's real port) from /actuator/health, then
# load-balances /cronsmith + /cronflow + /actuator across them.
#
# Store: each node gets its OWN embedded H2 file (deploy/data/cronsmith-<n>). Node-local replicated
# model - the leader broadcasts every write and each node keeps its own copy in sync, so a failover
# keeps the data. Persists across restarts. Uncomment a datasource in conf/scheduler.properties to
# switch to a shared MySQL/PostgreSQL. No rebuild needed.
# ============================================================================================
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
BACKEND="$ROOT/backend"
FRONTEND="$ROOT/frontend"
BIN="$HERE/bin"
CONF="$HERE/conf"
RUN="$HERE/run"          # pid files
LOGS="$HERE/logs"
DATA="$HERE/data"        # H2 file store (db mode)
FRONTEND_CONFIG="$FRONTEND/public/config.json"   # ng serve serves this live
CONFIG_BAK="$RUN/config.json.bak"                # backup while we patch apiPrefix into it

# shellcheck source=_build.sh
. "$HERE/_build.sh"

FRONTEND_PORT="${FRONTEND_PORT:-7200}"
SPREADER_PORT="${SPREADER_PORT:-22000}"
CRONFLOW_PREFIX="${CRONFLOW_PREFIX:-/cronflow}"   # DAG API prefix the web proxy also forwards

# Each node gets its OWN independent H2 file (node-local replicated model: the leader broadcasts each
# write and every node keeps its own copy). No AUTO_SERVER / shared file. conf wins if it sets a
# datasource (e.g. a shared MySQL/PostgreSQL, which takes the CAS path instead of broadcast).
scheduler_ds_args() {
  local i="$1"
  if grep -qE '^[[:space:]]*spring\.datasource\.url=' "$CONF/scheduler.properties" 2>/dev/null; then
    return 0
  fi
  printf -- '--spring.datasource.url=jdbc:h2:file:./data/cronsmith-%s;DB_CLOSE_DELAY=-1 --spring.datasource.username=sa --spring.datasource.password=' "$i"
}

# The HTTP ports assigned to the scheduler nodes this run — random free ports in EXEC_PORT_LO..HI,
# pre-generated here so the launcher knows them all. No fixed port: any of them is a valid seed the
# console bootstraps discovery from, and each node also advertises its own real port via health metadata.
SCHED_PORTS=""

launch_scheduler() {
  local i="$1" port="$2"
  # shellcheck disable=SC2046
  java -Xmx"${SCHED_XMX_GB}g" -jar "$BIN/$SCHED_JAR" \
    --server.port="$port" \
    --spring.spreader.port="$SPREADER_PORT" \
    --spring.spreader.ip-addresses=127.0.0.1 \
    --spring.config.additional-location="file:$CONF/scheduler.properties" \
    $(scheduler_ds_args "$i") \
    >"$LOGS/scheduler-$i.log" 2>&1 &
  echo $! >"$RUN/scheduler-$i.pid"
  echo "     node $i  ->  http://localhost:$port   (log: logs/scheduler-$i.log)"
}

start_schedulers() {
  local nodes="$1" i port used=""
  # Pre-generate one free random port per node (no fixed 19090); the console seeds from all of them.
  for i in $(seq 1 "$nodes"); do
    port=$(rand_free_port "$used"); used="$used $port"; SCHED_PORTS="$SCHED_PORTS $port"
  done
  SCHED_PORTS="${SCHED_PORTS# }"
  echo ">> starting $nodes scheduler node(s) on random ports: $SCHED_PORTS"
  i=1
  for port in $SCHED_PORTS; do launch_scheduler "$i" "$port"; i=$((i + 1)); done
  # Wait on the first node's health, then (multi-node) on cluster convergence — port-agnostic.
  wait_for_scheduler "$(echo "$SCHED_PORTS" | awk '{print $1}')"
  [ "$nodes" -gt 1 ] && wait_for_cluster "$nodes"
}

# Wait until the cluster reports the full roster — asks any node's /actuator/health, port-agnostic.
wait_for_cluster() {
  local want="$1" first i n
  first=$(echo "$SCHED_PORTS" | awk '{print $1}')
  echo -n ">> waiting for the $want-node cluster to converge "
  for i in $(seq 1 60); do
    n=$(curl -fsS "http://localhost:$first/actuator/health" 2>/dev/null | grep -o '"memberCount":[0-9]*' | grep -o '[0-9]*')
    [ "${n:-0}" -ge "$want" ] 2>/dev/null && { echo "ok ($n nodes)"; return 0; }
    echo -n "."; sleep 2
  done
  echo " timeout (saw ${n:-0}/$want) — continuing anyway"; return 0
}

wait_for_scheduler() {
  local port="$1" i
  echo -n ">> waiting for scheduler :$port "
  for i in $(seq 1 40); do
    if curl -fsS "http://localhost:$port/actuator/health" >/dev/null 2>&1; then echo "ok"; return 0; fi
    echo -n "."; sleep 2
  done
  echo " timeout"; echo "!! scheduler :$port did not come up — see logs/scheduler-1.log" >&2; return 1
}

# The console is now served from the built dist (see start_frontend), whose config.json is patched in
# place there — so the source under public/ is never touched and there is nothing to back up/restore.
patch_frontend_config() { :; }
restore_frontend_config() { :; }

start_frontend() {
  local dist="$FRONTEND/dist/cronflower/browser" seeds
  echo ">> building the web console (ng build), then serving it via server.mjs on :$FRONTEND_PORT"
  echo "   (cluster self-discovery via /actuator/health + load-balancing — no nginx/KONG)"
  ( cd "$FRONTEND" && [ -d node_modules ] || npm install --no-audit --no-fund ) >>"$LOGS/frontend.log" 2>&1 || true
  ( cd "$FRONTEND" && npx ng build --configuration development ) >>"$LOGS/frontend.log" 2>&1
  # Patch the served (disposable) build output's config.json — never the source under public/ — so the
  # browser talks same-origin (apiBaseUrl='') to this server, which proxies to the cluster.
  [ -f "$dist/config.json" ] && apply_config_apiprefix "$dist/config.json" "$API_PREFIX"
  # server.mjs: static dist + discover every member from ANY node's /actuator/health (all node ports are
  # handed in as seeds) and round-robin /cronsmith + /cronflow + /actuator across them, with failover.
  seeds="$(sched_urls_csv)"
  CF_WEB_PORT="$FRONTEND_PORT" CF_WEB_ROOT="$dist" CF_SEED_URL="$seeds" \
    CF_API_PREFIX="$API_PREFIX" CF_CRONFLOW_PREFIX="$CRONFLOW_PREFIX" node "$FRONTEND/server.mjs" \
    >>"$LOGS/frontend.log" 2>&1 &
  echo $! >"$RUN/frontend.pid"
  echo "     console  ->  http://localhost:$FRONTEND_PORT   (log: logs/frontend.log)"
}

# Comma-separated URLs of every scheduler node (its generated random port), so executors and the
# console can fail over across all of them.
sched_urls_csv() {
  local out="" port
  for port in $SCHED_PORTS; do out="$out,http://localhost:$port"; done
  echo "${out#,}"
}

start_executors() {
  local execs="$1" urls="$2" i port used=""
  echo ">> starting $execs executor node(s) — random ports in $EXEC_PORT_LO-$EXEC_PORT_HI"
  for i in $(seq 1 "$execs"); do
    port=$(rand_free_port "$used"); used="$used $port"
    java -Xmx"${EXEC_XMX_GB}g" -jar "$BIN/$EXEC_JAR" \
      --server.port="$port" \
      --spring.application.name="demo-executor" \
      --spring.config.additional-location="file:$CONF/executor.properties" \
      --cronsmith.client.server-urls="$urls" \
      --cronsmith.client.server-api-prefix="$API_PREFIX" \
      --cronflow.client.server-urls="$urls" \
      --cronflow.client.server-api-prefix="$CRONFLOW_PREFIX" \
      >"$LOGS/executor-$i.log" 2>&1 &
    echo $! >"$RUN/executor-$i.pid"
    echo "     executor $i  ->  :$port  (log: logs/executor-$i.log)"
  done
}

do_up() {
  local nodes=1 execs=0 OPTIND opt
  while getopts ":n:e:" opt; do
    case "$opt" in
      n) nodes="$OPTARG" ;;
      e) execs="$OPTARG" ;;
      *) echo "usage: $0 [-n nodes] [-e execs]" >&2; exit 1 ;;
    esac
  done

  check_prereqs
  check_capacity "$nodes" "$execs" "$(host_mem_gb)" "host RAM"

  mkdir -p "$RUN" "$LOGS" "$DATA"

  # One source of truth: the API prefix from conf/scheduler.properties (default /cronsmith). The
  # scheduler reads it from that same file; here we propagate it to the executor, the dev proxy and
  # the served config.json so a single edit flows through the whole chain.
  API_PREFIX="$(read_api_prefix)"
  echo ">> API prefix: $API_PREFIX (from conf/scheduler.properties; propagated to executor + proxy + config.json)"
  patch_frontend_config

  build_backend
  stage_jars

  # Run from the deploy dir so the default H2 file (./data/cronsmith) lands in deploy/data.
  cd "$HERE"
  start_schedulers "$nodes"
  start_frontend
  if [ "$execs" -gt 0 ]; then
    start_executors "$execs" "$(sched_urls_csv "$nodes")"
  else
    echo ">> no -e given: executors NOT started (create tasks in the console; run executors later to run them)"
  fi

  echo
  echo "cronsmith is up (local):"
  echo "  console    : http://localhost:$FRONTEND_PORT   (the one entry point — proxies to the cluster)"
  echo "  schedulers : $nodes node(s) on random ports [$SCHED_PORTS], per-node H2 file @ deploy/data"
  [ "$execs" -gt 0 ] && echo "  executors  : $execs node(s) on random ports $EXEC_PORT_LO-$EXEC_PORT_HI (shown above)"
  echo "  real DB?   : edit conf/scheduler.properties (MySQL/PostgreSQL) — default is per-node H2 files replicated by broadcast"

  # Spell out the valid `logs` names for whatever was actually started.
  local names="scheduler-1"; [ "$nodes" -gt 1 ] && names="scheduler-1..$nodes"
  names="$names | frontend"
  [ "$execs" -eq 1 ] && names="$names | executor-1"
  [ "$execs" -gt 1 ] && names="$names | executor-1..$execs"
  echo "  tail a log : $0 logs <name>       (name: $names)"
  echo "  stop all   : $0 down"
}

# Kill a process and all its descendants (ng serve spawns child node processes that must die too).
kill_tree() {
  local pid="$1" c
  for c in $(pgrep -P "$pid" 2>/dev/null); do kill_tree "$c"; done
  kill "$pid" 2>/dev/null
}

do_down() {
  local f pid fp
  if [ -d "$RUN" ]; then
    for f in "$RUN"/*.pid; do
      [ -e "$f" ] || continue
      pid="$(cat "$f")"
      kill_tree "$pid"
      echo ">> stopped $(basename "$f" .pid) (pid $pid)"
      rm -f "$f"
    done
  fi
  # Safety net: free the frontend port if an orphaned ng-serve child still LISTENS on it. Match only
  # the listener (-sTCP:LISTEN) so we never kill browser tabs that merely connected to the port.
  fp=$(lsof -ti tcp:"$FRONTEND_PORT" -sTCP:LISTEN 2>/dev/null) || true
  if [ -n "$fp" ]; then kill_tree "$fp" 2>/dev/null || true; echo ">> freed frontend port $FRONTEND_PORT"; fi
  restore_frontend_config
  echo ">> all local cronsmith processes stopped"
}

# `up` is the default action and can be omitted:
#   ./run-local.sh                 == ./run-local.sh up
#   ./run-local.sh -n 2 -e 1       == ./run-local.sh up -n 2 -e 1
case "${1:-}" in
  down) do_down ;;
  logs) tail -f "$LOGS/${2:-scheduler-1}".log ;;
  up)   shift; do_up "$@" ;;
  *)    do_up "$@" ;;
esac
