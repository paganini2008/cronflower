# Cronflower Glue: Containerized Multi-Language Script Tasks and DAG Nodes

Status: Draft for review. Owner: Fred Feng. Date: 2026-10-25.
Scope: an enterprise design (not an MVP). The target architecture runs user-authored scripts
(shell, python, java, sql) as first-class Task types and DAG node types, edited online, executed in
isolated containers, with streamed results and interactive debugging.

---

## 1. Goal and scope

Add a **Glue** capability so an operator can, from the console, write a script in shell, python, java,
or sql, save it as a versioned Task or DAG node, schedule or trigger it like any other task, run it in an
isolated container on an executor, and watch the output, exit status, and (for python and java) step
through it with a real debugger.

The name follows the industry convention: XXL-JOB calls this GLUE mode, and AWS Glue popularized the term
for script-driven data work.

### In scope
- New Task type **SCRIPT** and new DAG node type **script**, each carrying a language and source code.
- Online editor (Monaco) with language intelligence (LSP) and interactive debugging (DAP).
- Containerized execution on executors, with a Docker backend and a Kubernetes backend.
- Script versioning, result and log capture, an execution console with live streaming.
- A datasource registry for sql.
- Security: role-based access, sandboxing, network controls, secret injection, audit.

### Non-goals (v1 target)
- A general in-browser IDE with multi-file projects. Glue is single-script per task or node.
- Running Spark or heavy distributed compute. Glue runs process-level scripts. Spark-style work stays a
  future node type.
- Replacing the existing `@Task` bean and HTTP-API task types. Glue is additive.

---

## 2. Terminology

| Term | Meaning |
|------|---------|
| Script Task | A scheduler Task whose body is a script (language + source), run via a runner. |
| Script Node | A DAG node whose body is a script instead of a `@DagNode` bean method. |
| Runner | The executor component that executes one script run in an isolated unit. |
| Runtime image | A per-language container image carrying the interpreter plus the Glue entrypoint. |
| Sandbox | The isolation boundary around a run: runc, gVisor, Kata, or a Firecracker microVM. |
| Session | A long-lived interactive run for debugging or REPL, as opposed to a one-shot run. |
| Datasource | A named, pre-registered database connection that sql scripts run against. |

---

## 3. How it fits the current architecture

Glue reuses the existing control plane. The scheduler still owns schedules and dispatch. The executor is
extended from a bean and DAG-node runner into a **script runner**. Nothing in the scheduling, clustering,
retry, or dispatch model changes.

```
  Console (Monaco + LSP/DAP)          Scheduler cluster (cronflow)        Executor fleet (script runners)
  +------------------------+  author  +---------------------------+ dispatch +----------------------------+
  | Script editor          | -------> | Task store, cf_task_dag   | -------> | ScriptRunner               |
  | Results + logs (SSE/WS)| <------- | timing wheel, routing     |   logs   |   DockerRunner (per run)   |
  | Debug (DAP over WS)     |         | exec log: stdout/exit     | <------- |   K8sRunner    (Job/Pod)   |
  +------------------------+          +---------------------------+          |   sandbox: runc/gVisor/... |
                                                                             +----------------------------+
                                                                             per-language images + limits
```

Integration points that already exist and are reused:

| Existing piece | Reuse for Glue |
|----------------|----------------|
| `taskType` on the task model (today: bean, HTTP) | add `SCRIPT` |
| Task dispatch over `POST /cronflow/executor/run` (RunRequest) | carry script payload for script tasks |
| DAG node dispatch over `POST /cronflow/nodes/run` (NodeRunRequest) | carry script payload for script nodes |
| `cf_task_dag` DAG definition JSON | node gains optional language and script |
| Execution log (`cs_task_log`, `cf_dag_node_log`) | gains exit code, stdout, stderr (capped) |
| Scheduler retry, timeout, misfire, routing, sharding | unchanged, drive script runs the same way |
| DagState typed channels | script nodes read channels in, write channels out, as JSON |

---

## 4. Execution architecture (the core)

### 4.1 Runner abstraction

The executor gains a `ScriptRunner` SPI with pluggable backends. The same SPI serves both a scheduled
Script Task and a dispatched Script Node.

```
interface ScriptRunner {
    RunHandle start(ScriptRun spec);     // launch an isolated unit, return a handle
    // handle: stream stdout/stderr, await exit, read result, cancel/kill
}
```

| Backend | Unit per run | Where it runs | Target deployment |
|---------|--------------|---------------|-------------------|
| **DockerRunner** | one container | the executor host Docker or podman daemon | single host, on-prem, edge |
| **KubernetesRunner** | one Job or Pod | the cluster the executor has an API token for | scale, multi-tenant, cloud |

