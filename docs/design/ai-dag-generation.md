# Cronflower AI DAG Generation: Prompt to Workflow

Status: Draft for review. Owner: Fred Feng. Date: 2026-11-21.
Scope: an enterprise design (not an MVP). It turns a natural language prompt into a validated,
runnable DAG on the existing cronflower engine, with a private first model stance, a validate and
repair loop, retrieval grounding, and a mandatory human review step.

---

## 1. Goal and scope

Cronflower is DAG first. Authoring a DAG by hand (nodes, edges, channels, conditionals, shards) has a
real learning cost. This feature lets an operator describe the workflow in plain language, and the
system drafts a DAG they can preview on the canvas, edit, approve, and run.

The generated artifact is a **DagDefinition**, the same JSON the engine already loads and executes. The
model does not invent a new runtime. It fills in a schema the engine already understands.

### In scope
- A `POST` endpoint that takes a prompt plus context and returns a draft DagDefinition.
- An `LlmProvider` abstraction that is OpenAI compatible, so a private Ollama endpoint and OpenAI cloud
  (and any compatible server such as vLLM or LocalAI) are all drivers behind one interface.
- A **node catalog** built from the registered `@DagNode` beans, injected into the prompt so the model
  wires real nodes, not imagined ones.
- A validate and repair loop: schema check, semantic check, then a bounded retry that feeds the errors
  back to the model.
- Retrieval grounding (RAG) over the catalog, prior approved DAGs, and domain notes.
- A human in the loop review: the draft is never auto run.
- Governance: a private first default, redaction before any cloud call, a token budget, a model
  allowlist, and an audit trail.

### Non-goals (v1 target)
- Autonomous agents that execute tasks while planning. The model plans the graph. The engine runs it.
- Fine tuning a model. We use prompting, structured output, and retrieval over an off the shelf model.
- Generating arbitrary executor code in v1. The model wires existing `@DagNode` beans. Script node
  generation is a later slice and is independent of this feature (see section 12).

### Relationship to Script Tasks
AI generation and Script Tasks are independent. AI generation can wire bean nodes with no Script
feature present. Once Script nodes land, the catalog gains script templates and the model may emit them
too. Neither feature blocks the other.

---

## 2. Mainstream references

The design follows patterns that are now standard in the LLM application space.

| Pattern | Where it is used | How cronflower applies it |
|---|---|---|
| Structured output / function calling | OpenAI tools, Ollama format=json, Instructor | The model returns the DagDefinition JSON under a fixed schema, not free text. |
| Generate, validate, repair | Instructor, Guardrails, LangGraph self correction | Schema and semantic checks, then a bounded repair retry with the errors as feedback. |
| Graph as the target of planning | LangGraph, Dify, n8n AI nodes, Flowise | The target is an explicit node and edge graph the user can inspect and edit. |
| Retrieval augmented generation | LangChain, LlamaIndex | Retrieve the relevant nodes and example DAGs for the prompt, inject them as grounding. |
| Human in the loop | Dify, Argo, CI approval gates | The draft is a proposal. A person reviews, edits, and approves before it runs. |
| Private first, cloud optional | Ollama, vLLM, LocalAI | Default to the private endpoint. Cloud is opt in and redacted. |
| Prompt and cost governance | LLM gateways, guardrail layers | Model allowlist, token budget, rate limit, and an audit log per generation. |

Cronflower's distinctive position: the model targets a **real, already executable engine schema**, so a
generated DAG is not a mock. It runs on the same scheduler, cluster, and executors as a hand built one.

---

## 3. Architecture overview

```
 operator prompt
      |
      v
 +----------------------+     catalog + examples (RAG)
 |  Generation service  |<------------------------------+
 +----------------------+                               |
      |  build request                          +---------------+
      v                                          |  Node catalog |
 +----------------------+                        |  (from beans) |
 |     LlmProvider      |  OpenAI compatible     +---------------+
 |  ollama | openai |.. |----> chat/completions         ^
 +----------------------+                               |
      |  raw JSON                               +---------------+
      v                                          | Vector store  |
 +----------------------+   invalid              | (embeddings)  |
 |  Validator           |---------+              +---------------+
 |  schema + semantic   |         |  errors
 +----------------------+         v
      |  valid             +--------------+   bounded N retries
      |                    | Repair loop  |--> back to LlmProvider
      v                    +--------------+
 +----------------------+
 |  Draft DagDefinition |
 +----------------------+
      |
      v
 +----------------------+   edit / regenerate
 |  Console review      |<--------------------+  human in the loop
 |  (DAG canvas)        |
 +----------------------+
      |  approve
      v
 +----------------------+
 |  Persist as draft    |---> existing engine: schedule / trigger / run
 +----------------------+
```

The only new runtime pieces are the generation service, the provider abstraction, the validator, the
catalog, and the vector store. Everything downstream of the approved draft is the existing engine.

---

## 4. LlmProvider abstraction

One interface, many drivers. All drivers speak the OpenAI chat completions shape, which Ollama and most
local servers already expose.

