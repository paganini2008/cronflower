# Tutorial video

The screencast of the console, embedded in the root [`README.md`](../../README.md), the blog posts
under `docs/blogger/`, and the GitHub Pages site.

| File | Length | Covers |
|------|--------|--------|
| `dag-workflows.mp4` | ~3:20 | one end-to-end walkthrough: the dashboard and scheduled tasks (bean, HTTP, script, DAG-trigger, each tagged), executors and the scheduler cluster, the workflow graph, authoring a `@Dag` on the canvas and as code, generating one from a plain-language prompt with OpenAI, triggering a real run and watching it flow node by node across the cluster, the per-node detail, the run history, and the dark theme |

Each `.mp4` has a matching `.poster.jpg` used as the click-to-play thumbnail.

These are generated, not edited by hand. To rebuild them (console running on `http://localhost:7200`):

```bash
mvn -f tools/video-gen/pom.xml generate-resources
```

See [`tools/video-gen/`](../../tools/video-gen/) for the recorder, the flows and the synthesized BGM.
