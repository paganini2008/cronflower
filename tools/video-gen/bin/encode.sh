#!/usr/bin/env bash
# Encode one recorded screencast to a shareable mp4: mix in the synthesized BGM (looped, soft, faded),
# H.264 + faststart for inline playback, plus a poster frame. Output lands in docs/video/.
#   usage: bin/encode.sh <name>
set -euo pipefail

name="${1:?usage: encode.sh <name>}"
here="$(cd "$(dirname "$0")/.." && pwd)"
raw="$here/raw/$name.webm"
bgm="$here/assets/bgm.mp3"
outdir="$(cd "$here/../.." && pwd)/docs/video"
out="$outdir/$name.mp4"
poster="$outdir/$name.poster.jpg"

[ -f "$raw" ] || { echo "missing recording: $raw" >&2; exit 1; }
[ -f "$bgm" ] || { echo "missing bgm: $bgm (run make_bgm first)" >&2; exit 1; }
mkdir -p "$outdir"

dur="$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$raw")"
fadeout="$(awk -v d="$dur" 'BEGIN{printf "%.2f", (d-2>0)?d-2:0}')"

echo ">> encoding $name  (${dur}s)"
ffmpeg -hide_banner -loglevel error -y \
  -i "$raw" \
  -stream_loop -1 -i "$bgm" \
  -filter_complex "[1:a]volume=0.22,afade=t=in:st=0:d=1.5,afade=t=out:st=${fadeout}:d=2[a]" \
  -map 0:v -map "[a]" -t "$dur" \
  -c:v libx264 -preset veryfast -crf 23 -pix_fmt yuv420p -movflags +faststart \
  -c:a aac -b:a 128k \
  "$out"

# Poster frame a little after the intro card.
pstart="$(awk -v d="$dur" 'BEGIN{printf "%.2f", (d>6)?6:d/2}')"
ffmpeg -hide_banner -loglevel error -y -ss "$pstart" -i "$out" -frames:v 1 -q:v 3 "$poster"

echo ">> wrote $out"
echo ">> wrote $poster"
