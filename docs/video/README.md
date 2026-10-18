# Tutorial videos

Short screencasts of the console, embedded in the root [`README.md`](../../README.md), the blog posts
under `docs/blogger/`, and the GitHub Pages site.

| File | Length | Covers |
|------|--------|--------|
| `distributed-tasks.mp4` | ~1:58 | from New Task: an HTTP API task to a public endpoint, cron with Test, the visual builder, YCRON, options, create, the list with filter and the time-zone toggle, run-now with the real result, execution history, pause and resume, edit, executors, cluster, health, settings, and the REST API explorer |
| `dag-workflows.mp4` | ~1:10 | from New Workflow: author a `@Dag` on the canvas as code and create it, then trigger a real run and watch it flow node by node across the cluster, with per-node detail and the run history |

Each `.mp4` has a matching `.poster.jpg` used as the click-to-play thumbnail.

These are generated, not edited by hand. To rebuild them (console running on `http://localhost:7200`):

```bash
mvn -f tools/video-gen/pom.xml generate-resources
```

See [`tools/video-gen/`](../../tools/video-gen/) for the recorder, the flows and the synthesized BGM.
