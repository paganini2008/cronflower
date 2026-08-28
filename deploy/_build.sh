#!/usr/bin/env bash
# Shared build helpers for run-local.sh and run-docker.sh. Sourced, never run directly.
#
# Layout (this file lives in cronflower/deploy):
#   ROOT      = cronflower/            the monorepo root
#   BACKEND   = cronflower/backend     the 4-module Maven reactor
#   FRONTEND  = cronflower/frontend    the Angular console
#   BIN       = cronflower/deploy/bin  where the two runnable jars are staged

VERSION="${VERSION:-1.0.0-SNAPSHOT}"

# Maven: prefer the project's Maven Wrapper (backend/mvnw — no system Maven required), then honour
# an explicit $MVN, then `mvn` on PATH, then the known local install.
if [ -z "${MVN:-}" ]; then
  if [ -x "$BACKEND/mvnw" ]; then MVN="$BACKEND/mvnw"
  elif command -v mvn >/dev/null 2>&1; then MVN=mvn
  elif [ -x /usr/local/work/maven_3.9.12/bin/mvn ]; then MVN=/usr/local/work/maven_3.9.12/bin/mvn
  else echo "!! Maven not found — set MVN=/path/to/mvn, or keep backend/mvnw" >&2; exit 1
  fi
fi

# The base engine `com.github.paganini2008:cronsmith` and its cluster library
# `com.chaconneai:openspreader` are NOT published to Maven Central yet (they will be), so for now
# they are resolved from the LOCAL Maven repository. If a cronsmith checkout is present next to
# cronflower, install it (and thus refresh the local artifact) first.
CRONSMITH_REPO="${CRONSMITH_REPO:-$ROOT/../cronsmith}"

# The Maven Wrapper downloads a fresh Maven whose default localRepository is ~/.m2/repository, which
# does NOT hold cronsmith/openspreader. Pin the local repo that does (override with M2_REPO=). Once
# these artifacts are on Maven Central this can be dropped.
M2_REPO="${M2_REPO:-/usr/local/work/m2_repo}"
if [ -d "$M2_REPO" ]; then MVN_REPO_ARG="-Dmaven.repo.local=$M2_REPO"; else MVN_REPO_ARG=""; fi

SCHED_JAR="cronsmith-scheduler-example-${VERSION}.jar"
EXEC_JAR="cronsmith-executor-example-${VERSION}.jar"

# Executors get a random port in this range (avoids clashing with anything on the usual ports).
EXEC_PORT_LO="${EXEC_PORT_LO:-50000}"
EXEC_PORT_HI="${EXEC_PORT_HI:-60000}"

# JVM heap per node, in whole GB (applied as -Xmx). Default 1G each. Also drives the capacity guard.
SCHED_XMX_GB="${SCHED_XMX_GB:-1}"
EXEC_XMX_GB="${EXEC_XMX_GB:-1}"
MEM_BUDGET_PCT="${MEM_BUDGET_PCT:-70}"   # cap total requested heap at this % of available RAM

# Echo a random free port in [EXEC_PORT_LO, EXEC_PORT_HI], skipping any in the space-separated
# list passed as $1 and any currently bound on the host. Used for both local and docker executors.
rand_free_port() {
  local used=" $1 " p tries=0
  while :; do
    p=$(( EXEC_PORT_LO + RANDOM % (EXEC_PORT_HI - EXEC_PORT_LO + 1) ))
    case "$used" in
      *" $p "*) ;;                                   # already picked this round
      *) lsof -ti tcp:"$p" >/dev/null 2>&1 || { echo "$p"; return 0; } ;;
    esac
    tries=$((tries + 1)); [ "$tries" -gt 300 ] && { echo "$p"; return 0; }
  done
}

