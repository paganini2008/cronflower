#!/usr/bin/env bash
# Build the cronflower tutorial videos end to end.
#   1. Ensure the BGM exists (synthesized once, then reused).
#   2. Record each flow against the running console (http://localhost:7200 by default).
#   3. Encode each to docs/video/<name>.mp4 with BGM and a poster.
#
# The console must be running first (deploy/run-local.sh). Set HEADED=1 to watch the browser drive.
#   usage: bin/build.sh [flow ...]      (default: distributed-tasks dag-workflows)
set -euo pipefail

here="$(cd "$(dirname "$0")/.." && pwd)"
cd "$here"

flows=()
for a in "$@"; do [ -n "$a" ] && flows+=("$a"); done   # drop empty args (e.g. a blank -Dvideo.flows)
[ ${#flows[@]} -eq 0 ] && flows=(distributed-tasks dag-workflows)

# 1. BGM, generated once and reused.
if [ ! -f assets/bgm.mp3 ]; then
  echo ">> synthesizing BGM (one time)"
  mkdir -p assets
  python3 bin/make_bgm.py assets/bgm.wav
  ffmpeg -hide_banner -loglevel error -y -i assets/bgm.wav -codec:a libmp3lame -q:a 4 assets/bgm.mp3
  rm -f assets/bgm.wav
else
  echo ">> reusing assets/bgm.mp3"
fi

[ -d node_modules/playwright ] || npm ci

for flow in "${flows[@]}"; do
  echo ">> recording $flow"
  node bin/record.mjs "$flow"
  bash bin/encode.sh "$flow"
done

echo ">> done. videos in docs/video/"