This is the same split used by Apache Airflow (DockerOperator and KubernetesPodOperator), Argo Workflows,
and Apache DolphinScheduler. The DockerRunner mirrors a self-hosted CI runner. The KubernetesRunner
mirrors a Pod-per-task scheduler.

### 4.2 Sandbox ladder

Isolation strength is a deployment choice, selected per runtime image or per tenant. The container shape
is identical. Only the runtime class changes.

| Level | Mechanism | Strength | Cold start | K8s runtimeClassName |
|-------|-----------|----------|-----------|----------------------|
| runc (default) | namespaces + cgroups + seccomp + no-new-privileges + read-only rootfs | medium | low | runc |
| gVisor | user-space kernel | high | low | gvisor |
| Kata | lightweight VM | high | medium | kata |
| Firecracker | microVM | highest | low to medium | kata-fc or firecracker-containerd |

Default posture: runc with a locked-down profile for trusted operators. gVisor or Firecracker for
untrusted or multi-tenant code. This is exactly the ladder used by AWS Lambda (Firecracker), Google Cloud
Run and GKE Sandbox (gVisor), and Judge0 or Piston (nsjail at the OS level).

### 4.3 Run lifecycle

```
save script (new version) -> schedule or trigger -> scheduler routes to an executor
  -> ScriptRunner.start(spec):
       select runtime image for language
       create container or Job with:
         command   = Glue entrypoint for the language
         mounts    = /glue/script.<ext>, /glue/input.json, /glue/secrets/*
         env       = GLUE_* run context (scrubbed), no inline secrets
         limits    = cpu, memory, pids, wall-time, max output bytes
         fs        = read-only rootfs + a writable /glue/work tmpfs
         net       = none, or an egress allowlist (NetworkPolicy)
         security  = drop all caps, no-new-privileges, non-root uid, seccomp, runtimeClass
       start -> stream stdout and stderr -> process exits
  -> collect exit code + /glue/result.json + resource usage
  -> map: exit 0 = success, non-zero = failure, wall-time exceeded = timeout, OOM = failure
  -> store stdout, stderr, exit code in the execution log (size-capped, overflow to object store)
  -> for a DAG node: parse result channels and write them into DagState
  -> retry, timeout, misfire handled by the existing scheduler machinery
```

### 4.4 Input and output contract

A stable contract decouples the script from the platform. Everything a script needs is a file or an env
variable. Everything a script returns is a file or stdout.

Inputs, mounted at `/glue`:

| Path or var | Content |
|-------------|---------|
| `/glue/script.<ext>` | the script source |
| `/glue/input.json` | `{ context, params, channels }`: run id, task group and name, attempt, the task parameter, and (for DAG nodes) the inbound channel state |
| `/glue/secrets/<name>` | each referenced named credential, as a file (never an env value) |
| env `GLUE_RUN_ID`, `GLUE_TASK`, `GLUE_ATTEMPT`, `GLUE_DEADLINE_MS` | run context mirror for convenience |

Outputs:

| Path or stream | Meaning |
|----------------|---------|
| stdout, stderr | streamed live, stored capped |
| exit code | 0 success, non-zero failure |
| `/glue/result.json` (optional) | structured result. For a task: `{ "result": <any> }` feeds the task result and any SpEL parameter template. For a DAG node: `{ "channels": { name: value } }` writes outbound channels |

If `/glue/result.json` is absent, the task result is the last non-empty stdout line, matching the common
scheduler convention.

---

## 5. Language runtimes

Each language is one maintained base image plus the Glue entrypoint. Images are versioned, signed, and
scanned.

| Language | Base image | How a run executes | Structured result | Interactive debug |
|----------|-----------|--------------------|-------------------|-------------------|
| **shell** | `glue-shell` (debian-slim + bash + coreutils + curl + db clients) | `bash /glue/script.sh`, input.json plus env, stdin available | result.json or last stdout line | tier 1 only (run and inspect) |
| **python** | `glue-python:3.x` (slim + pip allowlist + debugpy) | `python /glue/script.py`, reads input.json | result.json | tier 2 via debugpy over DAP |
| **java** | `glue-java:21` (JDK) | single-file `java /glue/script.java`, or compile and run a known entrypoint. JShell for REPL sessions | result.json | tier 2 via JDWP and the java-debug adapter |
| **sql** | `glue-sql` (JDBC drivers) or in-executor JDBC | connect to a named datasource, run statements, page results | rows as JSON, or update counts | tier 1 only |

