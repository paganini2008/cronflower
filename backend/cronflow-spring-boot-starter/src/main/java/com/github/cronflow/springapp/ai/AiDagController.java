/*
 * Copyright 2026 Fred Feng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.cronflow.springapp.ai;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.github.cronflow.springapp.ai.DagValidator.Issue;
import com.github.cronflow.springapp.dag.ClusterDagRegistry;
import com.github.cronflow.springapp.dag.DagDefinitionCodec;
import com.github.cronflow.springapp.dag.DagExecutorRegistry;
import com.github.cronflow.springapp.dag.DagExecutorRegistry.ExecutorInstance;
import com.github.cronflow.springapp.pojo.DagDefinition;
import com.github.cronflow.springapp.pojo.DagRegistrationRequest;

/**
 * AI DAG generation endpoints, served under the DAG API group ({@code /cronflow/dag/ai/...}). Generate
 * drafts a DAG and records it; approve re-validates the reviewed DAG and registers it through the same
 * cluster path as a hand-authored one. A draft is never auto-run: approval is an explicit, recorded
 * step. Present only when {@code cronflow.ai.enabled=true}.
 */
@RestController
public class AiDagController {

    private final AiDagGenerator generator;
    private final NodeCatalogProvider catalogProvider;
    private final DagValidator validator;
    private final DagDefinitionCodec codec;
    private final ClusterDagRegistry clusterRegistry;
    private final DagExecutorRegistry registry;
    private final AiGenerationStore store;

    public AiDagController(AiDagGenerator generator, NodeCatalogProvider catalogProvider,
            DagValidator validator, DagDefinitionCodec codec, ClusterDagRegistry clusterRegistry,
            DagExecutorRegistry registry, AiGenerationStore store) {
        this.generator = generator;
        this.catalogProvider = catalogProvider;
        this.validator = validator;
        this.codec = codec;
        this.clusterRegistry = clusterRegistry;
        this.registry = registry;
        this.store = store;
    }

    public record GenerateRequest(String prompt, String provider, String model, Boolean privateOnly) {
        /** Back-compatible constructor (no explicit model — the provider default is used). */
        public GenerateRequest(String prompt, String provider, Boolean privateOnly) {
            this(prompt, provider, null, privateOnly);
        }
    }

    public record GenerateResponse(String id, boolean valid, DagDefinition dag, List<Issue> issues,
            String provider, String model, int promptTokens, int completionTokens, int repairs,
            String error) {}

    public record ApproveRequest(String id, DagDefinition dag, String application, String approver) {}

    public record ApproveResponse(boolean registered, String graph, String application,
            List<Issue> issues, String error) {}

    public record ProviderInfo(String id, boolean cloud, String defaultModel, List<String> models) {}

    @PostMapping("/ai/generate")
    public ResponseEntity<GenerateResponse> generate(@RequestBody GenerateRequest request) {
        if (request == null || request.prompt() == null || request.prompt().isBlank()) {
            return ResponseEntity.badRequest().body(new GenerateResponse(null, false, null, List.of(),
                    null, null, 0, 0, 0, "prompt is required"));
        }
        boolean privateOnly = Boolean.TRUE.equals(request.privateOnly());
        DagCandidate c = generator.generate(request.prompt(), catalogProvider.catalog(),
                request.provider(), request.model(), privateOnly);

        String id = UUID.randomUUID().toString();
        String status = c.dag() != null && c.error() == null ? AiGenerationRecord.DRAFT
                : AiGenerationRecord.FAILED;
        String candidateJson = c.dag() != null ? safeEncode(c.dag()) : c.rawJson();
        store.save(new AiGenerationRecord(id, request.prompt(), c.provider(), c.model(), status,
                c.dag() == null ? null : c.dag().graph(), null, candidateJson, joinIssues(c.issues()),
                c.promptTokens(), c.completionTokens(), c.repairs(), c.error(), null,
                LocalDateTime.now(), null, null));

        return ResponseEntity.ok(new GenerateResponse(id, c.valid(), c.dag(), c.issues(), c.provider(),
                c.model(), c.promptTokens(), c.completionTokens(), c.repairs(), c.error()));
    }

