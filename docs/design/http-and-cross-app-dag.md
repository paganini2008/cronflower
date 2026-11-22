# HTTP nodes, cross-application DAGs, and AI generation from OpenAPI

## Why

Today a DAG is registered by one application and every node runs on that application's Java
executors (a bean method), or as a shell/python/sql script on that same executor. Two things are
missing:

1. **HTTP nodes** — a node whose body is a call to an external HTTP endpoint (for example an endpoint
   of a local FastAPI app), with no Java code and no executor to deploy.
2. **Cross-application orchestration** — one DAG whose nodes are hosted by *different* applications
   (and/or HTTP endpoints), wired together through the same channels.

The unifying idea (the user's framing): **a FastAPI is just another executor — it exposes its nodes
over HTTP instead of through the Java executor SDK.** So a node is `(host, implementation)` where the
host is a Java executor *or* a plain HTTP endpoint.

## Model

`DagDefinition.NodeDef` gains four optional, back-compatible fields (the woven definition is stored as
one JSON blob in `cf_task_dag.definition`, so new fields round-trip with no schema change):

- `application` — which executor application hosts this node. Absent ⇒ the graph's owning application
  (today's behaviour). A node naming a different application makes the DAG cross-application.
- `kind` — `bean` (default, absent ⇒ bean), `script`, or `http`.
- `endpoint` — for `http` nodes: `{ url, method = POST, headers? }`.
- `io` — optional mapping. Default (no mapping): the request body is the merged channel state as JSON
  (POST) or query params (GET); the JSON response object's keys are the channel updates. Explicit
  mapping: `input` builds the request from named channels, `output` maps response fields (JSONPath) to
  channel writes.

`bean`/`script` nodes are unchanged.

## Execution

In the run loop, before dispatching a node:

- `http` node ⇒ run **server-side** via a new `HttpNodeRunner` (no executor needed): render the request
  from channel state + `io.input`, call the endpoint, map the response to channel updates via
  `io.output`, return `NodeRunResult`.
- `bean`/`script` node ⇒ dispatch to an executor as today, but routed by the node's `application`
  (falling back to the graph's application), so different nodes can land on different apps.

`DagNodeDispatcher` picks a live instance by application (new `pick(application)` over the registry's
`instances`, which already carry `application`), not only by graph.

## AI generation from OpenAPI

A FastAPI (or any OpenAPI service) is ingested as a catalog of HTTP nodes:

1. User gives a base URL; cronflower fetches `/openapi.json` (served automatically by FastAPI).
2. Each `path` + `method` becomes a `CatalogNode` of kind `http` with the endpoint prefilled and a
   description from the operation summary; request/response schemas inform the default `io` mapping.
3. The AI generator receives these HTTP catalog nodes **plus** the registered bean-node catalogs of
   one or more applications, and drafts a DAG wiring them together (cross-application allowed).
4. Validate → preview on the canvas → approve & register, exactly like today's AI DAG flow.

## Stages

1. **Engine core** — NodeDef fields; `HttpNodeRunner` (server-side); per-node application routing in
   `DagNodeDispatcher`; branch in the run loop. Unit-test an HTTP node end to end against a stub.
2. **OpenAPI ingest + AI** — fetch/parse `/openapi.json` → HTTP `CatalogNode`s; extend the generator
   prompt/schema to emit `kind=http`/`application`; validator updates.
3. **Console + frontend** — "Import from OpenAPI URL" on the AI page; HTTP-node authoring on the
   canvas; detail/display of HTTP + cross-app nodes.
4. **Docs + examples** — a worked FastAPI-3-endpoints example and a cross-app example.

Provenance (reported in chat, not spelled out here): the endpoint-as-node / OpenAPI-driven wiring is
the mainstream shape for HTTP workflow orchestration; channel-based data passing and the
generate→validate→repair loop match the existing engine and AI-DAG design.