Notes.
- shell and sql are the lowest-risk and ship first in the rollout, but all four are part of the target.
- java single-file source execution (`java script.java`, a JDK 11+ feature) keeps java process-isolated
  like the others and avoids an in-JVM classloader sandbox. JShell is offered for interactive sessions.
- sql does not need a process sandbox. Its boundary is the database account. See section 7.

---

## 6. Script authoring and versioning

Every save creates a new immutable **version** with author, timestamp, and a message. A run pins the exact
version it executed, so history is reproducible. This matches XXL-JOB GLUE history and a git-like model.

| Field | Notes |
|-------|-------|
| id, taskGroup or graph plus node | the owning task or DAG node |
| language | shell, python, java, sql |
| source | the script text, stored in a CLOB or an object store with a hash |
| version, author, created, message | version metadata |
| runtime options | image tag override, cpu and memory request, timeout, network policy, datasource ref, allowed egress |

Optionally the version store is backed by Git for diff, blame, and external review. The console shows a
version list with diff and a one-click rollback.

---

## 7. SQL and the datasource registry

sql scripts never carry a connection string. They name a **datasource** that an admin registered. This is
the Metabase and Redash model and keeps credentials out of scripts and logs.

| Datasource field | Notes |
|------------------|-------|
| name | referenced by sql scripts |
| jdbcUrl, driver | connection target |
| credentialRef | a reference into the secret store, never a literal |
| readOnly | optional, enforces a read-only session or account |
| limits | max rows, statement timeout, max result bytes |

The sql runner opens a pooled connection to the named datasource, runs the statements, pages results into
`result.json`, and enforces the limits. For strong isolation the sql runner itself can run inside a
container with only the JDBC drivers and no host access.

---

## 8. Data model

| Store | Change |
|-------|--------|
| Task detail (`cs_task_detail`) | `task_type` gains SCRIPT. New side table `cs_task_script(task_ref, language, version, source_ref, runtime_opts)` keyed to the task, or columns on the detail row |
| Execution log (`cs_task_log`) | add `exit_code`, `stdout_ref`, `stderr_ref` (inline if small, else object-store reference) |
| DAG definition (`cf_task_dag` JSON) | a node gains optional `language`, `script` or `scriptRef`, `runtimeOpts` |
| DAG node log (`cf_dag_node_log`) | add `exit_code`, `stdout_ref`, `stderr_ref` |
| New: script versions | `glue_script_version(id, owner, language, source_ref, version, author, created, message)` |
| New: datasource registry | `glue_datasource(name, jdbc_url, driver, credential_ref, read_only, limits)` |
| New: runtime registry | `glue_runtime(language, image, default_limits, sandbox_class, enabled)` |

All dialect schema files (`db/cronflow-schema-*.sql`) plus the JPA entities and the jOOQ table constants
change together, following the pattern already used for the task and DAG tables.

---

## 9. APIs

Authoring and run endpoints live under the scheduler prefix. Node test lives under the DAG prefix. The
executor contract extends the existing dispatch.

| Method and path | Purpose | Role |
|-----------------|---------|------|
| `POST /cronflow/scheduler/scripts` | create or update a script, returns a new version | SCRIPT_AUTHOR |
| `GET /cronflow/scheduler/scripts/{id}/versions` | version history and diff | SCRIPT_AUTHOR |
| `POST /cronflow/scheduler/scripts/run` | ad-hoc run or debug, streams output over SSE or WebSocket | SCRIPT_RUNNER |
| `POST /cronflow/scheduler/tasks` | save a Script Task (task_type SCRIPT), existing endpoint extended | SCHEDULER_ADMIN |
| `GET /cronflow/scheduler/datasources` and `POST ...` | list and manage named datasources | admin |
| `POST /cronflow/dag/nodes/test` | run one script node with sample channels, streams output | WORKFLOW_ADMIN |
| `POST /cronflow/executor/run` | existing task dispatch, now accepts a script payload | machine, permitAll |
| `WS /cronflow/executor/debug` | interactive debug bridge for a run (DAP frames) | authenticated bridge |

Streaming uses Server-Sent Events for one-way log tailing and WebSocket for the bidirectional debug
channel. The console connects to the scheduler, which proxies to the executor holding the run.

---

## 10. Console: Monaco plus LSP plus DAP editor

The editor follows the same stack as VS Code, GitHub Codespaces, Gitpod, and Eclipse Theia.