```java
public interface LlmProvider {
    String id();                       // "ollama", "openai", "vllm-internal"
    boolean supportsJsonSchema();      // native structured output vs prompt-coerced JSON
    LlmResponse chat(LlmRequest request);          // blocking
    Flow.Publisher<LlmChunk> stream(LlmRequest request); // optional token stream
}
```

| Driver | Endpoint | Auth | Default role |
|---|---|---|---|
| ollama | `${base-url}/v1/chat/completions` | Bearer, from env | private, default |
| openai | `https://api.openai.com/v1/chat/completions` | Bearer API key, from env | cloud, opt in |
| compatible | any OpenAI compatible base url | Bearer, from env | private cluster models |

Design points.

- Secrets are never written in config files or this document. The base url and token come from
  environment variables or a secret store, referenced by name only.
- Provider selection is config driven, with a default plus a per request override. A request may force
  the private provider for data that must not leave the network.
- `supportsJsonSchema` lets the service pick native structured output when available and fall back to a
  strict prompt plus a JSON parse when not.
- Timeouts, retries with backoff, and a circuit breaker live in the provider layer, not the service.

---

## 5. Generation contract

The model must return a DagDefinition. We constrain it two ways: a system prompt that states the rules,
and a JSON schema (as a function or `format` argument) that the output must match.

The output schema mirrors the engine's DagDefinition.

| Field | Meaning |
|---|---|
| `graph` | graph name and description |
| `inputs` | declared inputs and their types |
| `channels` | named channels with a reducer (last, append, merge, sum) |
| `nodes` | list of nodes, each a catalog node name plus its input and output channel bindings |
| `edges` | directed edges between nodes |
| `conditionals` | `when` expression plus branch targets and an `otherwise` |
| `shards` | fan out spec for a node, with the channel it shards over |

The **node catalog** is the grounding that keeps the model honest.

```json
{
  "nodes": [
    {
      "name": "fetchOrders",
      "description": "Reads orders for a date range from the orders service",
      "inputs":  [{"channel": "dateRange", "type": "object"}],
      "outputs": [{"channel": "orders", "type": "array"}],
      "params":  [{"name": "pageSize", "type": "int", "required": false}]
    }
  ]
}
```

