# video-gen

Reproducible **screen recordings** of the cronflower console, for the tutorials embedded in the root
README, the blog posts and the GitHub Pages site. Playwright drives a real Chromium through a scripted
session (visible cursor, realistic typing, scrolling, menus), records it, and ffmpeg adds a
synthesized background track and encodes to mp4. The playable files land in [`../../docs/video/`](../../docs/video/).

## One command

The console must be running first:

```bash
cd ../../deploy && ./run-local.sh -e 1     # console on http://localhost:7200
```

Then, from the repo root:

```bash
mvn -f tools/video-gen/pom.xml generate-resources
# one video only:
mvn -f tools/video-gen/pom.xml generate-resources -Dvideo.flows=dag-workflows
```

That runs [`bin/build.sh`](bin/build.sh): ensure the BGM, record each flow, encode each to
`docs/video/<name>.mp4` plus a `.poster.jpg`.

## Layout

| Path | What |
|------|------|
| `bin/make_bgm.py` | synthesizes `assets/bgm.mp3` (standard library only, no samples, no copyright). Runs once, then the mp3 is reused. |
| `bin/record.mjs` | the recording engine: injects the cursor, the caption bar and the intro card, and runs a flow. |
| `flows/<name>.mjs` | one tutorial: the intro `meta` and the scripted steps with the caption copy. |
| `bin/encode.sh` | webm to mp4, mixes the BGM (looped, soft, faded), writes the poster. |
| `bin/build.sh` | orchestration (BGM, then record + encode each flow). |
| `assets/bgm.mp3` | the reused background track. |
| `../../docs/video/*.mp4` | the committed, playable outputs. |

## Requirements

- Node 20+ and `npm` (Playwright and its Chromium install on first run).
- `ffmpeg` and `python3` on the PATH.
- The console running on `http://localhost:7200` (override with `CF_URL`).

Set `HEADED=1` to watch the browser drive the session while it records.

## Tune the content

Edit the caption copy and steps in `flows/distributed-tasks.mjs` and `flows/dag-workflows.mjs`. Each
step is resilient: a selector that does not match is skipped with a warning rather than failing the
whole recording. Keep each video under two minutes.