| Layer | Choice | Gives |
|-------|--------|-------|
| editor | Monaco in Angular | syntax highlight, folding, multi-cursor for shell, python, java, sql |
| intelligence | Language Server Protocol over WebSocket to a language server per language (pyright, jdtls, bash-language-server, a sql language server) | completion, hover, diagnostics, go-to |
| debug | Debug Adapter Protocol over WebSocket to a debug adapter per language (debugpy, java-debug) | breakpoints, step, variables, watch |

UX.
- New or Edit Task gains a Script invocation type: a language dropdown, the Monaco editor, a Run and a
  Debug button, and a results drawer with stdout and stderr tabs, exit code, duration, and a parameter
  override box.
- The DAG canvas lets a node be a script node. Double-click opens the same editor plus a Test node button
  that runs the node with sample channel input and shows the channel output.
- A results and log drawer streams live output. For scheduled runs, the execution-history row opens the
  stored stdout and stderr.

The LSP and DAP servers run as sidecar services next to the executors or as a shared language-service
deployment. They are not required for the editor to open and run, only for completion and step-debug.

---

## 11. Debugging, two tiers

| Tier | Languages | What it is | How |
|------|-----------|------------|-----|
| Tier 1, run and inspect | all four | execute, stream stdout and stderr, show exit code, duration, and result, re-run with a parameter or input override, inspect captured artifacts | the normal runner, output streamed over SSE |
| Tier 2, interactive step | python, java | breakpoints, step over and into, variables, watch, evaluate | the runner starts the process under debugpy (python) or JDWP (java), exposes the debug port inside the sandbox, the scheduler bridges a WebSocket to the browser Monaco DAP client |

shell and sql have no standard step-debugger and stay at tier 1. Tier 1 covers the common operator need
and matches what Airflow, DolphinScheduler, and notebook platforms offer. Tier 2 is the Codespaces-grade
addition for the two languages where it is standard.

---

## 12. Security model

Glue is arbitrary code execution by design. Security is the center of the design, not an add-on.

| Concern | Control |
|---------|---------|
| Who can author | role SCRIPT_AUTHOR, gated in SecurityConfig, every create and edit audited |
| Who can run | role SCRIPT_RUNNER, every run audited with who, when, which version, which executor |
| Where it runs | on executors only, never on the scheduler, so the blast radius is an executor sandbox |
| Isolation | container per run with the sandbox ladder in 4.2, default runc locked down, gVisor or Firecracker for untrusted code |
| Filesystem | read-only rootfs, a writable tmpfs work dir, no host mounts |
| Privileges | non-root uid, drop all capabilities, no-new-privileges, seccomp and apparmor profiles |
| Network | default none, optional per-script egress allowlist via a NetworkPolicy, no access to cluster metadata endpoints |
| Resources | cpu and memory limits, pids limit, wall-time kill, max output bytes, per-tenant and per-executor concurrency caps |
| Secrets | referenced by name, injected as mounted files, never inline in source, redacted in logs |
| sql | named datasources only, read-only option, row and time limits |
| Supply chain | runtime images signed with cosign and scanned with trivy, pinned by digest, an allowlist of images |
| Enablement | the whole feature is off by default, `cronflow.glue.enabled=false`, opt-in per executor with an allowed-language list |
| Audit | an append-only audit log of author and run events, exportable |

---

## 13. Observability

| Signal | Where |
|--------|-------|
| Live logs | streamed to the console over SSE during a run |
| Stored logs | execution log, inline when small, object-store reference when large |
| Metrics | run count, duration, exit codes, queue depth, cold-start time, sandbox kills, per language and per executor, exposed on the existing Prometheus endpoint |
| Artifacts | optional files a run writes under `/glue/out` are captured to an object store and linked from the run detail |
| Run detail | the DAG run and task detail pages gain a Logs and a Result view, reusing the existing viewers |

---

## 14. Scaling and high availability

| Mechanism | Effect |
|-----------|--------|
| Runner pool per executor | bounded concurrent runs, a queue with backpressure when full |
| Warm pools | pre-started containers per language cut cold start from seconds to tens of milliseconds |
| Kubernetes backend | each run is a Pod, the cluster autoscales, this is the enterprise scale path |
| Scheduler routing and sharding | script tasks dispatch across executors with the existing weighted and consistent-hash routing |
| Concurrency caps | per executor, per tenant, per script, to protect the host and the database |
| Isolation of failure | a runaway or OOM run kills only its own sandbox, the executor and other runs are unaffected |

---

## 15. Configuration