    @GetMapping("/ai/{id}")
    public ResponseEntity<AiGenerationRecord> get(@PathVariable String id) {
        return store.find(id).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/ai/history")
    public List<AiGenerationRecord> history() {
        return store.recent(50);
    }

    /**
     * Approve a reviewed DAG and register it through the normal cluster path.
     *
     * <ul>
     * <li>400 — missing graph/application, or the DAG fails validation against the catalog</li>
     * <li>422 — the chosen application has no live executor to host the graph</li>
     * <li>200 — registered; the record (if an id was given) is marked approved</li>
     * </ul>
     */
    @PostMapping("/ai/approve")
    public ResponseEntity<ApproveResponse> approve(@RequestBody ApproveRequest request) {
        DagDefinition dag = request == null ? null : request.dag();
        if (dag == null || dag.graph() == null || dag.graph().isBlank()) {
            return bad("a graph is required", List.of());
        }
        List<Issue> issues = validator.validate(dag, catalogProvider.catalog());
        if (!issues.isEmpty()) {
            return bad("the DAG did not pass validation", issues);
        }
        // A pure-HTTP DAG (every node is an HTTP call the scheduler makes itself) needs no executor, so
        // it can be registered host-less and even without an application. A DAG with bean/script nodes
        // still needs a live executor of its application to dispatch them to.
        boolean needsExecutor = needsExecutor(dag);
        String application = request.application() == null || request.application().isBlank()
                ? (needsExecutor ? null : "http")
                : request.application();
        if (needsExecutor && application == null) {
            return bad("an application is required", List.of());
        }
        if (!needsExecutor) {
            registry.registerDefinition(application, dag);
        } else {
            ExecutorInstance host = registry.anyLiveInstance(application).orElse(null);
            if (host == null) {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body(new ApproveResponse(false, dag.graph(), application, List.of(),
                                "no live executor for application '" + application + "'"));
            }
            DagRegistrationRequest reg = new DagRegistrationRequest(application, host.instanceId(),
                    host.runUrl(), host.healthCheckUrl(), List.of(dag), null, host.weight());
            clusterRegistry.register(reg);
        }
        if (request.id() != null && !request.id().isBlank()) {
            store.approve(request.id(), request.approver(), application, dag.graph());
        }
        return ResponseEntity.ok(new ApproveResponse(true, dag.graph(), application, List.of(), null));
    }

    @GetMapping("/ai/providers")
    public List<ProviderInfo> providers() {
        List<ProviderInfo> out = new ArrayList<>();
        for (LlmProvider p : generator.providers()) {
            out.add(new ProviderInfo(p.id(), p.cloud(), p.defaultModel(), p.models()));
        }
        return out;
    }

    @GetMapping("/ai/catalog")
    public List<CatalogNode> catalog() {
        return catalogProvider.catalog().nodes();
    }

    private String safeEncode(DagDefinition dag) {
        try {
            return codec.encode(dag, DagDefinitionCodec.JSON);
        } catch (Exception e) {
            return null;
        }
    }

    private String joinIssues(List<Issue> issues) {
        if (issues == null || issues.isEmpty()) {
            return null;
        }
        return issues.stream().map(Issue::toString).collect(Collectors.joining("\n"));
    }

    /** True when the DAG has a node that runs on an executor (a bean or script node) — an HTTP node and
     *  a subgraph node run on the scheduler itself, so a graph made only of those needs no executor. */
    private static boolean needsExecutor(DagDefinition dag) {
        if (dag.nodes() == null) {
            return false;
        }
        return dag.nodes().stream().anyMatch(n -> !n.isHttp()
                && (n.subgraph() == null || n.subgraph().isBlank()));
    }

    private ResponseEntity<ApproveResponse> bad(String message, List<Issue> issues) {
        return ResponseEntity.badRequest().body(new ApproveResponse(false, null, null, issues, message));
    }
}