The catalog is built from the registered `@DagNode` beans (name, javadoc or an annotation description,
the method's state reads and writes, and params). The model may only reference catalog node names. A
node it invents is caught by the validator in section 6.

System prompt skeleton (abridged):

```
You design a cronflower DAG as JSON matching the provided schema.
Use only nodes from the catalog. Wire channels by name.
Prefer a conditional over duplicated branches. Keep the graph acyclic unless a subgraph loop is asked for.
Return JSON only, no prose.
```

---

## 6. Validation and repair loop

A generated DAG passes two gates before a human ever sees it.

| Gate | Checks |
|---|---|
| Schema | Shape matches the DagDefinition schema. Types are right. Required fields present. |
| Semantic | Every node name exists in the catalog. Channels read by a node are produced upstream. No cycle unless a subgraph loop was requested. Reducers are valid for their channel type. Conditional expressions reference real channels. Shard targets a real array channel. |

If either gate fails, the service runs a repair loop.

```
attempt = 0
while attempt < maxRepairs:
    errors = validate(candidate)
    if errors.isEmpty: break
    candidate = llm.chat(repairPrompt(candidate, errors))   # errors as structured feedback
    attempt += 1
if not errors.isEmpty: return Draft.failed(errors)          # surfaced to the operator, not silently dropped
```

The repair prompt is specific. It lists each error with the offending path, so the model corrects the
JSON rather than regenerating from scratch. The loop is bounded (default 2 repairs). On exhaustion the
operator sees the best candidate plus the remaining errors and can fix it by hand on the canvas.

This mirrors the generate, validate, repair pattern used by Instructor and Guardrails, adapted to a
graph schema with semantic rules, not just field types.

---

## 7. Retrieval grounding (RAG)

For a small catalog the whole catalog fits in the prompt. For a large one, retrieval keeps the prompt
focused and the output accurate.

| Step | Detail |
|---|---|
| Index | Embed each catalog node description, each prior approved DAG, and domain notes. |
| Store | A vector store. Use pgvector when the backing database is PostgreSQL, an in memory index otherwise. |
| Retrieve | Embed the prompt, fetch the top k relevant nodes and one or two example DAGs. |
| Inject | Put only the retrieved nodes and examples in the system prompt. |

Embeddings use an `EmbeddingProvider` with the same private first stance as `LlmProvider`. The private
Ollama endpoint serves an embedding model, so catalog text and prompts need not leave the network.

RAG is a later slice. Slice A ships with the full catalog inlined, which is correct for a typical
deployment of tens of nodes. RAG matters once the catalog grows into the hundreds.

---

## 8. Human in the loop

The draft is a proposal, never an action.

1. The operator submits a prompt.
2. The service returns a draft DagDefinition plus any residual validation notes.
3. The console renders it on the existing DAG canvas. The operator can edit nodes, edges, channels, and
   conditionals directly.
4. The operator can refine by chat: "add a retry on fetchOrders", "split scoring into two nodes". The
   service regenerates and shows a diff against the current draft.
5. The operator approves. Only then is the DAG persisted as a draft definition and made available to
   schedule, trigger, or run.

No path auto runs a generated DAG. Approval is explicit and recorded with the approver and timestamp.

---

## 9. Safety and governance

LLM generation adds a new trust boundary. The design treats the model output as untrusted until
validated, and treats user and task data as private.

| Concern | Control |
|---|---|
| Data leaving the network | Private provider is the default. Cloud is opt in per request and per tenant. |
| Sensitive data in prompts | Redaction before any cloud call. Task payloads, credentials, and personal identifiers are stripped or masked. The catalog sends descriptions, not live data. |
| Prompt injection via catalog text | Catalog descriptions are data in the prompt, never instructions. The system prompt states this. Generated output is still fully validated. |
| Runaway cost | A token budget per generation and per tenant. A rate limit. A model allowlist. |
| Unsafe graphs | The validator rejects cycles, dangling channels, and unknown nodes before review. |
| Auditability | Each generation records prompt, provider, model, token counts, candidate, errors, and the approver. |
| Over trust of output | The human review step is mandatory and the draft is never executed on generation. |

The private first stance satisfies the deployment requirement and the privacy rules at once: by default
nothing about a workflow reaches a third party.

---

## 10. Data model and API

New persistence. One table for a generation record, one for provider config when not file based.

| Table | Key columns |
|---|---|
| `cf_ai_generation` | id, tenant, prompt, provider, model, status (draft, approved, failed), candidate_json, validation_json, prompt_tokens, completion_tokens, created_by, created_at, approved_by, approved_at |
| `cf_ai_provider` | id, kind (ollama, openai, compatible), base_url_ref, auth_ref, default_model, enabled, is_default |

Secrets are references (`auth_ref`) resolved from env or a secret store, not stored values.

REST, under the existing `/cronflow/dag` group, in a Swagger section of its own.

| Method and path | Purpose |
|---|---|
| `POST /cronflow/dag/ai/generate` | prompt plus context to a draft DagDefinition |
| `POST /cronflow/dag/ai/refine` | a draft plus a refinement instruction to a new draft and a diff |
| `GET  /cronflow/dag/ai/{id}` | fetch a generation record and its candidate |
| `POST /cronflow/dag/ai/{id}/approve` | approve and persist the draft as a DAG definition |
| `GET  /cronflow/dag/ai/providers` | list configured providers and their status |

Access is role gated. A new `DAG_AUTHOR` role may generate and approve. Generation is also behind a
feature flag so a deployment without a model endpoint is unaffected.

---

## 11. Configuration

All under `cronflow.ai`. Secrets come from env, referenced by name.

```yaml
cronflow:
  ai:
    enabled: true
    default-provider: ollama
    providers:
      ollama:
        kind: compatible
        base-url: ${CRONFLOW_AI_OLLAMA_URL}
        api-key: ${CRONFLOW_AI_OLLAMA_KEY}
        default-model: qwen2.5-coder
      openai:
        kind: openai
        api-key: ${OPENAI_API_KEY}
        default-model: gpt-4o-mini
        allow-cloud: false          # opt in explicitly
    embeddings:
      provider: ollama
      model: nomic-embed-text
    rag:
      enabled: false                # Slice B
      top-k: 8
    budget:
      max-prompt-tokens: 8000
      max-completion-tokens: 4000
      max-repairs: 2
    redaction:
      enabled: true                 # applied before any cloud call
```

---

## 12. Phased delivery

| Slice | Content | Verifiable outcome |
|---|---|---|
| A | LlmProvider (ollama + openai), node catalog from beans, generate endpoint, schema and semantic validator, repair loop, console preview, approve and run. No RAG. | A prompt produces a valid bean node DAG that runs on the engine, reviewed and approved by a person. |
| B | EmbeddingProvider, vector store (pgvector or in memory), retrieval of nodes and examples. | A large catalog still yields accurate graphs. Prompt size stays bounded. |
| C | Multi turn refine with diff, Script node templates in the catalog, saved DAG templates and few shot examples. | An operator shapes a DAG over several turns and can emit script nodes. |
| D | Eval harness (a set of prompts with expected shapes), guardrail layer, a cost and usage dashboard, per tenant governance. | Regression safety and operable cost and quality metrics. |

Slice A is the thin vertical slice: one private model, one cloud model, real beans, real validation, a
real run, with a person in the loop. It proves the whole contract end to end before RAG or refinement
add surface area.

---

## 13. Open questions

- Model choice for the private endpoint: a coder tuned model (for example a Qwen coder variant) versus a
  general model. To be measured with the Slice D eval set.
- Where redaction draws the line for cloud calls, and whether some tenants forbid cloud entirely.
- Whether approved generations feed back as few shot examples automatically, and how to keep that set
  curated rather than noisy.