# Read cronsmith.server.api-prefix from conf/scheduler.properties (default /cronsmith), normalized to a
# leading slash and no trailing slash; blank or "/" falls back to /cronsmith (the proxies need a
# non-empty prefix). The scheduler reads the property itself; this lets the runners propagate the SAME
# value to the executor (server-api-prefix), the console proxy (API_PREFIX env) and the served
# config.json (apiPrefix), so changing it in ONE place flows through the whole chain.
read_api_prefix() {
  local conf="$HERE/conf/scheduler.properties" p=""
  if [ -f "$conf" ]; then
    p=$(grep -E '^[[:space:]]*cronsmith\.server\.api-prefix[[:space:]]*=' "$conf" 2>/dev/null \
        | tail -1 | sed -E 's/^[^=]*=[[:space:]]*//; s/[[:space:]]*$//')
  fi
  [ -z "$p" ] && p="/cronsmith"
  case "$p" in /*) ;; *) p="/$p" ;; esac
  p="${p%/}"
  [ -z "$p" ] && p="/cronsmith"
  printf '%s' "$p"
}

# apply_config_apiprefix <config.json path> <prefix> — set the apiPrefix key, preserving the rest and
# the formatting (byte-identical when unchanged, so no spurious diff for the /cronsmith default).
apply_config_apiprefix() {
  local file="$1" prefix="$2"
  [ -f "$file" ] || return 0
  node -e '
    const fs = require("fs");
    const [f, p] = [process.argv[1], process.argv[2]];
    let j = {};
    try { j = JSON.parse(fs.readFileSync(f, "utf8")); } catch (e) {}
    j.apiPrefix = p;
    fs.writeFileSync(f, JSON.stringify(j, null, 2) + "\n");
  ' "$file" "$prefix"
}

build_backend() {
  # -DskipTests means this is a build-to-run, not a verify, so skip the JaCoCo coverage gate too
  # (with no fresh test data a coverage check is meaningless and would fail the run).
  if [ -f "$CRONSMITH_REPO/pom.xml" ]; then
    echo ">> installing base engine (cronsmith) from $CRONSMITH_REPO"
    "$MVN" -q $MVN_REPO_ARG -f "$CRONSMITH_REPO/pom.xml" install -DskipTests -Djacoco.skip=true
  else
    echo ">> base engine checkout not found at $CRONSMITH_REPO — assuming 'cronsmith' is already in $M2_REPO"
  fi
  echo ">> building backend (4-module reactor), local repo: ${M2_REPO:-default}"
  "$MVN" -q $MVN_REPO_ARG -f "$BACKEND/pom.xml" clean install -DskipTests -Djacoco.skip=true
}

stage_jars() {
  mkdir -p "$BIN"
  cp "$BACKEND/cronsmith-scheduler-example/target/$SCHED_JAR" "$BIN/$SCHED_JAR"
  cp "$BACKEND/cronsmith-executor-example/target/$EXEC_JAR"   "$BIN/$EXEC_JAR"
  echo ">> staged jars into $BIN:"
  echo "     $SCHED_JAR"
  echo "     $EXEC_JAR"
}

build_frontend_dist() {
  local ng_config="${NG_CONFIG:-production}"
  echo ">> building the web console ($FRONTEND, ng $ng_config)"
  ( cd "$FRONTEND" && npm install --no-audit --no-fund && npx ng build --configuration "$ng_config" )
}

# --- Preflight & capacity guards ------------------------------------------------------------

# Verify a JDK >= 17 is on PATH.
check_java() {
  command -v java >/dev/null 2>&1 || { echo "!! java not found on PATH - install a JDK 17+" >&2; exit 1; }
  local ver major
  ver=$(java -version 2>&1 | awk -F\" '/version/{print $2; exit}')   # e.g. 17.0.12 or 1.8.0_xx
  major=${ver%%.*}
  [ "$major" = 1 ] && major=$(printf '%s' "$ver" | cut -d. -f2)      # old 1.8-style
  if ! [ "$major" -ge 17 ] 2>/dev/null; then
    echo "!! JDK 17+ required, found '$ver'. Install/point JAVA_HOME at a 17+ JDK." >&2; exit 1
  fi
  echo ">> JDK ok (java $ver)"
}

# Verify Node >= 20 (and npm) are on PATH.
check_node() {
  command -v node >/dev/null 2>&1 || { echo "!! node not found on PATH - install Node 20+" >&2; exit 1; }
  command -v npm  >/dev/null 2>&1 || { echo "!! npm not found on PATH - install Node 20+ (with npm)" >&2; exit 1; }
  local ver major
  ver=$(node -v 2>/dev/null | sed 's/^v//'); major=${ver%%.*}
  if ! [ "$major" -ge 20 ] 2>/dev/null; then
    echo "!! Node 20+ required, found 'v$ver'." >&2; exit 1
  fi
  echo ">> Node ok (v$ver)"
}

# check_prereqs [docker]  - always checks JDK + Node; add 'docker' to also require a running Docker.
check_prereqs() {
  echo ">> checking prerequisites"
  check_java
  check_node
  if [ "${1:-}" = docker ]; then
    command -v docker >/dev/null 2>&1 || { echo "!! docker not found on PATH" >&2; exit 1; }
    docker info >/dev/null 2>&1 || { echo "!! Docker is not running - start Docker Desktop" >&2; exit 1; }
    echo ">> Docker ok"
  fi
}

# Total RAM (GB) of the host.
host_mem_gb() {
  if [ "$(uname)" = Darwin ]; then
    echo $(( $(sysctl -n hw.memsize) / 1024 / 1024 / 1024 ))
  else
    awk '/MemTotal/{printf "%d", $2/1024/1024}' /proc/meminfo
  fi
}

# Total RAM (GB) allocated to the Docker engine/VM.
docker_mem_gb() {
  docker info --format '{{.MemTotal}}' 2>/dev/null | awk '{printf "%d", $1/1024/1024/1024}'
}

# check_capacity <nodes> <execs> <total_gb> <label>
# Refuse to start if the requested heap exceeds MEM_BUDGET_PCT% of <total_gb>.
check_capacity() {
  local nodes="$1" execs="$2" total="$3" label="$4"
  [ "${total:-0}" -gt 0 ] 2>/dev/null || { echo ">> capacity check skipped (could not read $label memory)"; return 0; }
  local req=$(( nodes * SCHED_XMX_GB + execs * EXEC_XMX_GB ))
  local budget=$(( total * MEM_BUDGET_PCT / 100 ))
  echo ">> capacity: need ~${req}G heap ($nodes sched x ${SCHED_XMX_GB}G + $execs exec x ${EXEC_XMX_GB}G); budget ${budget}G (${MEM_BUDGET_PCT}% of ${total}G ${label})"
  if [ "$req" -gt "$budget" ]; then
    echo "!! refusing to start: ${req}G requested > ${budget}G budget." >&2
    echo "   reduce -n / -e, lower SCHED_XMX_GB / EXEC_XMX_GB, or raise MEM_BUDGET_PCT." >&2
    exit 1
  fi
}