| Key | Meaning |
|-----|---------|
| `cronflow.glue.enabled` | master switch, default false |
| `cronflow.glue.runner` | docker or kubernetes |
| `cronflow.glue.languages` | allowed languages on this executor |
| `cronflow.glue.images.<lang>` | runtime image by digest |
| `cronflow.glue.sandbox-class` | runc, gvisor, kata, firecracker |
| `cronflow.glue.limits.*` | default cpu, memory, pids, wall-time, max output |
| `cronflow.glue.network` | none or an egress allowlist |
| `cronflow.glue.max-concurrency` | per-executor cap |
| `cronflow.glue.datasources.*` | the sql datasource registry, credentials by reference |

---

## 16. Failure, retry, timeout semantics

Script runs plug into the existing scheduler semantics with no new concepts.

| Outcome | Mapping |
|---------|---------|
| exit 0 | success |
| exit non-zero | failure, drives the task retry and misfire policy |
| wall-time exceeded | the sandbox is killed, recorded as a timeout |
| OOM or sandbox kill | failure |
| executor lost mid-run | the run is marked failed on the next heartbeat gap, retried per policy |
| DAG node failure | the node fails, the DAG applies its on-failure edges and run status, exactly as for bean nodes |

---

## 17. Delivery plan

The design above is the full enterprise target. Delivery is phased for safe rollout. Every phase ships a
secure, usable increment. There is no cut-down permanent MVP.

| Phase | Content |
|-------|---------|
| Phase 1 | Runner SPI and DockerRunner, shell and sql Script Tasks, Monaco editor in New Task, tier-1 run and debug with streamed output, script versioning, SCRIPT_AUTHOR and SCRIPT_RUNNER roles, audit, stored logs, runc sandbox, feature flag off by default |
| Phase 2 | python and java Script Tasks, DAG script nodes with channel in and out, SSE and WebSocket streaming, the datasource registry, the KubernetesRunner |
| Phase 3 | tier-2 interactive debugging with DAP for python and java, LSP intelligence, warm pools, gVisor and Firecracker hardening, artifacts and object-store overflow, per-tenant quotas and multi-tenancy |

---

## 18. Open decisions

1. Default sandbox for trusted operators: runc locked down, or gVisor from day one.
2. Java execution model for one-shot runs: single-file `java script.java`, or compile and run a known
   entrypoint, with JShell reserved for interactive sessions.
3. Script version store: database CLOB, object store, or Git-backed.
4. Secret store integration: Kubernetes Secrets, HashiCorp Vault, or both behind one reference scheme.
5. Whether the sql runner runs in-executor over JDBC, or always inside a container with only drivers.
6. Scope of tier-2 debugging at launch: python only first, or python and java together.

---

## Appendix A. Input and output example

`/glue/input.json` handed to a run:

```json
{
  "context": { "runId": "a1b2", "task": "reports/daily", "attempt": 0, "deadlineMs": 60000 },
  "params":  { "date": "2026-10-25" },
  "channels": { "input": "applicant-42" }
}
```

A python Script Node that reads a channel and writes two channels:

```python
import json
data = json.load(open("/glue/input.json"))
applicant = data["channels"]["input"]
score = 40
json.dump({"channels": {"score": score, "factors": "credit"}}, open("/glue/result.json", "w"))
print(f"scored {applicant} = {score}")
```

A shell Script Task that calls a public API and returns a result:

```bash
#!/usr/bin/env bash
set -euo pipefail
date=$(python -c 'import json;print(json.load(open("/glue/input.json"))["params"]["date"])')
curl -s "https://httpbin.org/get?date=$date" | tee /glue/work/out.json
echo "{\"result\": \"ok\"}" > /glue/result.json
```

A sql Script Task against a named datasource (datasource chosen in runtime options):

```sql
-- datasource: warehouse (read-only)
select status, count(*) as n
from orders
where created_at >= :date
group by status;
```

## Appendix B. Comparison to prior art

| System | What it does | What Cronflower borrows |
|--------|--------------|-------------------------|
| XXL-JOB GLUE | online editor, versioned code, executor runs shell, python, java-via-groovy | the product shape: online editor plus versioning plus executor run |
| Apache DolphinScheduler | shell, python, sql task types, tenant isolation, a K8s task | tenant and container isolation, named datasources |
| Airflow, Argo | DockerOperator and KubernetesPodOperator, Pod per task | the Docker and Kubernetes runner split |
| AWS Lambda, Fargate | customer code in Firecracker microVMs, warm pools | the microVM hardening option and warm pools |
| Judge0, Piston | run untrusted code via nsjail and containers | the sandbox and resource-limit discipline |
| GitHub Codespaces, Gitpod, Theia | Monaco plus LSP plus DAP over WebSocket | the editor, intelligence, and debug stack |
| Metabase, Redash | sql against registered connections | the datasource registry and credential handling |
