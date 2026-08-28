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
# Ports: scheduler nodes 19090, 19091, …  ·  frontend 7200  ·  executors random in 50000-60000.
# The Angular dev server proxies /cronsmith + /actuator to the first scheduler (:19090).
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

SCHED_BASE_PORT="${SCHED_BASE_PORT:-19090}"
FRONTEND_PORT="${FRONTEND_PORT:-7200}"
SPREADER_PORT="${SPREADER_PORT:-22000}"

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

launch_scheduler() {
  local i="$1"
  local port=$((SCHED_BASE_PORT + i - 1))
  # shellcheck disable=SC2046
  java -Xmx"${SCHED_XMX_GB}g" -jar "$BIN/$SCHED_JAR" \
    --server.port="$port" \
    --spring.spreader.port="$SPREADER_PORT" \
    --spring.spreader.ip-addresses=127.0.0.1 \
    --spring.config.additional-location="file:$CONF/scheduler.properties" \
    $(scheduler_ds_args "$i") \
    >"$LOGS/scheduler-$i.log" 2>&1 &
  echo $! >"$RUN/scheduler-$i.pid"
  echo "     node $i  ->  http://localhost:$port/cronsmith/tasks   (log: logs/scheduler-$i.log)"
}

start_schedulers() {
  local nodes="$1" i
  echo ">> starting $nodes scheduler node(s) — leader (node 1) first, then the rest together"
  # Node 1 first: the console + executors target :$SCHED_BASE_PORT and it becomes the leader; followers
  # then start in parallel. Each node has its OWN store and stays in sync via the leader's broadcast.
  launch_scheduler 1
  wait_for_scheduler "$SCHED_BASE_PORT"
  for i in $(seq 2 "$nodes"); do launch_scheduler "$i"; done
  for i in $(seq 2 "$nodes"); do wait_for_scheduler "$((SCHED_BASE_PORT + i - 1))"; done
}

wait_for_scheduler() {
  local port="$1" node=$(( $1 - SCHED_BASE_PORT + 1 )) i
  echo -n ">> waiting for scheduler :$port "
  for i in $(seq 1 40); do
    if curl -fsS "http://localhost:$port/actuator/health" >/dev/null 2>&1; then echo "ok"; return 0; fi
    echo -n "."; sleep 2
  done
  echo " timeout"; echo "!! scheduler :$port did not come up — see logs/scheduler-$node.log" >&2; return 1
}

# Back up config.json once, then set its apiPrefix to the configured value so the browser calls the
# same path the dev proxy forwards. Restored by do_down.
patch_frontend_config() {
  [ -f "$FRONTEND_CONFIG" ] || return 0
  [ -f "$CONFIG_BAK" ] || cp "$FRONTEND_CONFIG" "$CONFIG_BAK"
  apply_config_apiprefix "$FRONTEND_CONFIG" "$API_PREFIX"
}
restore_frontend_config() {
  if [ -f "$CONFIG_BAK" ]; then
    cp "$CONFIG_BAK" "$FRONTEND_CONFIG"; rm -f "$CONFIG_BAK"
    echo ">> restored frontend/public/config.json"
  fi
}

start_frontend() {
  echo ">> starting the frontend (ng serve) on :$FRONTEND_PORT (proxying $API_PREFIX + /actuator to :$SCHED_BASE_PORT)"
  ( cd "$FRONTEND" && [ -d node_modules ] || npm install --no-audit --no-fund ) >>"$LOGS/frontend.log" 2>&1 || true
  # `exec` so the recorded pid IS the ng-serve process (not a wrapper subshell), making it killable.
  # API_PREFIX drives proxy.conf.cjs; SCHEDULER_URL points the dev proxy at node 1.
  ( cd "$FRONTEND" && API_PREFIX="$API_PREFIX" SCHEDULER_URL="http://localhost:$SCHED_BASE_PORT" \
      exec npx ng serve --port "$FRONTEND_PORT" --host 0.0.0.0 ) >"$LOGS/frontend.log" 2>&1 &
  echo $! >"$RUN/frontend.pid"
  echo "     console  ->  http://localhost:$FRONTEND_PORT   (log: logs/frontend.log)"
}

# Comma-separated URLs of all $1 scheduler nodes, so executors can fail over if the leader dies.
sched_urls_csv() {
  local n="$1" i out=""
  for i in $(seq 1 "$n"); do out="$out,http://localhost:$((SCHED_BASE_PORT + i - 1))"; done
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
  echo "  console    : http://localhost:$FRONTEND_PORT"
  echo "  scheduler  : http://localhost:$SCHED_BASE_PORT/cronsmith/tasks   ($nodes node(s), per-node H2 file @ deploy/data)"
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
  restore_frontend_config && echo ">> restored frontend/public/config.json"
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
